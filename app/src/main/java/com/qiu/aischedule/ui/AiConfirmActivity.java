package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.util.DateUtils;

/**
 * AI 确认页（阶段 2 为手动填写版）。
 * 展示原始输入（只读）+ 可编辑字段（标题/日期/时间/地点/提醒）。
 * 用户确认后写入数据库。阶段 4 起：进入本页前由 LLM 解析并自动回填这些字段。
 */
public class AiConfirmActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_TEXT = "extra_source_text";

    private ScheduleRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_confirm);

        repo = ScheduleRepository.getInstance(this);

        String sourceText = getIntent().getStringExtra(EXTRA_SOURCE_TEXT);
        TextView tvSource = findViewById(R.id.tvSource);
        tvSource.setText((sourceText == null || sourceText.isEmpty())
                ? getString(R.string.manual_entry_hint) : sourceText);

        EditText etTitle = findViewById(R.id.etTitle);
        EditText etDate = findViewById(R.id.etDate);
        EditText etTime = findViewById(R.id.etTime);
        EditText etLocation = findViewById(R.id.etLocation);
        EditText etReminder = findViewById(R.id.etReminder);

        long now = System.currentTimeMillis();
        etDate.setText(DateUtils.formatDate(now));
        etTime.setText(DateUtils.formatTime(now));
        etReminder.setText("0");

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.toast_empty_title, Toast.LENGTH_SHORT).show();
                return;
            }
            long start = DateUtils.parseDateTime(etDate.getText().toString(), etTime.getText().toString());
            if (start == 0) {
                Toast.makeText(this, R.string.toast_invalid_time, Toast.LENGTH_SHORT).show();
                return;
            }
            int reminder = parseInt(etReminder.getText().toString().trim());

            EventRecord event = new EventRecord();
            event.title = title;
            event.startTime = start;
            event.endTime = start;
            event.location = etLocation.getText().toString().trim();
            event.reminderMinutes = reminder;
            event.sourceText = sourceText == null ? "" : sourceText;
            event.status = getString(R.string.status_saved);

            repo.insertEvent(event);
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();

            // 保存后直接跳到看板页，便于演示
            startActivity(new Intent(this, ScheduleListActivity.class));
            finish();
        });
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
