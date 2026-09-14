package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.entity.House;
import com.rentagent.entity.SysUser;
import com.rentagent.entity.ViewingAppointment;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.mapper.ViewingAppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 看房预约（FR-17）：状态流转 待确认→已确认/已拒绝→已完成，时段唯一防冲突 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    public static final int ST_PENDING = 0;
    public static final int ST_CONFIRMED = 1;
    public static final int ST_REJECTED = 2;
    public static final int ST_COMPLETED = 3;
    public static final int ST_CANCELLED = 4;

    private final ViewingAppointmentMapper mapper;
    private final HouseMapper houseMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notification;

    public ViewingAppointment create(long houseId, java.time.LocalDateTime time, String remark, long tenantId) {
        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() != HouseService.ST_ONLINE) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        if (tenantId == house.getLandlordId()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "不能预约自己的房源");
        }
        ViewingAppointment a = new ViewingAppointment();
        a.setHouseId(houseId);
        a.setTenantId(tenantId);
        a.setLandlordId(house.getLandlordId());
        a.setAppointmentTime(time);
        a.setStatus(ST_PENDING);
        a.setRemark(remark);
        try {
            mapper.insert(a);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.APPOINTMENT_CONFLICT);
        }
        notification.send(house.getLandlordId(), 1, "新的看房预约",
                userMapper.selectById(tenantId).getNickname() + " 预约看房「" + house.getTitle() + "」", "appointment", a.getId());
        return a;
    }

    /** confirm/reject（房东）、cancel（租客）、complete（房东） */
    public void act(long id, String action, String reason, long uid, int role) {
        ViewingAppointment a = mapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.APPOINTMENT_STATUS_INVALID);
        }
        boolean isLandlord = a.getLandlordId() == uid;
        boolean isTenant = a.getTenantId() == uid;
        switch (action) {
            case "confirm" -> {
                require(isLandlord, ErrorCode.FORBIDDEN);
                from(a, ST_PENDING);
                a.setStatus(ST_CONFIRMED);
                notifyTenant(a, "看房预约已确认", "房东已确认您对「" + houseTitle(a) + "」的预约");
            }
            case "reject" -> {
                require(isLandlord, ErrorCode.FORBIDDEN);
                from(a, ST_PENDING);
                a.setStatus(ST_REJECTED);
                a.setRejectReason(reason);
                notifyTenant(a, "看房预约已拒绝", "房东拒绝了您的预约：" + (reason == null ? "时间不合适" : reason));
            }
            case "cancel" -> {
                require(isTenant, ErrorCode.FORBIDDEN);
                if (a.getStatus() != ST_PENDING && a.getStatus() != ST_CONFIRMED) {
                    throw new BizException(ErrorCode.APPOINTMENT_STATUS_INVALID);
                }
                a.setStatus(ST_CANCELLED);
                notification.send(a.getLandlordId(), 1, "看房预约已取消",
                        "租客取消了「" + houseTitle(a) + "」的预约", "appointment", a.getId());
            }
            case "complete" -> {
                require(isLandlord, ErrorCode.FORBIDDEN);
                from(a, ST_CONFIRMED);
                a.setStatus(ST_COMPLETED);
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID);
        }
        mapper.updateById(a);
    }

    public Page<Map<String, Object>> pageFor(long uid, boolean landlordSide, long page, long size) {
        Page<ViewingAppointment> p = mapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ViewingAppointment>()
                        .eq(landlordSide, ViewingAppointment::getLandlordId, uid)
                        .eq(!landlordSide, ViewingAppointment::getTenantId, uid)
                        .orderByDesc(ViewingAppointment::getId));
        Page<Map<String, Object>> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public Map<String, Object> toVO(ViewingAppointment a) {
        House house = houseMapper.selectById(a.getHouseId());
        SysUser tenant = userMapper.selectById(a.getTenantId());
        Map<String, Object> vo = new HashMap<>();
        vo.put("appointment", a);
        vo.put("houseTitle", house == null ? null : house.getTitle());
        vo.put("houseCover", house == null ? null : house.getCoverUrl());
        vo.put("tenantName", tenant == null ? null : tenant.getNickname());
        vo.put("rent", house == null ? null : house.getRent());
        return vo;
    }

    private void from(ViewingAppointment a, int expected) {
        if (a.getStatus() != expected) {
            throw new BizException(ErrorCode.APPOINTMENT_STATUS_INVALID);
        }
    }

    private void require(boolean ok, ErrorCode code) {
        if (!ok) {
            throw new BizException(code);
        }
    }

    private String houseTitle(ViewingAppointment a) {
        House h = houseMapper.selectById(a.getHouseId());
        return h == null ? "房源" : h.getTitle();
    }

    private void notifyTenant(ViewingAppointment a, String title, String content) {
        notification.send(a.getTenantId(), 1, title, content, "appointment", a.getId());
    }

    public List<ViewingAppointment> listByTenant(long uid) {
        return mapper.selectList(new LambdaQueryWrapper<ViewingAppointment>()
                .eq(ViewingAppointment::getTenantId, uid));
    }
}
