package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 租赁订单表：status 0在租 1已退租 2已到期；contract_id 一对一 */
@Data
@TableName("lease_order")
public class LeaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private Long houseId;

    private Long tenantId;

    private Long landlordId;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal monthlyRent;

    private BigDecimal deposit;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
