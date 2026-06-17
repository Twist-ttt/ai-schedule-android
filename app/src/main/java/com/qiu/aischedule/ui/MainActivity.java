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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qiu.aischedule.R;
import com.qiu.aischedule.network.LlmClient;
import com.qiu.aischedule.network.ParsedSchedule;
import com.qiu.aischedule.notify.ReminderScheduler;

/**
 * 首页（输入页）。
 * - 「AI 解析」：调用 LLM 解析并自动回填确认页；
 * - 「解析 / 手动填写」：降级路径；
 * - 「我的日程」进入看板页；菜单提供 测试通知 / 历史 / 设置。
 * 首次进入请求 POST_NOTIFICATIONS（Android 13+ 通知所需运行时权限）。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private EditText etInput;
    private Button btnAiParse;
    private Button btnToConfirm;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInput = findViewById(R.id.etInput);
        btnAiParse = findViewById(R.id.btnAiParse);
        btnToConfirm = findViewById(R.id.btnToConfirm);
        progressBar = findViewById(R.id.progressBar);

        requestNotificationPermissionIfNeeded();

        btnAiParse.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_input, Toast.LENGTH_SHORT).show();
                return;
            }
            setParsing(true);
            LlmClient.getInstance().parse(this, text, new LlmClient.Callback() {
                @Override
                public void onSuccess(ParsedSchedule parsed, long historyId, String rawJson) {
                    setParsing(false);
                    if (parsed.needClarification && parsed.question != null && !parsed.question.isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.toast_clarify_prefix) + parsed.question,
                                Toast.LENGTH_LONG).show();
                    }
                    Intent intent = new Intent(MainActivity.this, AiConfirmActivity.class);
                    intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, text);
                    intent.putExtra(AiConfirmActivity.EXTRA_TITLE, parsed.title);
                    intent.putExtra(AiConfirmActivity.EXTRA_DATE, parsed.date);
                    intent.putExtra(AiConfirmActivity.EXTRA_TIME, parsed.time);
                    intent.putExtra(AiConfirmActivity.EXTRA_LOCATION, parsed.location);
                    intent.putExtra(AiConfirmActivity.EXTRA_REMINDER, parsed.reminderMinutes);
                    intent.putExtra(AiConfirmActivity.EXTRA_HISTORY_ID, historyId);
                    startActivity(intent);
                }

                @Override
                public void onError(String message) {
                    setParsing(false);
                    Toast.makeText(MainActivity.this, R.string.toast_parse_failed, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnToConfirm.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, text);
            startActivity(intent);
        });

        findViewById(R.id.btnToSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class)));
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
