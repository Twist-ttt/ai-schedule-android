package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
public class AiConfirmActivity extends BaseActivity {

    public static final String EXTRA_SOURCE_TEXT = "extra_source_text";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_TIME = "extra_time";
    public static final String EXTRA_LOCATION = "extra_location";
    public static final String EXTRA_REMINDER = "extra_reminder";
    public static final String EXTRA_HISTORY_ID = "extra_history_id";

    /** 进入模式：AI 解析回填 / 手动填写。决定标题与「原始输入」引用块是否显示。 */
    public static final String EXTRA_MODE = "extra_mode";
    public static final int MODE_AI = 0;
    public static final int MODE_MANUAL = 1;

    private ScheduleRepository repo;
    private long historyId = -1L;

    // 日期/时间以结构化状态持有（由选择器写入），仅在显示时格式化为字符串。
    private EditText etDate;
    private EditText etTime;
    private long dateMillis;
    private int hour;
    private int minute;

    @Override
    protected boolean showUpNavigation() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_confirm);

        repo = ScheduleRepository.getInstance(this);

        Intent it = getIntent();
        int mode = it.getIntExtra(EXTRA_MODE, MODE_MANUAL); // 漏传时退化为自洽的「新建」表单
        boolean isAi = (mode == MODE_AI);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isAi ? R.string.confirm_title : R.string.manual_confirm_title);
        }

        String sourceText = it.getStringExtra(EXTRA_SOURCE_TEXT);
        // 「原始输入」引用块仅 AI 模式显示——手动填写就是普通新建表单
        findViewById(R.id.sourceBlock).setVisibility(isAi ? View.VISIBLE : View.GONE);
        TextView tvSource = findViewById(R.id.tvSource);
        if (isAi) {
            tvSource.setText((sourceText == null || sourceText.isEmpty())
                    ? getString(R.string.manual_entry_hint) : sourceText);
        }

        EditText etTitle = findViewById(R.id.etTitle);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        EditText etLocation = findViewById(R.id.etLocation);
        EditText etReminder = findViewById(R.id.etReminder);

        // 默认值：当前日期 + 当前时间（结构化）
        dateMillis = DateUtils.todayStart();
        int[] hm = DateUtils.hourMinuteOf(System.currentTimeMillis());
        hour = hm[0];
        minute = hm[1];

        // 字段只读，点击弹出选择器
        etDate.setOnClickListener(v -> showDatePicker());
        etTime.setOnClickListener(v -> showTimePicker());

        // AI 解析结果回填（如有）
        if (it.hasExtra(EXTRA_TITLE)) etTitle.setText(it.getStringExtra(EXTRA_TITLE));
        if (it.hasExtra(EXTRA_DATE)) {
            long d = DateUtils.parseDateMillis(it.getStringExtra(EXTRA_DATE));
            if (d > 0) dateMillis = d;
        }
        if (it.hasExtra(EXTRA_TIME)) {
            int[] t = DateUtils.parseHourMinute(it.getStringExtra(EXTRA_TIME));
            if (t != null) {
                hour = t[0];
                minute = t[1];
            }
        }
        if (it.hasExtra(EXTRA_LOCATION)) etLocation.setText(it.getStringExtra(EXTRA_LOCATION));
        if (it.hasExtra(EXTRA_REMINDER)) {
            etReminder.setText(String.valueOf(it.getIntExtra(EXTRA_REMINDER, 0)));
        }
        historyId = it.getLongExtra(EXTRA_HISTORY_ID, -1L);

        refreshDateTimeDisplay();

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.toast_empty_title, Toast.LENGTH_SHORT).show();
                return;
            }
            long start = DateUtils.combine(dateMillis, hour, minute);
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

    /** 弹出日期选择器；选中后更新 dateMillis 并刷新显示。 */
    private void showDatePicker() {
        GlassDatePickerDialog.newInstance(dateMillis, (year, month0, day) -> {
            dateMillis = DateUtils.ofDate(year, month0, day);
            refreshDateTimeDisplay();
        }).show(getSupportFragmentManager(), "date_picker");
    }

    /** 弹出时间选择器（时 00-23、分 00-59 转轮）；选中后更新 hour/minute 并刷新显示。 */
    private void showTimePicker() {
        GlassTimePickerDialog.newInstance(hour, minute, (h, m) -> {
            hour = h;
            minute = m;
            refreshDateTimeDisplay();
        }).show(getSupportFragmentManager(), "time_picker");
    }

    /** 把结构化日期/时间状态格式化显示到只读字段（系统本地化）。 */
    private void refreshDateTimeDisplay() {
        etDate.setText(DateUtils.formatDateLocalized(this, dateMillis));
        etTime.setText(DateUtils.formatTimeLocalized(this, DateUtils.combine(dateMillis, hour, minute)));
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
