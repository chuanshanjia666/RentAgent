package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 租金账单表：status 0待支付 1已支付 2已逾期（仅记录，不对接支付，FR-19） */
@Data
@TableName("rent_bill")
public class RentBill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long leaseOrderId;

    private Integer periodNo;

    private LocalDate dueDate;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime paidAt;

    private String remark;

    private LocalDateTime createdAt;
}
