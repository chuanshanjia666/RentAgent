package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI API 供应商配置表（管理员动态配置，取代仅依赖配置文件的旧方式）。
 * 支持配置多种协议模型（OpenAI Chat Completions / Anthropic Messages / OpenAI Responses），
 * is_active 标识当前全站生效的供应商。
 */
@Data
@TableName("ai_provider_config")
public class AiProviderConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商名称/描述（如 DeepSeek、OpenAI、通义千问等） */
    private String name;

    /** 通信协议：openai-chat-completions / anthropic-messages / openai-responses */
    private String protocol;

    /** 接口基址 */
    private String baseUrl;

    /** API Key 凭据 */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 最大 Token 预算 */
    private Integer maxTokens;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 自定义 User-Agent（可选） */
    private String userAgent;

    /** 是否当前生效：1是 0否 */
    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

