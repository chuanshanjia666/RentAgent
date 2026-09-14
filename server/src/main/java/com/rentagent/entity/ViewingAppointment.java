package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 看房预约表：status 0待确认 1已确认 2已拒绝 3已完成 4已取消；UNIQUE(house_id, appointment_time) 防重复预约（FR-17） */
@Data
@TableName("viewing_appointment")
public class ViewingAppointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private Long tenantId;

    private Long landlordId;

    private LocalDateTime appointmentTime;

    private Integer status;

    private String rejectReason;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
