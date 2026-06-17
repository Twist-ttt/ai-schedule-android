package com.qiu.aischedule.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 解析历史表（对应 PPT 的 ParseHistory）。
 * 记录每次"原始口语输入 → AI 返回 JSON"的过程，用于调试、回溯与展示 AI 辅助过程。
 */
@Entity(tableName = "parse_history", indices = {@Index(value = "createdAt")})
public class ParseHistory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 原始口语文本 */
    public String inputText;

    /** 调用的模型名称，如 deepseek-chat */
    public String modelName;

    /** AI 返回的 JSON 文本 */
    public String jsonResult;

    /** 解析可信度 0~1 */
    public float confidence;

    /** 解析时间，epoch 毫秒 */
    public long createdAt;

    /** 是否已被写入日程 */
    public boolean isApplied;
}
