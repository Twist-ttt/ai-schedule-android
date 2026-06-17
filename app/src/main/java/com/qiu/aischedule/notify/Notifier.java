package com.qiu.aischedule.notify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.ui.DetailActivity;
import com.qiu.aischedule.ui.MainActivity;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;

/**
 * 通知工具：创建通知渠道（API26+ 必需）并弹出通知。
 * 点击通知跳转日程详情（测试通知跳首页）。
 */
public final class Notifier {

    public static final String CHANNEL_ID = "schedule_reminder";
    private static final int TEST_NOTIF_ID = 9999;

    private Notifier() {
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        ctx.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("日程到点提醒");
                nm.createNotificationChannel(channel);
            }
        }
    }

    /** 弹出提醒通知。eventId=-1 表示测试通知。Room 读取放在后台线程。 */
    public static void showEventReminder(Context ctx, long eventId) {
        ensureChannel(ctx);
        AppExecutors.getInstance().diskIO().execute(() -> {
            String title;
            String text;
            PendingIntent contentIntent;
            int notifId;

            if (eventId == -1L) {
                title = ctx.getString(R.string.notif_test_title);
                text = ctx.getString(R.string.notif_test_text);
                contentIntent = openMainActivity(ctx);
                notifId = TEST_NOTIF_ID;
            } else {
                EventRecord e = ScheduleRepository.getInstance(ctx).getEventSync(eventId);
                if (e == null) {
                    return;
                }
                title = e.title;
                text = DateUtils.formatDateTime(e.startTime)
                        + (e.location != null && !e.location.isEmpty() ? "  ·  " + e.location : "");
                contentIntent = openDetail(ctx, e.id);
                notifId = (int) e.id;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);

            android.util.Log.i("Notifier", "posting notification id=" + notifId + " title=" + title);
            NotificationManagerCompat.from(ctx).notify(notifId, builder.build());
        });
    }

    private static PendingIntent openDetail(Context ctx, long eventId) {
        Intent intent = new Intent(ctx, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_EVENT_ID, eventId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, (int) eventId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openMainActivity(Context ctx) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
