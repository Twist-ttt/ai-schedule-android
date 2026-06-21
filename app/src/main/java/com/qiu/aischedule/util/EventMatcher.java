package com.qiu.aischedule.util;

import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.network.TargetSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 在真实日程列表里按 {@link TargetSpec} 匹配候选。
 * <p><b>硬过滤（排除）</b>：titleKeyword（双向 contains）、date（命中当天）。
 * 为空则不强制。date 解析失败时不强制（避免因日期解析问题 0 命中）。
 * <p><b>软打分（仅排序，不排除）</b>：time 精确/接近、timePeriod 命中区间、locationKeyword contains。
 * <p>返回按分降序的候选列表；调用方据 size 判断 0/1/N（0=未找到，1=直接命中，N=弹候选）。
 * <p>这样设计既能用日期/标题收敛，又不会因 LLM 猜偏一个具体时间而 0 命中（time 只排序不排除）。
 */
public final class EventMatcher {

    private EventMatcher() {
    }

    public static List<EventRecord> match(List<EventRecord> events, TargetSpec t) {
        if (events == null || t == null) {
            return new ArrayList<>();
        }

        boolean hasTitle = notEmpty(t.titleKeyword);
        long dayStart = notEmpty(t.date) ? DateUtils.parseDateMillis(t.date) : 0L;
        boolean hasDate = dayStart > 0;
        long[] dateRange = hasDate ? DateUtils.dayRange(dayStart) : null;

        List<Scored> scored = new ArrayList<>();
        for (EventRecord e : events) {
            // 硬过滤
            if (hasTitle && !titleContains(e.title, t.titleKeyword)) {
                continue;
            }
            if (dateRange != null && (e.startTime < dateRange[0] || e.startTime >= dateRange[1])) {
                continue;
            }
            scored.add(new Scored(e, score(e, t)));
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<EventRecord> out = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            out.add(s.event);
        }
        return out;
    }

    private static int score(EventRecord e, TargetSpec t) {
        int s = 0;
        int[] ehm = DateUtils.hourMinuteOf(e.startTime);

        // time：精确 / 接近
        if (notEmpty(t.time)) {
            int[] thm = DateUtils.parseHourMinute(t.time);
            if (thm != null) {
                int diff = Math.abs((ehm[0] * 60 + ehm[1]) - (thm[0] * 60 + thm[1]));
                if (diff == 0) {
                    s += 3;
                } else if (diff <= 15) {
                    s += 2;
                } else if (diff <= 30) {
                    s += 1;
                }
            }
        }

        // timePeriod：命中区间
        if (notEmpty(t.timePeriod) && periodMatch(ehm[0], t.timePeriod)) {
            s += 2;
        }

        // locationKeyword：contains
        if (notEmpty(t.locationKeyword) && e.location != null && e.location.contains(t.locationKeyword)) {
            s += 2;
        }
        return s;
    }

    private static boolean periodMatch(int hour, String period) {
        switch (period) {
            case "morning":
                return hour >= 5 && hour < 12;
            case "afternoon":
                return hour >= 12 && hour < 18;
            case "evening":
                return hour >= 18 && hour < 22;
            case "night":
                return hour >= 22 || hour < 5;
            default:
                return false;
        }
    }

    /** 双向 contains（关键词可能是标题的子串，也可能反过来），忽略大小写。 */
    private static boolean titleContains(String title, String kw) {
        if (title == null) {
            return false;
        }
        String a = title.toLowerCase();
        String b = kw.toLowerCase();
        return a.contains(b) || b.contains(a);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static final class Scored {
        final EventRecord event;
        final int score;

        Scored(EventRecord event, int score) {
            this.event = event;
            this.score = score;
        }
    }
}
