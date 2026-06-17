package com.qiu.aischedule.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmManager 到点触发的广播接收器：读取对应日程并弹出通知。
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String EXTRA_EVENT_ID = "event_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L);
        android.util.Log.i("AlarmReceiver", "onReceive eventId=" + eventId);
        Notifier.showEventReminder(context, eventId);
    }
}
