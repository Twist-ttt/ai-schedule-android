package com.qiu.aischedule.util;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.notify.ReminderScheduler;

/**
 * 日程动作的共享入口（删除强确认等），供 MainActivity 与 CandidatePickerActivity 复用，
 * 避免两处各写一份删除确认逻辑。
 */
public final class ScheduleActions {

    private ScheduleActions() {
    }

    /**
     * 删除强确认：显示标题+时间+地点，确认后取消提醒并删除、标记历史已应用。
     *
     * @param afterDelete 删除完成后的回调（主线程），可为 null（如 MainActivity 删除后留在首页）；
     *                    CandidatePickerActivity 传 finish 以关闭候选页。
     */
    public static void confirmDelete(Activity ctx, EventRecord e, long historyId, Runnable afterDelete) {
        if (e == null) {
            return;
        }
        String time = DateUtils.formatDateTime(e.startTime);
        String loc = (e.location == null || e.location.trim().isEmpty())
                ? ctx.getString(R.string.event_no_location) : e.location;
        String msg = ctx.getString(R.string.dialog_delete_confirm_msg, e.title, time, loc);
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.dialog_delete_confirm_title)
                .setMessage(msg)
                .setNegativeButton(R.string.dialog_delete_cancel, null)
                .setPositiveButton(R.string.dialog_delete_ok, (d, w) -> {
                    ReminderScheduler.cancel(ctx, e.id);
                    ScheduleRepository.getInstance(ctx).deleteEvent(e);
                    if (historyId != -1L) {
                        ScheduleRepository.getInstance(ctx).markHistoryApplied(historyId, true);
                    }
                    Toast.makeText(ctx, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                    if (afterDelete != null) {
                        afterDelete.run();
                    }
                })
                .show();
    }
}
