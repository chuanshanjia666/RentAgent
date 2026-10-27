package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 举报表：target_type 1房源 2评价 3用户（多态关联，业务层校验） */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    private Integer targetType;

    private Long targetId;

    private String reason;

    private Integer status;

    private Long handleBy;

    private String handleRemark;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
