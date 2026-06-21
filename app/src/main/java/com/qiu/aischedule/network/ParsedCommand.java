package com.qiu.aischedule.network;

/**
 * 统一自然语言指令解析结果。首页「AI 解析」据此分流 create / edit / delete / clarify。
 * <ul>
 *   <li>create：{@link #schedule} 承载新日程字段；</li>
 *   <li>edit：{@link #target} 为匹配条件（客户端在真实库匹配），{@link #patch} 为改动量；</li>
 *   <li>delete：{@link #target} 为匹配条件；</li>
 *   <li>clarify：{@link #needClarification}=true，{@link #question} 为追问。</li>
 * </ul>
 * 设计要点：LLM <b>不返回事件 id</b>，目标由客户端按 {@link #target} 在真实日程表匹配，规避幻觉。
 */
public class ParsedCommand {

    public static final String INTENT_CREATE = "create";
    public static final String INTENT_EDIT = "edit";
    public static final String INTENT_DELETE = "delete";
    public static final String INTENT_CLARIFY = "clarify";

    public String intent = INTENT_CREATE;

    /** edit / delete 的匹配条件。 */
    public TargetSpec target;
    /** edit 的改动量。 */
    public SchedulePatch patch;
    /** create 的新日程字段（复用现有 ParsedSchedule，保持其纯净）。 */
    public ParsedSchedule schedule;

    public boolean needClarification = false;
    public String question = "";
}
