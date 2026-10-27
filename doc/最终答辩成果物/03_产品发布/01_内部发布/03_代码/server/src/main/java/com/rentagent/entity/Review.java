package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评价表：lease_order_id UNIQUE 保证一单一评，仅完成合同可评（FR-20） */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long leaseOrderId;

    private Long houseId;

    private Long landlordId;

    private Long tenantId;

    private Integer houseScore;

    private Integer landlordScore;

    private String content;

    private Integer status;

    private LocalDateTime createdAt;
}
