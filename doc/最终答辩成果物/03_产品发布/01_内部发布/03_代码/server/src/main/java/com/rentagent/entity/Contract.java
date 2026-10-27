package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 电子合同表：status 0待租客确认 1待房东确认 2已生效 3已退租 4已作废；clauses/risk_flags 为 JSON 字符串 */
@Data
@TableName("contract")
public class Contract {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private Long tenantId;

    private Long landlordId;

    private String clauses;

    private String riskFlags;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal monthlyRent;

    private BigDecimal deposit;

    private Integer status;

    private LocalDateTime signedTenantAt;

    private LocalDateTime signedLandlordAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
