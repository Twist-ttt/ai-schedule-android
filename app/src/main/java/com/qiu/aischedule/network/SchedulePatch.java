package com.qiu.aischedule.network;

/**
 * edit 的改动量。
 * <ul>
 *   <li>字符串字段：空串 = 不改；</li>
 *   <li>{@link #reminderMinutes}：−1 = 不改、0 = 取消提醒、&gt;0 = 提前 N 分钟；</li>
 *   <li>{@link #timeShiftMinutes}：0 = 不平移、正数推迟、负数提前（相对量，处理"推迟1小时"）。</li>
 * </ul>
 * <p>注意（Gson 兜底）：{@link #reminderMinutes} 字段初始化为 −1，且 LlmClient 解析时对缺字段
 * 显式兜底 −1，防止 Gson 把缺失的 int 字段反序列化成默认 0（会被误读为"取消提醒"）。
 * {@link #timeShiftMinutes} 默认 0 正好=不平移，无需额外兜底。
 */
public class SchedulePatch {

    public String title = "";
    public String date = "";
    public String time = "";
    public String location = "";
    public int reminderMinutes = -1;
    public int timeShiftMinutes = 0;
}
