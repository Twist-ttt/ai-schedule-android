package com.qiu.aischedule.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.util.DateUtils;

/**
 * 详情编辑页：查看 / 修改 / 删除单条日程。
 * 更新后重新设置提醒；删除后取消提醒。看板页经 ContentProvider 自动刷新。
 */
public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ScheduleRepository repo;
    private EventRecord current;

    private EditText etTitle;
    private EditText etDate;
    private EditText etTime;
    private EditText etLocation;
    private EditText etReminder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        repo = ScheduleRepository.getInstance(this);
        long id = getIntent().getLongExtra(EXTRA_EVENT_ID, -1L);

        etTitle = findViewById(R.id.etTitle);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        etLocation = findViewById(R.id.etLocation);
        etReminder = findViewById(R.id.etReminder);

        repo.observeEvent(id).observe(this, event -> {
            if (event == null) {
                return;
            }
            current = event;
            etTitle.setText(event.title);
            etDate.setText(DateUtils.formatDate(event.startTime));
            etTime.setText(DateUtils.formatTime(event.startTime));
            etLocation.setText(event.location);
            etReminder.setText(String.valueOf(event.reminderMinutes));
        });

        findViewById(R.id.btnUpdate).setOnClickListener(v -> {
            if (current == null) {
                return;
            }
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
            current.title = title;
            current.startTime = start;
            current.endTime = start;
            current.location = etLocation.getText().toString().trim();
            current.reminderMinutes = parseInt(etReminder.getText().toString().trim());
            repo.updateEvent(current);
            reschedule(current);
            Toast.makeText(this, R.string.toast_updated, Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (current == null) {
                return;
            }
            ReminderScheduler.cancel(this, current.id);
            repo.deleteEvent(current);
            Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void reschedule(EventRecord e) {
        if (e.reminderMinutes > 0) {
            long trigger = e.startTime - e.reminderMinutes * 60000L;
            if (trigger > System.currentTimeMillis()) {
                ReminderScheduler.schedule(this, e.id, trigger);
            } else {
                ReminderScheduler.cancel(this, e.id);
            }
        } else {
            ReminderScheduler.cancel(this, e.id);
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
