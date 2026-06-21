package com.qiu.aischedule.network;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.ApiConfig;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.local.entity.ParseHistory;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.security.SecretStore;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * LLM 网络客户端：调用 OpenAI 兼容的 /chat/completions 接口（默认 DeepSeek）。
 * 统一自然语言入口 {@link #interpret}：把日程列表作上下文，让 LLM 判断 create/edit/delete/clarify。
 * - 请求经 OkHttp 异步执行，**不在主线程**（读配置/Key、发请求、写历史都在后台）；
 * - 用 Gson 构造请求体与解析响应；解析后写入 ParseHistory（AI 辅助过程留痕）；
 * - 无 Key / 网络失败回调 onError，UI 降级提示（永不死胡同）。
 * 设计：LLM 只输出匹配条件（TargetSpec）与改动量（SchedulePatch），<b>不返回 eventId</b>，
 * 目标由客户端在真实库匹配——规避幻觉、误改。
 */
public class LlmClient {

    private static volatile LlmClient INSTANCE;

    private final OkHttpClient client;

    private LlmClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static LlmClient getInstance() {
        if (INSTANCE == null) {
            synchronized (LlmClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LlmClient();
                }
            }
        }
        return INSTANCE;
    }

    public interface CommandCallback {
        void onSuccess(ParsedCommand command, long historyId);

        void onError(String message);
    }

    private static final String COMMAND_PROMPT =
            "你是日程指令解析器，只输出一个 JSON 对象，不要任何解释或代码块标记。\n"
                    + "判断用户意图 intent：create(新建日程) / edit(修改已有日程) / delete(删除已有日程) / clarify(信息不足需追问)。\n"
                    + "现有日程列表（JSON 数组）：{events}\n"
                    + "硬性规则：\n"
                    + "1. 禁止返回事件 id；edit/delete 只输出 target 匹配条件，由系统在真实库中匹配。\n"
                    + "2. 相对时间（如推迟1小时、提前30分钟）放进 patch.timeShiftMinutes（整数分钟，正推迟负提前），禁止改写成绝对时间。\n"
                    + "3. 模糊时段（上午/下午/晚上/夜里）填 target.timePeriod（morning/afternoon/evening/night），target.time 留空，禁止硬猜 HH:mm。\n"
                    + "4. create 时填 schedule 对象(title/date(yyyy-MM-dd)/time(HH:mm)/location/reminderMinutes(0=不提醒))；edit 时填 target 与 patch。\n"
                    + "5. edit 的 patch：字符串字段空串=不改；reminderMinutes 用 -1 表示不改、0 表示取消提醒、正数表示提前分钟；timeShiftMinutes 用 0 表示不平移。\n"
                    + "6. 若信息不足无法判断，intent=clarify 并在 question 写明要追问什么。\n"
                    + "7. 今天是 {today}。\n"
                    + "输出格式：{\"intent\":\"\",\"target\":{\"titleKeyword\":\"\",\"date\":\"\",\"time\":\"\",\"timePeriod\":\"\",\"locationKeyword\":\"\"},\"patch\":{\"title\":\"\",\"date\":\"\",\"time\":\"\",\"location\":\"\",\"reminderMinutes\":-1,\"timeShiftMinutes\":0},\"schedule\":{\"title\":\"\",\"date\":\"\",\"time\":\"\",\"location\":\"\",\"reminderMinutes\":0},\"needClarification\":false,\"question\":\"\"}";

    /**
     * 统一自然语言入口：把日程列表作上下文喂给 LLM，判断 create/edit/delete/clarify。
     * 整体在后台线程；无 Key / 网络失败回调 onError。
     */
    public void interpret(Context context, String userInput, List<EventRecord> events, CommandCallback callback) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ScheduleRepository repo = ScheduleRepository.getInstance(context);
            ApiConfig cfg = repo.getApiConfigSync();
            String key = SecretStore.getApiKey(context);

            if (cfg == null || key == null || key.isEmpty()) {
                postCmdError(callback, context.getString(R.string.toast_ai_unavailable));
                return;
            }

            String model = notEmpty(cfg.modelName) ? cfg.modelName : "deepseek-v4-flash";
            String baseUrl = notEmpty(cfg.baseUrl) ? cfg.baseUrl : "https://api.deepseek.com";
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            String body = buildCommandBody(model, userInput, buildEventsContext(events));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + key)
                    .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    postCmdError(callback, "网络失败：" + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        postCmdError(callback, "接口返回 " + response.code());
                        return;
                    }
                    try {
                        String content = extractContent(respBody);
                        ParsedCommand cmd = parseCommand(content);

                        ParseHistory history = new ParseHistory();
                        history.inputText = userInput;
                        history.modelName = model;
                        history.jsonResult = wrapHistoryJson(content, cmd); // 留痕：raw + intent + target
                        history.confidence = cmd.needClarification ? 0.3f : 0.9f;
                        history.createdAt = System.currentTimeMillis();
                        history.isApplied = false;
                        long historyId = repo.insertHistorySync(history);

                        postCmdSuccess(callback, cmd, historyId);
                    } catch (Exception ex) {
                        postCmdError(callback, "解析失败：" + ex.getMessage());
                    }
                }
            });
        });
    }

    /** 构造喂给 LLM 的紧凑日程列表 JSON（id/title/date/time/location/reminder）。 */
    private String buildEventsContext(List<EventRecord> events) {
        JsonArray arr = new JsonArray();
        if (events == null) {
            return arr.toString();
        }
        int limit = Math.min(events.size(), 50);
        for (int i = 0; i < limit; i++) {
            EventRecord e = events.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id);
            o.addProperty("title", e.title == null ? "" : e.title);
            o.addProperty("date", DateUtils.formatDate(e.startTime));
            o.addProperty("time", DateUtils.formatTime(e.startTime));
            o.addProperty("location", e.location == null ? "" : e.location);
            o.addProperty("reminder", e.reminderMinutes);
            arr.add(o);
        }
        return arr.toString();
    }

    private String buildCommandBody(String model, String userInput, String eventsJson) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", COMMAND_PROMPT
                .replace("{events}", eventsJson)
                .replace("{today}", DateUtils.formatDate(System.currentTimeMillis())));
        messages.add(sys);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userInput);
        messages.add(user);

        root.add("messages", messages);
        root.addProperty("temperature", 0);
        return root.toString();
    }

    /** 从模型输出中抽取首个 JSON 对象并解析为 ParsedCommand。 */
    private ParsedCommand parseCommand(String content) {
        int s = content.indexOf('{');
        int e = content.lastIndexOf('}');
        if (s < 0 || e < 0 || e < s) {
            throw new RuntimeException("未找到 JSON");
        }
        JsonObject o = JsonParser.parseString(content.substring(s, e + 1)).getAsJsonObject();

        ParsedCommand cmd = new ParsedCommand();
        cmd.intent = optStr(o, "intent");
        if (cmd.intent.isEmpty()) {
            cmd.intent = ParsedCommand.INTENT_CREATE;
        }
        cmd.needClarification = optBool(o, "needClarification");
        cmd.question = optStr(o, "question");
        if (o.has("target") && o.get("target").isJsonObject()) {
            cmd.target = parseTarget(o.getAsJsonObject("target"));
        }
        if (o.has("patch") && o.get("patch").isJsonObject()) {
            cmd.patch = parsePatch(o.getAsJsonObject("patch"));
        }
        if (o.has("schedule") && o.get("schedule").isJsonObject()) {
            cmd.schedule = parseScheduleObj(o.getAsJsonObject("schedule"));
        }
        return cmd;
    }

    private TargetSpec parseTarget(JsonObject o) {
        TargetSpec t = new TargetSpec();
        t.titleKeyword = optStr(o, "titleKeyword");
        t.date = optStr(o, "date");
        t.time = optStr(o, "time");
        t.timePeriod = optStr(o, "timePeriod");
        t.locationKeyword = optStr(o, "locationKeyword");
        return t;
    }

    private SchedulePatch parsePatch(JsonObject o) {
        SchedulePatch p = new SchedulePatch();
        p.title = optStr(o, "title");
        p.date = optStr(o, "date");
        p.time = optStr(o, "time");
        p.location = optStr(o, "location");
        // Gson 兜底：reminderMinutes 缺字段不能变 0（=误取消提醒），缺则 −1（不改）
        if (o.has("reminderMinutes") && o.get("reminderMinutes").isJsonPrimitive()) {
            JsonPrimitive pr = o.get("reminderMinutes").getAsJsonPrimitive();
            if (pr.isNumber()) {
                p.reminderMinutes = pr.getAsInt();
            } else {
                try {
                    p.reminderMinutes = Integer.parseInt(pr.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    p.reminderMinutes = -1;
                }
            }
        } else {
            p.reminderMinutes = -1;
        }
        p.timeShiftMinutes = optInt(o, "timeShiftMinutes");
        return p;
    }

    private ParsedSchedule parseScheduleObj(JsonObject o) {
        ParsedSchedule p = new ParsedSchedule();
        p.title = optStr(o, "title");
        p.date = optStr(o, "date");
        p.time = optStr(o, "time");
        p.location = optStr(o, "location");
        p.reminderMinutes = optInt(o, "reminderMinutes");
        p.needClarification = optBool(o, "needClarification");
        p.question = optStr(o, "question");
        return p;
    }

    /**
     * 留痕：把 AI 原始输出与解析出的 intent/target 一并存进 jsonResult，
     * 便于在解析历史页回看"这句话被理解成什么意图、要操作哪个目标"。
     */
    private String wrapHistoryJson(String rawContent, ParsedCommand cmd) {
        JsonObject o = new JsonObject();
        o.addProperty("raw", rawContent);
        o.addProperty("intent", cmd.intent);
        if (cmd.target != null) {
            JsonObject t = new JsonObject();
            t.addProperty("titleKeyword", cmd.target.titleKeyword);
            t.addProperty("date", cmd.target.date);
            t.addProperty("time", cmd.target.time);
            t.addProperty("timePeriod", cmd.target.timePeriod);
            t.addProperty("locationKeyword", cmd.target.locationKeyword);
            o.add("target", t);
        }
        return o.toString();
    }

    private void postCmdSuccess(CommandCallback cb, ParsedCommand cmd, long historyId) {
        AppExecutors.getInstance().mainThread().execute(() -> cb.onSuccess(cmd, historyId));
    }

    private void postCmdError(CommandCallback cb, String msg) {
        AppExecutors.getInstance().mainThread().execute(() -> cb.onError(msg));
    }

    /** 从 OpenAI 兼容响应中取 choices[0].message.content。 */
    private String extractContent(String respBody) {
        JsonObject resp = JsonParser.parseString(respBody).getAsJsonObject();
        return resp.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private static String optStr(JsonObject o, String k) {
        return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsString() : "";
    }

    private static int optInt(JsonObject o, String k) {
        if (o.has(k) && o.get(k).isJsonPrimitive()) {
            JsonPrimitive p = o.get(k).getAsJsonPrimitive();
            if (p.isNumber()) {
                return p.getAsInt();
            }
            try {
                return Integer.parseInt(p.getAsString().trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static boolean optBool(JsonObject o, String k) {
        if (o.has(k) && o.get(k).isJsonPrimitive()) {
            JsonPrimitive p = o.get(k).getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            return "true".equalsIgnoreCase(p.getAsString().trim());
        }
        return false;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
