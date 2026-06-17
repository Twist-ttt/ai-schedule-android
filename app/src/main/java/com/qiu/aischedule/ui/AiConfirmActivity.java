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
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;

/**
 * AI 确认页。
 * - 由「AI 解析」进入：字段被自动回填（携带 historyId，保存时标记历史为已应用）；
 * - 由「手动填写」进入：字段可手动编辑（降级路径）。
 * 保存：后台线程写入数据库；若 reminderMinutes>0 且提醒时间在未来，则用 AlarmManager 设置提醒。
 */
public class AiConfirmActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_TEXT = "extra_source_text";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_TIME = "extra_time";
    public static final String EXTRA_LOCATION = "extra_location";
    public static final String EXTRA_REMINDER = "extra_reminder";
    public static final String EXTRA_HISTORY_ID = "extra_history_id";

    private ScheduleRepository repo;
    private long historyId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_confirm);

        repo = ScheduleRepository.getInstance(this);

        Intent it = getIntent();
        String sourceText = it.getStringExtra(EXTRA_SOURCE_TEXT);
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

        // AI 解析结果回填（如有）
        if (it.hasExtra(EXTRA_TITLE)) etTitle.setText(it.getStringExtra(EXTRA_TITLE));
        if (it.hasExtra(EXTRA_DATE)) {
            String d = it.getStringExtra(EXTRA_DATE);
            if (d != null && !d.isEmpty()) etDate.setText(d);
        }
        if (it.hasExtra(EXTRA_TIME)) {
            String t = it.getStringExtra(EXTRA_TIME);
            if (t != null && !t.isEmpty()) etTime.setText(t);
        }
        if (it.hasExtra(EXTRA_LOCATION)) etLocation.setText(it.getStringExtra(EXTRA_LOCATION));
        if (it.hasExtra(EXTRA_REMINDER)) {
            etReminder.setText(String.valueOf(it.getIntExtra(EXTRA_REMINDER, 0)));
        }
        historyId = it.getLongExtra(EXTRA_HISTORY_ID, -1L);

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

            // 后台线程：写入 DB + 设置提醒
            AppExecutors.getInstance().diskIO().execute(() -> {
                long newId = repo.insertEventSync(event);
                if (reminder > 0) {
                    long trigger = start - reminder * 60000L;
                    if (trigger > System.currentTimeMillis()) {
                        ReminderScheduler.schedule(getApplicationContext(), newId, trigger);
                    }
                }
                if (historyId != -1L) {
                    repo.markHistoryApplied(historyId, true);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, ScheduleListActivity.class));
                    finish();
                });
            });
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
