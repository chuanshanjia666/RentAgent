package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 房源表：status 0待审核 1已通过 2已驳回 3已上架 4已下架 5已出租 */
@Data
@TableName("house")
public class House {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long landlordId;

    private String title;

    private String community;

    private String city;

    private String district;

    private String address;

    private String layout;

    private BigDecimal area;

    private String orientation;

    private String floorDesc;

    private BigDecimal rent;

    private String depositType;

    private String facilities;

    private String description;

    private String coverUrl;

    private BigDecimal lng;

    private BigDecimal lat;

    private Integer viewCount;

    private BigDecimal avgScore;

    private Integer status;

    private String rejectReason;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
