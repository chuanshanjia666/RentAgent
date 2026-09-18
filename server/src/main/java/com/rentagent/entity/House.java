package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 房源表：status 0待审核 1已通过 2已驳回 3已上架 4已下架 5已出租 */
@Data
@TableName(value = "house", autoResultMap = true)
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

    /**
     * 设施清单（house.facilities 是 JSON 列）。用 typeHandler 统一成数组：
     * 早先实体声明为 String，导致"同一个逻辑字段三种形状"——入参是数组、
     * 出参是 JSON 字符串、AI 填充接口又是数组，前端被迫写
     * {@code facilities?: string | string[]} + 运行时解析，冒烟脚本也得按类型分支断言。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> facilities;

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
