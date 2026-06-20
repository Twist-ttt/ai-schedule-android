package com.qiu.aischedule.util;

import android.content.Context;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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

    // ===== 日期/时间选择器支持 =====
    // MaterialDatePicker.selection 是「所选日期 UTC 零点」的毫秒，需在 UTC/本地时区间换算，
    // 否则跨时区会出现日期偏移（如 UTC+8 下本地零点被当成前一天）。

    /** MaterialDatePicker.selection（UTC 零点）→ 同年月日的本地零点 millis。 */
    public static long utcMidnightToLocal(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(Calendar.YEAR, utc.get(Calendar.YEAR));
        local.set(Calendar.MONTH, utc.get(Calendar.MONTH));
        local.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }

    /** 本地零点 millis → 同年月日的 UTC 零点 millis（供 MaterialDatePicker.setSelection）。 */
    public static long localMidnightToUtc(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(Calendar.YEAR, local.get(Calendar.YEAR));
        utc.set(Calendar.MONTH, local.get(Calendar.MONTH));
        utc.set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    /** 本地零点 + hour:minute → 当天该时刻的 millis（合并日期与时间为单个时间点）。 */
    public static long combine(long localMidnight, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(localMidnight);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 从 millis 取 [hourOfDay, minute]。 */
    public static int[] hourMinuteOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }

    /** "yyyy-MM-dd" → 本地零点 millis；失败返回 0（AI 回填日期用）。 */
    public static long parseDateMillis(String dateStr) {
        if (dateStr == null) return 0;
        try {
            return FMT_DATE.parse(dateStr.trim()).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /** "HH:mm" → [hour, minute]；失败返回 null（AI 回填时间用）。 */
    public static int[] parseHourMinute(String timeStr) {
        if (timeStr == null) return null;
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(FMT_TIME.parse(timeStr.trim()));
            return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
        } catch (ParseException e) {
            return null;
        }
    }

    // ===== 本地化显示（系统地区 + 12/24 小时自适应，不写死格式）=====

    /** 系统本地化日期显示，如「2026年6月20日」。 */
    public static String formatDateLocalized(Context ctx, long millis) {
        return millis <= 0 ? "" : android.text.format.DateUtils.formatDateTime(
                ctx, millis, android.text.format.DateUtils.FORMAT_SHOW_DATE);
    }

    /** 系统本地化时间显示，遵循系统 12/24 小时设置。 */
    public static String formatTimeLocalized(Context ctx, long millis) {
        return millis <= 0 ? "" : android.text.format.DateUtils.formatDateTime(
                ctx, millis, android.text.format.DateUtils.FORMAT_SHOW_TIME);
    }

    /** 相对时间（「5 分钟前」/「昨天」），用于解析历史等日志场景。 */
    public static CharSequence relative(long millis) {
        return millis <= 0 ? "" : android.text.format.DateUtils.getRelativeTimeSpanString(millis);
    }
}
