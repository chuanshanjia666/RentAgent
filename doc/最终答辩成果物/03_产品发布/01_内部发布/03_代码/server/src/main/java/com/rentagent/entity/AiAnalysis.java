package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 分析结果表：type 1定价建议 2虚假房源检测 3合同解读 4房源信息识别（FR-08/14/15/16） */
@Data
@TableName("ai_analysis")
public class AiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer type;

    private String targetType;

    private Long targetId;

    private String inputSnapshot;

    private String result;

    private String model;

    private LocalDateTime createdAt;
}
