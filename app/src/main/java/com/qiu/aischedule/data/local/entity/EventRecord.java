package com.qiu.aischedule.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 日程表实体（对应 PPT 的 EventRecord）。
 * 一条记录 = 用户确认后写入的一条日程。
 */
@Entity(tableName = "events", indices = {@Index(value = "startTime")})
public class EventRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 日程标题 */
    public String title;

    /** 开始时间，epoch 毫秒，便于排序与提醒计算 */
    public long startTime;

    /** 结束时间，epoch 毫秒 */
    public long endTime;

    /** 地点 */
    public String location;

    /** 提前提醒分钟数，0 表示不提醒 */
    public int reminderMinutes;

    /** 用户原始口语输入（证明"口语 → 结构化"链路） */
    public String sourceText;

    /** 状态：草稿 / 已保存 / 已完成 */
    public String status;
}
