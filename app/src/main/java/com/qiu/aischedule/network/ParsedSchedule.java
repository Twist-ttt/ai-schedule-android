package com.qiu.aischedule.network;

/**
 * LLM 解析后的日程草稿（与确认页字段对应）。
 * needClarification=true 表示时间/关键信息不明确，需用户补充。
 */
public class ParsedSchedule {

    public String title = "";
    public String date = "";
    public String time = "";
    public String location = "";
    public int reminderMinutes = 0;
    public boolean needClarification = false;
    public String question = "";
}
