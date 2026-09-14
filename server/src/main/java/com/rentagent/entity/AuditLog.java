package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志表：只增不改（FR-07/22/23 留痕） */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private String ip;

    private LocalDateTime createdAt;
}
