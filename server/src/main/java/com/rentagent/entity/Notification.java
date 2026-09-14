package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知表：type 1预约 2审核 3签约 4账单 5举报 9系统 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer type;

    private String title;

    private String content;

    private String refType;

    private Long refId;

    private Integer isRead;

    private LocalDateTime createdAt;
}
