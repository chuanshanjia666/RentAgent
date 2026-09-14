package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 对话消息表：role 1user 2assistant 3tool；citations 为 RAG 来源引用（NFR-05） */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer role;

    private String content;

    private String toolName;

    private String toolArgs;

    private String toolResult;

    private String citations;

    private Integer tokenCount;

    private Integer latencyMs;

    private LocalDateTime createdAt;
}
