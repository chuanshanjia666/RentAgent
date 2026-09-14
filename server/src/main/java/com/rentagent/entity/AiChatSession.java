package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话表：scene 1找房助手 2智能客服 3合同解读 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer scene;

    private String title;

    private String contextSummary;

    private Integer isTransferred;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
