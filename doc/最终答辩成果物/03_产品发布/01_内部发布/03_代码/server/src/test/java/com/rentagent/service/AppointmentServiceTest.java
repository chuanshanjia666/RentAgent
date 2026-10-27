package com.rentagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.House;
import com.rentagent.entity.SysUser;
import com.rentagent.entity.ViewingAppointment;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.mapper.ViewingAppointmentMapper;
import com.rentagent.support.EntityMetadataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 看房预约单元测试（FR-17）：时段唯一防冲突、状态机流转、双方权限边界。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-APPT-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    private static final long TENANT = 2L;
    private static final long LANDLORD = 7L;
    private static final long STRANGER = 99L;

    @Mock
    private ViewingAppointmentMapper mapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private NotificationService notification;

    private AppointmentService service;

    /** 相对当前时间取未来时段：写死日期会在那一天之后变成"过去时间"而全线失败 */
    private final LocalDateTime slot = LocalDateTime.now().plusDays(2).withNano(0);

    @BeforeEach
    void setUp() {
        // act() 的状态流转走 LambdaUpdateWrapper.set(...)，需要渲染 lambda 列名
        EntityMetadataHelper.init(ViewingAppointment.class);
        service = new AppointmentService(mapper, houseMapper, userMapper, notification);
        SysUser tenant = new SysUser();
        tenant.setId(TENANT);
        tenant.setNickname("小陈");
        when(userMapper.selectById(anyLong())).thenReturn(tenant);
    }

    private House house(int status) {
        House h = new House();
        h.setId(101L);
        h.setLandlordId(LANDLORD);
        h.setTitle("软件园公寓 1 室 1 厅");
        h.setStatus(status);
        return h;
    }

    private ViewingAppointment appointment(int status, long tenantId, long landlordId) {
        ViewingAppointment a = new ViewingAppointment();
        a.setId(55L);
        a.setHouseId(101L);
        a.setTenantId(tenantId);
        a.setLandlordId(landlordId);
        a.setAppointmentTime(slot);
        a.setStatus(status);
        return a;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    // ── 创建预约 ──

    @Test
    @DisplayName("UT-APPT-01 预约不存在或非上架房源返回 2001")
    void rejectsNonOnlineHouse() {
        when(houseMapper.selectById(999L)).thenReturn(null);
        assertCode(2001, () -> service.create(999L, slot, null, TENANT));

        for (int status : new int[]{HouseService.ST_PENDING, HouseService.ST_PASSED,
                HouseService.ST_REJECTED, HouseService.ST_OFFLINE, HouseService.ST_RENTED}) {
            when(houseMapper.selectById(101L)).thenReturn(house(status));
            assertCode(2001, () -> service.create(101L, slot, null, TENANT));
        }
        verify(mapper, never()).insert(any(ViewingAppointment.class));
    }

    @Test
    @DisplayName("UT-APPT-02 不能预约自己的房源（1000）")
    void rejectsBookingOwnHouse() {
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));

        BizException e = assertThrows(BizException.class,
                () -> service.create(101L, slot, null, LANDLORD));
        assertEquals(1000, e.getCode());
        assertEquals("不能预约自己的房源", e.getMessage());
    }

    @Test
    @DisplayName("UT-APPT-13 过去时间不可预约（1000），且不落库")
    void rejectsPastAppointmentTime() {
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));

        BizException e = assertThrows(BizException.class,
                () -> service.create(101L, LocalDateTime.now().minusMinutes(1), null, TENANT));

        assertEquals(1000, e.getCode());
        assertEquals("预约时间需晚于当前时间", e.getMessage());
        verify(mapper, never()).insert(any(ViewingAppointment.class));
    }

    @Test
    @DisplayName("UT-APPT-03 创建成功初始为待确认并通知房东")
    void createsPendingAppointmentAndNotifiesLandlord() {
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));
        when(mapper.insert(any(ViewingAppointment.class))).thenAnswer(inv -> {
            ((ViewingAppointment) inv.getArgument(0)).setId(55L);
            return 1;
        });

        ViewingAppointment created = service.create(101L, slot, "想看看房", TENANT);

        assertEquals(AppointmentService.ST_PENDING, created.getStatus());
        assertEquals(LANDLORD, created.getLandlordId());
        assertEquals(slot, created.getAppointmentTime());
        assertEquals("想看看房", created.getRemark());
        // 占位键与唯一键 uk_active_slot 对应：同一房源同一时段的有效预约互斥
        assertEquals("101:" + slot, created.getActiveSlot());
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notification).send(eq(LANDLORD), eq(1), title.capture(), anyString(), eq("appointment"), eq(55L));
        assertEquals("新的看房预约", title.getValue());
    }

    @Test
    @DisplayName("UT-APPT-04 同房源同时段重复预约冲突（3001）且不产生通知")
    void rejectsDuplicateSlot() {
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));
        when(mapper.insert(any(ViewingAppointment.class))).thenThrow(new DuplicateKeyException("uk_active_slot"));

        assertCode(3001, () -> service.create(101L, slot, null, TENANT));

        verify(notification, never()).send(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("UT-APPT-05 时段互斥只约束有效预约：待确认/已确认持有 active_slot，终态置 null 释放")
    void onlyActiveStatusesHoldSlot() {
        assertTrue(AppointmentService.holdsSlot(AppointmentService.ST_PENDING));
        assertTrue(AppointmentService.holdsSlot(AppointmentService.ST_CONFIRMED));
        assertFalse(AppointmentService.holdsSlot(AppointmentService.ST_REJECTED));
        assertFalse(AppointmentService.holdsSlot(AppointmentService.ST_COMPLETED));
        assertFalse(AppointmentService.holdsSlot(AppointmentService.ST_CANCELLED));

        assertEquals("101:" + slot, AppointmentService.slotKey(101L, slot));
    }

    // ── 房东侧动作 ──

    @Test
    @DisplayName("UT-APPT-06 预约不存在返回 3002")
    void returns3002WhenAppointmentMissing() {
        when(mapper.selectById(55L)).thenReturn(null);

        assertCode(3002, () -> service.act(55L, "confirm", null, LANDLORD, 2));
    }

    @Test
    @DisplayName("UT-APPT-07 房东确认预约：仅待确认态可确认，越权与非待确认一律拒绝")
    void landlordConfirmsAppointment() {
        ViewingAppointment pending = appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD);
        when(mapper.selectById(55L)).thenReturn(pending);
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));

        service.act(55L, "confirm", null, LANDLORD, 2);

        assertEquals(AppointmentService.ST_CONFIRMED, pending.getStatus());
        // 走显式 update 而非 updateById：终态需要把 active_slot 写成 null 来释放时段
        verify(mapper).update(any(), any());
        verify(mapper, never()).updateById(any(ViewingAppointment.class));
        verify(notification).send(eq(TENANT), eq(1), eq("看房预约已确认"), anyString(), eq("appointment"), eq(55L));

        // 非房东
        when(mapper.selectById(55L)).thenReturn(appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD));
        assertCode(1007, () -> service.act(55L, "confirm", null, STRANGER, 1));
        // 状态已是已确认，重复确认
        when(mapper.selectById(55L)).thenReturn(appointment(AppointmentService.ST_CONFIRMED, TENANT, LANDLORD));
        assertCode(3002, () -> service.act(55L, "confirm", null, LANDLORD, 2));
    }

    @Test
    @DisplayName("UT-APPT-08 房东拒绝预约写入原因并通知租客")
    void landlordRejectsAppointment() {
        ViewingAppointment a = appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD);
        when(mapper.selectById(55L)).thenReturn(a);
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));

        service.act(55L, "reject", "时间不合适", LANDLORD, 2);

        assertEquals(AppointmentService.ST_REJECTED, a.getStatus());
        assertEquals("时间不合适", a.getRejectReason());
        verify(notification).send(eq(TENANT), eq(1), eq("看房预约已拒绝"), anyString(), eq("appointment"), eq(55L));
    }

    @Test
    @DisplayName("UT-APPT-09 租客取消：待确认/已确认可取消，已拒绝/已完成/已取消不可")
    void tenantCancelsAppointment() {
        ViewingAppointment pending = appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD);
        when(mapper.selectById(55L)).thenReturn(pending);
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));
        service.act(55L, "cancel", null, TENANT, 1);
        assertEquals(AppointmentService.ST_CANCELLED, pending.getStatus());

        ViewingAppointment confirmed = appointment(AppointmentService.ST_CONFIRMED, TENANT, LANDLORD);
        when(mapper.selectById(56L)).thenReturn(confirmed);
        service.act(56L, "cancel", null, TENANT, 1);
        assertEquals(AppointmentService.ST_CANCELLED, confirmed.getStatus());

        for (int status : new int[]{AppointmentService.ST_REJECTED, AppointmentService.ST_COMPLETED,
                AppointmentService.ST_CANCELLED}) {
            when(mapper.selectById(57L)).thenReturn(appointment(status, TENANT, LANDLORD));
            assertCode(3002, () -> service.act(57L, "cancel", null, TENANT, 1));
        }
        // 非租客本人（房东也不行）取消
        when(mapper.selectById(58L)).thenReturn(appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD));
        assertCode(1007, () -> service.act(58L, "cancel", null, LANDLORD, 2));
    }

    @Test
    @DisplayName("UT-APPT-10 房东完成看房：必须已确认态")
    void landlordCompletesViewing() {
        ViewingAppointment a = appointment(AppointmentService.ST_CONFIRMED, TENANT, LANDLORD);
        when(mapper.selectById(55L)).thenReturn(a);

        service.act(55L, "complete", null, LANDLORD, 2);
        assertEquals(AppointmentService.ST_COMPLETED, a.getStatus());

        when(mapper.selectById(56L)).thenReturn(appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD));
        assertCode(3002, () -> service.act(56L, "complete", null, LANDLORD, 2));

        when(mapper.selectById(57L)).thenReturn(appointment(AppointmentService.ST_CONFIRMED, TENANT, LANDLORD));
        assertCode(1007, () -> service.act(57L, "complete", null, STRANGER, 1));
    }

    @Test
    @DisplayName("UT-APPT-11 未知动作返回 1000 且不落库")
    void rejectsUnknownAction() {
        when(mapper.selectById(55L)).thenReturn(appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD));

        assertCode(1000, () -> service.act(55L, "approve", null, LANDLORD, 2));

        verify(mapper, never()).update(any(), any());
    }

    // ── 列表隔离 ──

    @Test
    @DisplayName("UT-APPT-12 我的预约列表按租客/房东两侧隔离并可组装房源信息")
    void scopesListBySide() {
        Page<ViewingAppointment> page = new Page<>(1, 10);
        page.setRecords(List.of(appointment(AppointmentService.ST_PENDING, TENANT, LANDLORD)));
        page.setTotal(1);
        doReturn(page).when(mapper).selectPage(any(), any());
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));

        PageVO<TradeDto.AppointmentVO> tenantSide = service.pageFor(TENANT, false, 1, 10);
        assertEquals(1, tenantSide.list().size());
        assertEquals("软件园公寓 1 室 1 厅", tenantSide.list().get(0).houseTitle());
        assertEquals("小陈", tenantSide.list().get(0).tenantName());

        PageVO<TradeDto.AppointmentVO> landlordSide = service.pageFor(LANDLORD, true, 1, 10);
        assertEquals(1, landlordSide.list().size());
        assertNull(landlordSide.list().get(0).houseCover());
    }
}
