package com.qiu.aischedule.network;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.ApiConfig;
import com.qiu.aischedule.data.local.entity.ParseHistory;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.security.SecretStore;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * LLM 网络客户端：调用 OpenAI 兼容的 /chat/completions 接口（默认 DeepSeek）。
 * - 请求经 OkHttp 异步执行，**不在主线程**；
 * - 用 Gson 构造请求体与解析响应；
 * - 解析后写入 ParseHistory（AI 辅助过程留痕），再通过主线程回调通知 UI；
 * - 无 API Key / 网络失败时回调 onError，UI 降级为手动填写。
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

    public interface Callback {
        void onSuccess(ParsedSchedule parsed, long historyId, String rawJson);

        void onError(String message);
    }

    private static final String SYSTEM_PROMPT =
            "你是日程解析助手。把用户输入解析为 JSON，只输出 JSON 对象，不要任何解释或代码块标记。" +
                    "字段：title(字符串)、date(yyyy-MM-dd)、time(HH:mm)、location(字符串)、" +
                    "reminderMinutes(整数,0表示不提醒)、needClarification(布尔)、question(字符串)。" +
                    "若时间或关键信息不明确，请设 needClarification=true 并在 question 中说明要追问什么；否则 needClarification=false。" +
                    "今天的日期是 {today}。";

    public void parse(Context context, String userInput, Callback callback) {
        // 整体放在后台线程：读配置(Key 来自 EncryptedSharedPreferences)、发请求、写历史都在非主线程
        AppExecutors.getInstance().diskIO().execute(() -> {
            ScheduleRepository repo = ScheduleRepository.getInstance(context);
            ApiConfig cfg = repo.getApiConfigSync();
            String key = SecretStore.getApiKey(context);

            if (cfg == null || key == null || key.isEmpty()) {
                postError(callback, context.getString(R.string.toast_no_apikey));
                return;
            }

            String model = notEmpty(cfg.modelName) ? cfg.modelName : "deepseek-chat";
            String baseUrl = notEmpty(cfg.baseUrl) ? cfg.baseUrl : "https://api.deepseek.com";
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            String body = buildBody(model, userInput);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + key)
                    .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    postError(callback, "网络失败：" + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        postError(callback, "接口返回 " + response.code());
                        return;
                    }
                    try {
                        String content = extractContent(respBody);
                        ParsedSchedule parsed = parseSchedule(content);

                        ParseHistory history = new ParseHistory();
                        history.inputText = userInput;
                        history.modelName = model;
                        history.jsonResult = content;
                        history.confidence = parsed.needClarification ? 0.3f : 0.9f;
                        history.createdAt = System.currentTimeMillis();
                        history.isApplied = false;
                        long historyId = repo.insertHistorySync(history);

                        postSuccess(callback, parsed, historyId, content);
                    } catch (Exception ex) {
                        postError(callback, "解析失败：" + ex.getMessage());
                    }
                }
            });
        });
    }

    private String buildBody(String model, String userInput) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", SYSTEM_PROMPT.replace("{today}", DateUtils.formatDate(System.currentTimeMillis())));
        messages.add(sys);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userInput);
        messages.add(user);

        root.add("messages", messages);
        root.addProperty("temperature", 0);
        return root.toString();
    }

    /** 从 OpenAI 兼容响应中取 choices[0].message.content。 */
    private String extractContent(String respBody) {
        JsonObject resp = JsonParser.parseString(respBody).getAsJsonObject();
        return resp.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    /** 从模型输出中抽取首个 JSON 对象并解析为 ParsedSchedule（兼容代码块/多余文字）。 */
    private ParsedSchedule parseSchedule(String content) {
        int s = content.indexOf('{');
        int e = content.lastIndexOf('}');
        if (s < 0 || e < 0 || e < s) {
            throw new RuntimeException("未找到 JSON");
        }
        JsonObject o = JsonParser.parseString(content.substring(s, e + 1)).getAsJsonObject();
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

    private void postSuccess(Callback cb, ParsedSchedule p, long historyId, String rawJson) {
        AppExecutors.getInstance().mainThread().execute(() -> cb.onSuccess(p, historyId, rawJson));
    }

    private void postError(Callback cb, String msg) {
        AppExecutors.getInstance().mainThread().execute(() -> cb.onError(msg));
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
