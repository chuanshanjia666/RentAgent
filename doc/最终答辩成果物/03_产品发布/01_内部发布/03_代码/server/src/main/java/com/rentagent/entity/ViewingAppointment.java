package com.rentagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看房预约表：status 0待确认 1已确认 2已拒绝 3已完成 4已取消（FR-17）。
 * <p>
 * 时段防重由 {@code active_slot} 上的唯一键 {@code uk_active_slot} 保证：待确认/已确认持有该键，
 * 终态（已拒绝/已完成/已取消）置为 NULL 释放时段，同一房源同一时段随即可以被重新预约。
 */
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

    /** 时段占位键，仅待确认/已确认非空；见类注释与 {@code AppointmentService#slotKey} */
    private String activeSlot;

    private String rejectReason;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
