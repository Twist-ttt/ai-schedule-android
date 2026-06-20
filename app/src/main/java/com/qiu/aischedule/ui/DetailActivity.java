package com.qiu.aischedule.ui;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.util.DateUtils;

/**
 * 详情编辑页：查看 / 修改 / 删除单条日程。
 * 更新后重新设置提醒；删除后取消提醒。看板页经 ContentProvider 自动刷新。
 */
public class DetailActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ScheduleRepository repo;
    private EventRecord current;

    private EditText etTitle;
    private EditText etDate;
    private EditText etTime;
    private EditText etLocation;
    private EditText etReminder;

    // 日期/时间以结构化状态持有（由选择器写入），仅在显示时格式化为字符串。
    private long dateMillis;
    private int hour;
    private int minute;

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

        // 字段只读，点击弹出选择器
        etDate.setOnClickListener(v -> showDatePicker());
        etTime.setOnClickListener(v -> showTimePicker());

        repo.observeEvent(id).observe(this, event -> {
            if (event == null) {
                return;
            }
            current = event;
            etTitle.setText(event.title);
            // 从 startTime 拆出日期（本地零点）与时分
            dateMillis = DateUtils.dayRange(event.startTime)[0];
            int[] hm = DateUtils.hourMinuteOf(event.startTime);
            hour = hm[0];
            minute = hm[1];
            refreshDateTimeDisplay();
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
            long start = DateUtils.combine(dateMillis, hour, minute);
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

    /** 弹出日期选择器；选中后更新 dateMillis 并刷新显示。 */
    private void showDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.picker_date_title)
                .setSelection(DateUtils.localMidnightToUtc(dateMillis))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            dateMillis = DateUtils.utcMidnightToLocal(selection);
            refreshDateTimeDisplay();
        });
        picker.show(getSupportFragmentManager(), "date_picker");
    }

    /** 弹出时间选择器；选中后更新 hour/minute 并刷新显示（遵循系统 12/24 小时制）。 */
    private void showTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTitleText(R.string.picker_time_title)
                .setHour(hour)
                .setMinute(minute)
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            hour = picker.getHour();
            minute = picker.getMinute();
            refreshDateTimeDisplay();
        });
        picker.show(getSupportFragmentManager(), "time_picker");
    }

    /** 把结构化日期/时间状态格式化显示到只读字段（系统本地化）。 */
    private void refreshDateTimeDisplay() {
        etDate.setText(DateUtils.formatDateLocalized(this, dateMillis));
        etTime.setText(DateUtils.formatTimeLocalized(this, DateUtils.combine(dateMillis, hour, minute)));
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
