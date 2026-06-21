package com.qiu.aischedule.util;

import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.network.SchedulePatch;

/**
 * 把 {@link SchedulePatch} 的改动量应用到原事件，产出 MODE_EDIT 预填用的「合并字段」。
 * <p>规则：
 * <ul>
 *   <li>title / location：patch 非空取 patch，否则取原值；</li>
 *   <li>reminder：patch.reminderMinutes ≥ 0 取 patch，否则取原值（−1=不改）；</li>
 *   <li>date / time：patch 有则取，否则取原 startTime 拆分；</li>
 *   <li>{@link SchedulePatch#timeShiftMinutes}：在合并后的时间点上整体平移，自动跨天。</li>
 * </ul>
 * 注意：此处只算"显示用合并字段"；真正的落库（保留 endTime/sourceText/status/时长）在
 * AiConfirmActivity MODE_EDIT 保存分支完成。
 */
public final class SchedulePatcher {

    private SchedulePatcher() {
    }

    public static MergedFields apply(EventRecord original, SchedulePatch p) {
        MergedFields m = new MergedFields();
        if (p == null) {
            p = new SchedulePatch();
        }

        m.title = notEmpty(p.title) ? p.title : (original.title == null ? "" : original.title);
        m.location = notEmpty(p.location) ? p.location : (original.location == null ? "" : original.location);
        m.reminder = p.reminderMinutes >= 0 ? p.reminderMinutes : original.reminderMinutes;

        // 日期：patch 有则取，否则取原 startTime 所在自然日；解析失败回退原值
        long baseDate = notEmpty(p.date) ? DateUtils.parseDateMillis(p.date) : 0L;
        if (baseDate <= 0) {
            baseDate = DateUtils.dayRange(original.startTime)[0];
        }
        // 时分：patch 有则取，否则取原 startTime
        int h;
        int min;
        int[] thm = notEmpty(p.time) ? DateUtils.parseHourMinute(p.time) : null;
        if (thm != null) {
            h = thm[0];
            min = thm[1];
        } else {
            int[] ohm = DateUtils.hourMinuteOf(original.startTime);
            h = ohm[0];
            min = ohm[1];
        }

        // 相对平移（推迟/提前）：在合并后的时间点上整体平移，再重算 date/h/m（自动跨天）
        if (p.timeShiftMinutes != 0) {
            long shifted = DateUtils.combine(baseDate, h, min) + p.timeShiftMinutes * 60000L;
            baseDate = DateUtils.dayRange(shifted)[0];
            int[] shm = DateUtils.hourMinuteOf(shifted);
            h = shm[0];
            min = shm[1];
        }

        m.dateMillis = baseDate;
        m.hour = h;
        m.minute = min;
        return m;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 合并后的显示字段，供 AiConfirmActivity MODE_EDIT 预填。 */
    public static final class MergedFields {
        public String title;
        public long dateMillis;
        public int hour;
        public int minute;
        public String location;
        public int reminder;
    }
}
