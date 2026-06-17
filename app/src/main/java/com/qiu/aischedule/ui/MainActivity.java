package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;
import com.qiu.aischedule.network.LlmClient;
import com.qiu.aischedule.network.ParsedSchedule;

/**
 * 首页（输入页）。
 * - 「AI 解析」：调用 LLM 把一句话解析为结构化字段，自动回填到确认页（满足网络访问要求）；
 * - 「解析 / 手动填写」：降级路径，无 API Key/断网也能用；
 * - 「我的日程」进入看板页；菜单提供历史与设置。
 */
public class MainActivity extends AppCompatActivity {

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

        // 降级：直接手动填写
        btnToConfirm.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, text);
            startActivity(intent);
        });

        findViewById(R.id.btnToSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class)));
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
