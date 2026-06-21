package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;

/**
 * AI 确认页（三种模式）：
 * - {@link #MODE_AI}：由「AI 解析」(create) 进入，字段被自动回填；
 * - {@link #MODE_MANUAL}：由「手动填写」进入，字段可手动编辑（降级路径）；
 * - {@link #MODE_EDIT}：由自然语言修改（edit）进入，字段已合并好「原值+改动」预填。
 * <p>保存：后台线程写入。AI/手动走 insert；EDIT 走 update——<b>先读原始 EventRecord 再 patch</b>，
 * 保留 endTime/sourceText/status，且改开始时间时保持原时长（newEndTime = newStart + oldDuration）。
 * 保存后弹反馈对话框（明确告知是否已设提醒），点击「查看日程」进看板页。
 */
public class AiConfirmActivity extends BaseActivity {

    public static final String EXTRA_SOURCE_TEXT = "extra_source_text";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_TIME = "extra_time";
    public static final String EXTRA_LOCATION = "extra_location";
    public static final String EXTRA_REMINDER = "extra_reminder";
    public static final String EXTRA_HISTORY_ID = "extra_history_id";
    /** MODE_EDIT 时携带要修改的原日程 id。 */
    public static final String EXTRA_EVENT_ID = "extra_event_id";

    /** 进入模式。决定标题与「原始输入」引用块是否显示。 */
    public static final String EXTRA_MODE = "extra_mode";
    public static final int MODE_AI = 0;
    public static final int MODE_MANUAL = 1;
    public static final int MODE_EDIT = 2;

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
        final int mode = it.getIntExtra(EXTRA_MODE, MODE_MANUAL); // 漏传时退化为「新建」表单
        boolean showSource = (mode == MODE_AI || mode == MODE_EDIT);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(mode == MODE_EDIT
                    ? R.string.confirm_edit_title
                    : (mode == MODE_AI ? R.string.confirm_title : R.string.manual_confirm_title));
        }

        String sourceText = it.getStringExtra(EXTRA_SOURCE_TEXT);
        // 「原始输入/修改指令」引用块：AI 回填与修改模式显示；手动填写隐藏
        findViewById(R.id.sourceBlock).setVisibility(showSource ? View.VISIBLE : View.GONE);
        TextView tvSource = findViewById(R.id.tvSource);
        if (showSource) {
            tvSource.setText((sourceText == null || sourceText.isEmpty())
                    ? getString(R.string.manual_entry_hint) : sourceText);
        }

        EditText etTitle = findViewById(R.id.etTitle);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        final EditText etLocation = findViewById(R.id.etLocation);
        final EditText etReminder = findViewById(R.id.etReminder);

        // 默认值：当前日期 + 当前时间（结构化）
        dateMillis = DateUtils.todayStart();
        int[] hm = DateUtils.hourMinuteOf(System.currentTimeMillis());
        hour = hm[0];
        minute = hm[1];

        // 字段只读，点击弹出选择器
        etDate.setOnClickListener(v -> showDatePicker());
        etTime.setOnClickListener(v -> showTimePicker());

        // 回填（AI 回填或 EDIT 合并字段共用同一组 EXTRAs）
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
            final long start = DateUtils.combine(dateMillis, hour, minute);
            final int reminder = parseInt(etReminder.getText().toString().trim());
            final String location = etLocation.getText().toString().trim();
            final int modeFinal = mode;
            final String sourceFinal = sourceText == null ? "" : sourceText;
            final long editId = it.getLongExtra(EXTRA_EVENT_ID, -1L);

            // 后台线程：写入 + 设置提醒
            AppExecutors.getInstance().diskIO().execute(() -> {
                if (modeFinal == MODE_EDIT) {
                    saveEdit(editId, title, start, reminder, location);
                } else {
                    saveCreate(title, start, reminder, location, sourceFinal);
                }
            });
        });
    }

    /** 新建（AI/手动）：insert + 设提醒。 */
    private void saveCreate(String title, long start, int reminder, String location, String sourceText) {
        EventRecord event = new EventRecord();
        event.title = title;
        event.startTime = start;
        event.endTime = start;
        event.location = location;
        event.reminderMinutes = reminder;
        event.sourceText = sourceText;
        event.status = getString(R.string.status_saved);

        long newId = repo.insertEventSync(event);
        boolean willRemind = false;
        if (reminder > 0) {
            long trigger = start - reminder * 60000L;
            if (trigger > System.currentTimeMillis()) {
                ReminderScheduler.schedule(getApplicationContext(), newId, trigger);
                willRemind = true;
            }
        }
        if (historyId != -1L) {
            repo.markHistoryApplied(historyId, true);
        }
        final boolean reminded = willRemind;
        final int remindMin = reminder;
        runOnUiThread(() -> showSavedDialog(R.string.dialog_saved_title, reminded, remindMin));
    }

    /**
     * 修改（EDIT）：先读原始 EventRecord 再 patch，保留 endTime/sourceText/status；
     * 改开始时间时保持原时长（duration≤0 兜底 1 小时）。
     */
    private void saveEdit(long eventId, String title, long start, int reminder, String location) {
        EventRecord orig = repo.getEventSync(eventId);
        if (orig == null) {
            runOnUiThread(() -> Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
            return;
        }
        long duration = orig.endTime - orig.startTime;
        if (duration <= 0) {
            duration = 3_600_000L; // 兜底 1 小时
        }
        orig.title = title;
        orig.startTime = start;
        orig.endTime = start + duration; // 改开始时间也保时长
        orig.location = location;
        orig.reminderMinutes = reminder;
        // sourceText / status 不动（保留创建时来源；修改指令完整记在 ParseHistory）
        repo.updateEvent(orig);
        reschedule(orig);
        if (historyId != -1L) {
            repo.markHistoryApplied(historyId, true);
        }
        final boolean reminded = reminder > 0
                && (start - reminder * 60000L > System.currentTimeMillis());
        final int remindMin = reminder;
        runOnUiThread(() -> showSavedDialog(R.string.dialog_updated_title, reminded, remindMin));
    }

    /** 更新后重设提醒：reminder>0 且未来则 schedule，否则 cancel（复制自 DetailActivity 范式）。 */
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

    /** 保存/更新成功反馈：标题 + 三态（已提醒/未提醒/已过）。 */
    private void showSavedDialog(int titleRes, boolean willRemind, int reminderMinutes) {
        String msg;
        if (willRemind) {
            msg = getString(R.string.dialog_saved_reminder_set, reminderMinutes);
        } else if (reminderMinutes > 0) {
            msg = getString(R.string.dialog_saved_reminder_passed);
        } else {
            msg = getString(R.string.dialog_saved_no_reminder);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_saved_ok, (d, w) -> {
                    startActivity(new Intent(this, ScheduleListActivity.class));
                    finish();
                })
                .show();
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
