package com.qiu.aischedule.network;

/**
 * edit / delete 的目标匹配条件（由 LLM 提取，客户端在真实库匹配）。
 * <ul>
 *   <li>{@link #titleKeyword} / {@link #date}：<b>硬过滤</b>（必须命中；为空则不强制）；</li>
 *   <li>{@link #time} / {@link #timePeriod} / {@link #locationKeyword}：<b>软打分</b>（仅排序，不排除）。</li>
 * </ul>
 * {@link #timePeriod} ∈ {morning, afternoon, evening, night}，承载"上午/下午/晚上/夜里"语义，
 * 避免把模糊时段强行转成 HH:mm（见 System Prompt 硬约束）。
 */
public class TargetSpec {

    public String titleKeyword = "";
    /** yyyy-MM-dd，命中当天；空则不强制。 */
    public String date = "";
    /** HH:mm，仅打分；空则不强制。 */
    public String time = "";
    /** morning / afternoon / evening / night，仅打分；空则不强制。 */
    public String timePeriod = "";
    /** 地点关键词，仅打分；空则不强制。 */
    public String locationKeyword = "";
}
