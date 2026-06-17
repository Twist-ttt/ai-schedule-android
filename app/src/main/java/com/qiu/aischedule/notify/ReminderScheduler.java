package com.qiu.aischedule.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * 提醒调度：用 AlarmManager 在"开始时间 - 提前分钟"注册一个定时广播，
 * 到点由 {@link AlarmReceiver} 弹出通知。App 退出后到点仍可提醒。
 * 使用 setAndAllowWhileIdle（无需 SCHEDULE_EXACT_ALARM 权限，Doze 下也能唤醒）。
 */
public final class ReminderScheduler {

    private ReminderScheduler() {
    }

    public static void schedule(Context ctx, long eventId, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, buildPendingIntent(ctx, eventId));
    }

    public static void cancel(Context ctx, long eventId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(buildPendingIntent(ctx, eventId));
        }
    }

    private static PendingIntent buildPendingIntent(Context ctx, long eventId) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId);
        // 用 eventId 作为 requestCode，保证每条日程的闹钟相互独立、可单独取消
        return PendingIntent.getBroadcast(ctx, (int) eventId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
