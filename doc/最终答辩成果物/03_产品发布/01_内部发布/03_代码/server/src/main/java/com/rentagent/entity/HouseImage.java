package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 房源图片表 */
@Data
@TableName("house_image")
public class HouseImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private String url;

    private Integer sort;

    private Integer isCover;

    private LocalDateTime createdAt;
}
