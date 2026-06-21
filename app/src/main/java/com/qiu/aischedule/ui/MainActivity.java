package com.qiu.aischedule.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.network.LlmClient;
import com.qiu.aischedule.network.ParsedCommand;
import com.qiu.aischedule.network.ParsedSchedule;
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;
import com.qiu.aischedule.util.EventMatcher;
import com.qiu.aischedule.util.SchedulePatcher;

import java.util.List;

/**
 * 首页（输入页）—— 统一自然语言入口。
 * 「AI 解析」一句话自动判断 create/edit/delete/clarify：
 *  - create：进确认页新建；
 *  - edit：按 TargetSpec 在真实库匹配，单条进 MODE_EDIT，多条进候选页（阶段3）；
 *  - delete：匹配后强确认删除（阶段2）；
 *  - clarify：Toast 追问。
 * 「手动填写」：降级路径；「我的日程」进看板；菜单：测试通知/历史/设置。
 * 首次进入请求 POST_NOTIFICATIONS（Android 13+）。
 */
public class MainActivity extends BaseActivity {

    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private EditText etInput;
    private Button btnAiParse;
    private Button btnToConfirm;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 首页 Toolbar 不显示标题：品牌名已由 Hero 卡片承载。
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayShowTitleEnabled(false);
        }

        etInput = findViewById(R.id.etInput);
        btnAiParse = findViewById(R.id.btnAiParse);
        btnToConfirm = findViewById(R.id.btnToConfirm);
        progressBar = findViewById(R.id.progressBar);

        requestNotificationPermissionIfNeeded();

        btnAiParse.setOnClickListener(v -> {
            final String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_input, Toast.LENGTH_SHORT).show();
                return;
            }
            setParsing(true);
            // 后台读全部日程作上下文，喂给 LLM 做统一意图判断
            AppExecutors.getInstance().diskIO().execute(() -> {
                final List<EventRecord> events =
                        ScheduleRepository.getInstance(this).getEventsAllSync();
                LlmClient.getInstance().interpret(this, text, events, new LlmClient.CommandCallback() {
                    @Override
                    public void onSuccess(ParsedCommand cmd, long historyId) {
                        setParsing(false);
                        handleCommand(cmd, historyId, text, events);
                    }

                    @Override
                    public void onError(String message) {
                        setParsing(false);
                        Toast.makeText(MainActivity.this, R.string.toast_ai_unavailable, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        btnToConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_MODE, AiConfirmActivity.MODE_MANUAL);
            startActivity(intent);
        });

        findViewById(R.id.btnToSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class)));
    }

    /** 按 AI 返回的意图分流 create/edit/delete/clarify。 */
    private void handleCommand(ParsedCommand cmd, long historyId, String input, List<EventRecord> events) {
        if (ParsedCommand.INTENT_CLARIFY.equals(cmd.intent)
                || (cmd.needClarification && cmd.question != null && !cmd.question.isEmpty())) {
            Toast.makeText(this, getString(R.string.toast_clarify_prefix) + cmd.question, Toast.LENGTH_LONG).show();
            return;
        }
        switch (cmd.intent) {
            case ParsedCommand.INTENT_EDIT:
                handleEdit(cmd, events, input, historyId);
                break;
            case ParsedCommand.INTENT_DELETE:
                handleDelete(cmd, events, historyId);
                break;
            case ParsedCommand.INTENT_CREATE:
            default:
                startCreate(cmd.schedule, input, historyId);
                break;
        }
    }

    /** create：带 schedule 字段进 AI 确认页。 */
    private void startCreate(ParsedSchedule parsed, String input, long historyId) {
        if (parsed == null) {
            parsed = new ParsedSchedule();
        }
        Intent intent = new Intent(this, AiConfirmActivity.class);
        intent.putExtra(AiConfirmActivity.EXTRA_MODE, AiConfirmActivity.MODE_AI);
        intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, input);
        intent.putExtra(AiConfirmActivity.EXTRA_TITLE, parsed.title);
        intent.putExtra(AiConfirmActivity.EXTRA_DATE, parsed.date);
        intent.putExtra(AiConfirmActivity.EXTRA_TIME, parsed.time);
        intent.putExtra(AiConfirmActivity.EXTRA_LOCATION, parsed.location);
        intent.putExtra(AiConfirmActivity.EXTRA_REMINDER, parsed.reminderMinutes);
        intent.putExtra(AiConfirmActivity.EXTRA_HISTORY_ID, historyId);
        startActivity(intent);
    }

    /** edit：按 TargetSpec 匹配真实日程，单条进 MODE_EDIT，多条进候选页（阶段3）。 */
    private void handleEdit(ParsedCommand cmd, List<EventRecord> events, String input, long historyId) {
        if (cmd.target == null) {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_LONG).show();
            return;
        }
        List<EventRecord> matches = EventMatcher.match(events, cmd.target);
        if (matches.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_match, cmd.target.titleKeyword), Toast.LENGTH_LONG).show();
            return;
        }
        if (matches.size() == 1) {
            EventRecord e = matches.get(0);
            SchedulePatcher.MergedFields f = SchedulePatcher.apply(e, cmd.patch);
            startEdit(e.id, f, input, historyId);
        } else {
            // 阶段 3 实现：多匹配弹候选列表。当前引导用户用更精确的描述。
            Toast.makeText(this, R.string.toast_multi_pending, Toast.LENGTH_LONG).show();
        }
    }

    /** delete：按 TargetSpec 匹配真实日程，单条强确认删除，多条进候选页（阶段3）。 */
    private void handleDelete(ParsedCommand cmd, List<EventRecord> events, long historyId) {
        if (cmd.target == null) {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_LONG).show();
            return;
        }
        List<EventRecord> matches = EventMatcher.match(events, cmd.target);
        if (matches.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_match, cmd.target.titleKeyword), Toast.LENGTH_LONG).show();
            return;
        }
        if (matches.size() == 1) {
            confirmAndDelete(matches.get(0), historyId);
        } else {
            // 阶段 3 实现：多匹配弹候选列表。当前引导用户用更精确的描述。
            Toast.makeText(this, R.string.toast_multi_pending, Toast.LENGTH_LONG).show();
        }
    }

    /** 删除强确认：显示标题+时间+地点，确认后取消提醒并删除。 */
    private void confirmAndDelete(EventRecord e, long historyId) {
        String time = DateUtils.formatDateTime(e.startTime);
        String loc = (e.location == null || e.location.trim().isEmpty())
                ? getString(R.string.event_no_location) : e.location;
        String msg = getString(R.string.dialog_delete_confirm_msg, e.title, time, loc);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_confirm_title)
                .setMessage(msg)
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .setPositiveButton(R.string.dialog_delete_ok, (d, w) -> {
                    ReminderScheduler.cancel(this, e.id);
                    ScheduleRepository.getInstance(this).deleteEvent(e);
                    if (historyId != -1L) {
                        ScheduleRepository.getInstance(this).markHistoryApplied(historyId, true);
                    }
                    Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** edit 单匹配：把合并字段预填进 MODE_EDIT 确认页。 */
    private void startEdit(long eventId, SchedulePatcher.MergedFields f, String input, long historyId) {
        Intent intent = new Intent(this, AiConfirmActivity.class);
        intent.putExtra(AiConfirmActivity.EXTRA_MODE, AiConfirmActivity.MODE_EDIT);
        intent.putExtra(AiConfirmActivity.EXTRA_EVENT_ID, eventId);
        intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, input);
        intent.putExtra(AiConfirmActivity.EXTRA_TITLE, f.title);
        intent.putExtra(AiConfirmActivity.EXTRA_DATE, DateUtils.formatDate(f.dateMillis));
        intent.putExtra(AiConfirmActivity.EXTRA_TIME,
                DateUtils.formatTime(DateUtils.combine(f.dateMillis, f.hour, f.minute)));
        intent.putExtra(AiConfirmActivity.EXTRA_LOCATION, f.location);
        intent.putExtra(AiConfirmActivity.EXTRA_REMINDER, f.reminder);
        intent.putExtra(AiConfirmActivity.EXTRA_HISTORY_ID, historyId);
        startActivity(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
            }
        }
    }

    private void setParsing(boolean parsing) {
        progressBar.setVisibility(parsing ? View.VISIBLE : View.GONE);
        btnAiParse.setEnabled(!parsing);
        btnToConfirm.setEnabled(!parsing);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_test_notify) {
            ReminderScheduler.schedule(this, -1L, System.currentTimeMillis() + 5000);
            Toast.makeText(this, R.string.toast_test_scheduled, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
