package com.qiu.aischedule.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * API 配置表（对应 PPT 的 ApiConfig）。
 * 单行配置（固定 id=1），保存 LLM 接口的 provider/baseUrl/modelName 等；
 * API Key 本身不明文入库，仅保存引用别名，实际密钥用 EncryptedSharedPreferences 存储。
 */
@Entity(tableName = "api_config")
public class ApiConfig {

    /** 固定单行 id */
    @PrimaryKey
    public long id = 1L;

    /** 提供方：deepseek / openai / glm 等 */
    public String provider;

    /** 接口地址，如 https://api.deepseek.com */
    public String baseUrl;

    /** 模型名称，如 deepseek-chat */
    public String modelName;

    /** 密钥引用别名（明文 Key 存于 EncryptedSharedPreferences） */
    public String keyAlias;

    public long createdAt;

    public long updatedAt;
}
