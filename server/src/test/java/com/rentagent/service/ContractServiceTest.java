package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.BizException;
import com.rentagent.entity.Contract;
import com.rentagent.entity.House;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.RentBill;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.ContractMapper;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.LeaseOrderMapper;
import com.rentagent.mapper.RentBillMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.UserContext;
import com.rentagent.support.EntityMetadataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线签约与租金单元测试（FR-18/19）：合同状态机、签署顺序、订单与账单派生规则、账单支付权限。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-CONTRACT-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractServiceTest {

    private static final long TENANT = 2L;
    private static final long LANDLORD = 7L;
    private static final long STRANGER = 99L;

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private LeaseOrderMapper orderMapper;
    @Mock
    private RentBillMapper billMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private NotificationService notification;

    private ContractService service;

    @BeforeEach
    void setUp() {
        EntityMetadataHelper.init(House.class, Contract.class, LeaseOrder.class, RentBill.class);
        service = new ContractService(contractMapper, orderMapper, billMapper, houseMapper,
                userMapper, notification, new ObjectMapper());
        SysUser tenant = new SysUser();
        tenant.setId(TENANT);
        tenant.setNickname("小陈");
        SysUser landlord = new SysUser();
        landlord.setId(LANDLORD);
        landlord.setNickname("李房东");
        when(userMapper.selectById(TENANT)).thenReturn(tenant);
        when(userMapper.selectById(LANDLORD)).thenReturn(landlord);
        when(houseMapper.selectById(101L)).thenReturn(house(HouseService.ST_ONLINE));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private House house(int status) {
        House h = new House();
        h.setId(101L);
        h.setLandlordId(LANDLORD);
        h.setTitle("软件园公寓 1 室 1 厅");
        h.setCity("大连市");
        h.setDistrict("高新园区");
        h.setCommunity("软件园公寓");
        h.setLayout("1室1厅");
        h.setArea(new BigDecimal("45"));
        h.setRent(new BigDecimal("2100"));
        h.setDepositType("押一付三");
        h.setStatus(status);
        return h;
    }

    private Contract contract(int status, LocalDate start, LocalDate end) {
        Contract c = new Contract();
        c.setId(88L);
        c.setHouseId(101L);
        c.setTenantId(TENANT);
        c.setLandlordId(LANDLORD);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setMonthlyRent(new BigDecimal("2100"));
        c.setDeposit(new BigDecimal("2100"));
        c.setStatus(status);
        c.setClauses("[{\"title\":\"租期\",\"text\":\"租赁期自 2026-11-01 至 2027-11-01\"}]");
        return c;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    // ── 合同创建 ──

    @Test
    @DisplayName("UT-CONTRACT-01 非上架房源不可发起签约（2001）")
    void rejectsOfflineHouse() {
        for (int status : new int[]{HouseService.ST_PENDING, HouseService.ST_PASSED,
                HouseService.ST_REJECTED, HouseService.ST_OFFLINE, HouseService.ST_RENTED}) {
            when(houseMapper.selectById(101L)).thenReturn(house(status));
            assertCode(2001, () -> service.create(101L, LocalDate.now().plusDays(1),
                    LocalDate.now().plusMonths(12), TENANT));
        }
        when(houseMapper.selectById(999L)).thenReturn(null);
        assertCode(2001, () -> service.create(999L, LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(12), TENANT));
    }

    @Test
    @DisplayName("UT-CONTRACT-02 不能与自己发布的房源签约（1000）")
    void rejectsSigningOwnHouse() {
        BizException e = assertThrows(BizException.class, () -> service.create(101L,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(12), LANDLORD));
        assertEquals(1000, e.getCode());
        assertEquals("不能签约自己的房源", e.getMessage());
    }

    @Test
    @DisplayName("UT-CONTRACT-03 租期非法拒绝：起止无序、起止相同、起始早于今天（1000）")
    void rejectsInvalidTerm() {
        LocalDate today = LocalDate.now();
        assertCode(1000, () -> service.create(101L, today.plusMonths(12), today.plusMonths(1), TENANT));
        assertCode(1000, () -> service.create(101L, today.plusDays(1), today.plusDays(1), TENANT));
        assertCode(1000, () -> service.create(101L, today.minusDays(1), today.plusMonths(12), TENANT));
    }

    @Test
    @DisplayName("UT-CONTRACT-04 起始日为今天允许签约（边界）")
    void allowsTermStartingToday() {
        when(contractMapper.selectCount(any())).thenReturn(0L);
        when(contractMapper.insert(any(Contract.class))).thenAnswer(inv -> {
            ((Contract) inv.getArgument(0)).setId(88L);
            return 1;
        });

        Contract c = service.create(101L, LocalDate.now(), LocalDate.now().plusMonths(12), TENANT);

        assertEquals(ContractService.ST_TENANT_CONFIRM, c.getStatus());
        assertEquals(0, c.getStartDate().compareTo(LocalDate.now()));
    }

    @Test
    @DisplayName("UT-CONTRACT-05 同房源同租客已有进行中合同不可重复发起（3003）")
    void rejectsDuplicateActiveContract() {
        when(contractMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.create(101L,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(12), TENANT));
        assertEquals(3003, e.getCode());
        assertTrue(e.getMessage().contains("已存在进行中的合同"));
        verify(contractMapper, never()).insert(any(Contract.class));
    }

    @Test
    @DisplayName("UT-CONTRACT-06 合同创建成功：待租客确认、金额与押金取房源租金、条款非空并通知房东")
    void createsContract() {
        when(contractMapper.selectCount(any())).thenReturn(0L);
        when(contractMapper.insert(any(Contract.class))).thenAnswer(inv -> {
            ((Contract) inv.getArgument(0)).setId(88L);
            return 1;
        });

        Contract c = service.create(101L, LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13), TENANT);

        assertEquals(ContractService.ST_TENANT_CONFIRM, c.getStatus());
        assertEquals(new BigDecimal("2100"), c.getMonthlyRent());
        assertEquals(new BigDecimal("2100"), c.getDeposit());
        assertTrue(c.getClauses().contains("租赁标的"));
        assertTrue(c.getClauses().contains("小陈"));
        verify(notification).send(eq(LANDLORD), eq(3), eq("新的签约请求"), anyString(), eq("contract"), eq(88L));
    }

    // ── 双方签署 ──

    @Test
    @DisplayName("UT-CONTRACT-07 合同不存在返回 3004")
    void actFailsWhenContractMissing() {
        when(contractMapper.selectById(88L)).thenReturn(null);

        assertCode(3004, () -> service.act(88L, "sign", TENANT, 1));
    }

    @Test
    @DisplayName("UT-CONTRACT-08 租客签署后进入待房东确认并通知房东")
    void tenantSignAdvancesToLandlordConfirm() {
        Contract c = contract(ContractService.ST_TENANT_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13));
        when(contractMapper.selectById(88L)).thenReturn(c);

        Map<String, Object> result = service.act(88L, "sign", TENANT, 1);

        assertEquals(ContractService.ST_LANDLORD_CONFIRM, c.getStatus());
        assertNotNull(c.getSignedTenantAt());
        assertEquals(ContractService.ST_LANDLORD_CONFIRM, result.get("status"));
        verify(contractMapper).updateById(c);
        verify(notification).send(eq(LANDLORD), eq(3), eq("租客已确认合同"), anyString(), eq("contract"), eq(88L));
    }

    @Test
    @DisplayName("UT-CONTRACT-09 房东签署后合同生效：派生订单、按月生成账单、房源置已出租")
    void landlordSignActivatesAndDerivesOrderBills() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = start.plusMonths(12);
        Contract c = contract(ContractService.ST_LANDLORD_CONFIRM, start, end);
        when(contractMapper.selectById(88L)).thenReturn(c);
        when(orderMapper.insert(any(LeaseOrder.class))).thenAnswer(inv -> {
            ((LeaseOrder) inv.getArgument(0)).setId(66L);
            return 1;
        });
        House house = house(HouseService.ST_ONLINE);
        when(houseMapper.selectById(101L)).thenReturn(house);

        Map<String, Object> result = service.act(88L, "sign", LANDLORD, 2);

        assertEquals(ContractService.ST_EFFECTIVE, c.getStatus());
        assertNotNull(c.getSignedLandlordAt());
        assertEquals(66L, result.get("orderId"));

        ArgumentCaptor<LeaseOrder> orderCaptor = ArgumentCaptor.forClass(LeaseOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        LeaseOrder order = orderCaptor.getValue();
        assertEquals(88L, order.getContractId());
        assertEquals(TENANT, order.getTenantId());
        assertEquals(0, order.getStatus());
        assertEquals(new BigDecimal("2100"), order.getMonthlyRent());

        ArgumentCaptor<RentBill> billCaptor = ArgumentCaptor.forClass(RentBill.class);
        verify(billMapper, times(12)).insert(billCaptor.capture());
        List<RentBill> bills = billCaptor.getAllValues();
        for (int i = 0; i < 12; i++) {
            assertEquals(i + 1, bills.get(i).getPeriodNo());
            assertEquals(start.plusMonths(i), bills.get(i).getDueDate());
            assertEquals(new BigDecimal("2100"), bills.get(i).getAmount());
            assertEquals(0, bills.get(i).getStatus());
            assertEquals(66L, bills.get(i).getLeaseOrderId());
        }

        assertEquals(HouseService.ST_RENTED, house.getStatus());
        verify(houseMapper).updateById(house);
        verify(notification).send(eq(TENANT), eq(3), eq("合同已生效"), anyString(), eq("order"), eq(66L));
    }

    @Test
    @DisplayName("UT-CONTRACT-10 起止不足一个月仍生成 1 期账单（期数取下限 1）")
    void generatesSingleBillForShortTerm() {
        Contract c = contract(ContractService.ST_LANDLORD_CONFIRM,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20));
        when(contractMapper.selectById(88L)).thenReturn(c);
        when(orderMapper.insert(any(LeaseOrder.class))).thenAnswer(inv -> {
            ((LeaseOrder) inv.getArgument(0)).setId(66L);
            return 1;
        });

        service.act(88L, "sign", LANDLORD, 2);

        ArgumentCaptor<RentBill> billCaptor = ArgumentCaptor.forClass(RentBill.class);
        verify(billMapper, times(1)).insert(billCaptor.capture());
        assertEquals(LocalDate.of(2026, 11, 1), billCaptor.getValue().getDueDate());
    }

    @Test
    @DisplayName("UT-CONTRACT-11 乱序或重复签署被拒（3003）")
    void rejectsOutOfOrderSigning() {
        // 房东在待租客确认阶段抢先签署
        when(contractMapper.selectById(88L)).thenReturn(contract(ContractService.ST_TENANT_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(3003, () -> service.act(88L, "sign", LANDLORD, 2));

        // 租客在待房东确认阶段重复签署
        when(contractMapper.selectById(89L)).thenReturn(contract(ContractService.ST_LANDLORD_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(3003, () -> service.act(89L, "sign", TENANT, 1));

        // 已生效合同不可再签
        when(contractMapper.selectById(90L)).thenReturn(contract(ContractService.ST_EFFECTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(3003, () -> service.act(90L, "sign", TENANT, 1));
        assertCode(3003, () -> service.act(90L, "sign", LANDLORD, 2));
    }

    @Test
    @DisplayName("UT-CONTRACT-12 拒签：待签双方可作废，非双方与已生效阶段被拒")
    void rejectRules() {
        Contract c = contract(ContractService.ST_TENANT_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13));
        when(contractMapper.selectById(88L)).thenReturn(c);
        service.act(88L, "reject", LANDLORD, 2);
        assertEquals(ContractService.ST_VOIDED, c.getStatus());

        Contract c2 = contract(ContractService.ST_LANDLORD_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13));
        when(contractMapper.selectById(89L)).thenReturn(c2);
        service.act(89L, "reject", TENANT, 1);
        assertEquals(ContractService.ST_VOIDED, c2.getStatus());

        when(contractMapper.selectById(90L)).thenReturn(contract(ContractService.ST_EFFECTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(3003, () -> service.act(90L, "reject", TENANT, 1));

        when(contractMapper.selectById(91L)).thenReturn(contract(ContractService.ST_TENANT_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(1007, () -> service.act(91L, "reject", STRANGER, 1));
    }

    @Test
    @DisplayName("UT-CONTRACT-13 退租：已生效合同置已退租、订单关闭、房源重新上架")
    void terminateReleasesHouse() {
        Contract c = contract(ContractService.ST_EFFECTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13));
        LeaseOrder order = new LeaseOrder();
        order.setId(66L);
        order.setContractId(88L);
        order.setStatus(0);
        House house = house(HouseService.ST_RENTED);
        when(contractMapper.selectById(88L)).thenReturn(c);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(houseMapper.selectById(101L)).thenReturn(house);

        service.act(88L, "terminate", TENANT, 1);

        assertEquals(ContractService.ST_TERMINATED, c.getStatus());
        assertEquals(1, order.getStatus());
        assertEquals(HouseService.ST_ONLINE, house.getStatus());
        verify(notification).send(eq(LANDLORD), eq(3), eq("合同已退租"), anyString(), eq("contract"), eq(88L));

        // 待签/已作废合同不可退租
        when(contractMapper.selectById(89L)).thenReturn(contract(ContractService.ST_LANDLORD_CONFIRM,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(3003, () -> service.act(89L, "terminate", TENANT, 1));

        when(contractMapper.selectById(90L)).thenReturn(contract(ContractService.ST_EFFECTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));
        assertCode(1007, () -> service.act(90L, "terminate", STRANGER, 1));
    }

    @Test
    @DisplayName("UT-CONTRACT-14 未知动作返回 1000")
    void rejectsUnknownContractAction() {
        when(contractMapper.selectById(88L)).thenReturn(contract(ContractService.ST_EFFECTIVE,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(13)));

        assertCode(1000, () -> service.act(88L, "extend", TENANT, 1));
    }

    // ── 订单与账单 ──

    @Test
    @DisplayName("UT-CONTRACT-15 账单查询：仅订单双方或管理员可见，他人不可见（3004）")
    void billsCheckOwnership() {
        LeaseOrder order = new LeaseOrder();
        order.setId(66L);
        order.setTenantId(TENANT);
        order.setLandlordId(LANDLORD);
        when(orderMapper.selectById(66L)).thenReturn(order);
        when(billMapper.selectList(any())).thenReturn(List.of());
        // 控制器入口为 UserContext.userId()，角色上下文必然存在
        UserContext.set(new UserContext.User(STRANGER, 1));

        service.bills(66L, TENANT);
        service.bills(66L, LANDLORD);

        assertCode(3004, () -> service.bills(66L, STRANGER));

        // 管理员越权读放行（role=3）
        UserContext.set(new UserContext.User(1L, 3));
        service.bills(66L, STRANGER);

        when(orderMapper.selectById(999L)).thenReturn(null);
        assertCode(3004, () -> service.bills(999L, TENANT));
    }

    @Test
    @DisplayName("UT-CONTRACT-16 账单支付：仅订单租客可支付，非待支付状态拒绝")
    void billPaymentRules() {
        RentBill bill = new RentBill();
        bill.setId(31L);
        bill.setLeaseOrderId(66L);
        bill.setPeriodNo(1);
        bill.setAmount(new BigDecimal("2100"));
        bill.setStatus(0);
        LeaseOrder order = new LeaseOrder();
        order.setId(66L);
        order.setTenantId(TENANT);
        order.setLandlordId(LANDLORD);
        when(billMapper.selectById(31L)).thenReturn(bill);
        when(orderMapper.selectById(66L)).thenReturn(order);

        service.payBill(31L, TENANT);

        assertEquals(1, bill.getStatus());
        assertNotNull(bill.getPaidAt());
        verify(notification).send(eq(LANDLORD), eq(4), eq("租金已支付"), anyString(), eq("order"), eq(66L));

        // 房东不是承租人，不可支付
        assertCode(1007, () -> service.payBill(31L, LANDLORD));

        // 已支付账单重复支付
        bill.setStatus(1);
        BizException e = assertThrows(BizException.class, () -> service.payBill(31L, TENANT));
        assertEquals(1000, e.getCode());
        assertEquals("账单状态不允许支付", e.getMessage());

        // 账单不存在
        when(billMapper.selectById(999L)).thenReturn(null);
        assertCode(3004, () -> service.payBill(999L, TENANT));
    }

    @Test
    @DisplayName("UT-CONTRACT-17 订单列表按角色口径过滤：租客看自己的、房东看自己收到的")
    void ordersScopedByRole() {
        Page<LeaseOrder> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        doReturn(page).when(orderMapper).selectPage(any(), any());

        Page<Map<String, Object>> tenantOrders = service.orders(TENANT, 1, 1, 10);
        Page<Map<String, Object>> landlordOrders = service.orders(LANDLORD, 2, 1, 10);

        assertEquals(0, tenantOrders.getTotal());
        assertEquals(0, landlordOrders.getTotal());
        ArgumentCaptor<Wrapper<LeaseOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper, times(2)).selectPage(any(), captor.capture());
        assertTrue(captor.getAllValues().get(0).getSqlSegment().contains("tenant_id"));
        assertTrue(captor.getAllValues().get(1).getSqlSegment().contains("landlord_id"));
    }

    @Test
    @DisplayName("UT-CONTRACT-18 我的合同列表按签署双方过滤")
    void mineScopedToBothParties() {
        Page<Contract> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        doReturn(page).when(contractMapper).selectPage(any(), any());

        service.mine(TENANT, 1, 10);

        ArgumentCaptor<Wrapper<Contract>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(contractMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("landlord_id"));
    }

    @Test
    @DisplayName("UT-CONTRACT-19 合同详情：不存在 3004；仅合同双方与管理员可读，其余 1007")
    void detailChecksOwnership() {
        when(contractMapper.selectById(999L)).thenReturn(null);
        assertCode(3004, () -> service.detail(999L, TENANT, 1));

        Contract c = contract(ContractService.ST_EFFECTIVE, LocalDate.now(), LocalDate.now().plusMonths(12));
        when(contractMapper.selectById(88L)).thenReturn(c);

        assertEquals(c, service.detail(88L, TENANT, 1));
        assertEquals(c, service.detail(88L, LANDLORD, 2));
        assertEquals(c, service.detail(88L, STRANGER, 3));
        assertCode(1007, () -> service.detail(88L, STRANGER, 1));
    }

    @Test
    @DisplayName("UT-CONTRACT-20 生效时间写回：租客/房东签署时间戳各自独立")
    void signTimestampsWrittenIndependently() {
        LocalDate start = LocalDate.now().plusDays(1);
        Contract c = contract(ContractService.ST_TENANT_CONFIRM, start, start.plusMonths(12));
        when(contractMapper.selectById(88L)).thenReturn(c);
        service.act(88L, "sign", TENANT, 1);
        LocalDateTime tenantSigned = c.getSignedTenantAt();
        assertNotNull(tenantSigned);
        assertEquals(null, c.getSignedLandlordAt());

        when(contractMapper.selectById(88L)).thenReturn(c);
        service.act(88L, "sign", LANDLORD, 2);

        assertEquals(tenantSigned, c.getSignedTenantAt());
        assertNotNull(c.getSignedLandlordAt());
        assertTrue(!c.getSignedLandlordAt().isBefore(tenantSigned));
    }
}
