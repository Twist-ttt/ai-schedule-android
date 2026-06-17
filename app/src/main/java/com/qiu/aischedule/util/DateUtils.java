package com.qiu.aischedule.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期/时间工具：统一以 epoch 毫秒存储与转换。
 * 列表显示、提醒计算、按天筛选都依赖这里。
 */
public final class DateUtils {

    public static final SimpleDateFormat FMT_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    public static final SimpleDateFormat FMT_TIME = new SimpleDateFormat("HH:mm", Locale.getDefault());
    public static final SimpleDateFormat FMT_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private DateUtils() {
    }

    public static String formatDateTime(long millis) {
        return millis <= 0 ? "" : FMT_DATETIME.format(new Date(millis));
    }

    public static String formatDate(long millis) {
        return millis <= 0 ? "" : FMT_DATE.format(new Date(millis));
    }

    public static String formatTime(long millis) {
        return millis <= 0 ? "" : FMT_TIME.format(new Date(millis));
    }

    /** 解析"日期 + 时间"为 epoch 毫秒；失败返回 0。 */
    public static long parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || timeStr == null) {
            return 0;
        }
        try {
            return FMT_DATETIME.parse(dateStr.trim() + " " + timeStr.trim()).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /** 返回给定毫秒所在自然日的 [startMillis, endMillis) 区间。 */
    public static long[] dayRange(long anyMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(anyMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{start, cal.getTimeInMillis()};
    }

    public static long todayStart() {
        return dayRange(System.currentTimeMillis())[0];
    }
}
