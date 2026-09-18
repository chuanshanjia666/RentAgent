package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.common.JsonColumns;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在线签约（FR-18）与订单/租金（FR-19）：
 * 合同状态机 0待租客确认 → 1待房东确认 → 2已生效（派生订单与账单） / 4已作废；退租 2→3。
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    public static final int ST_TENANT_CONFIRM = 0;
    public static final int ST_LANDLORD_CONFIRM = 1;
    public static final int ST_EFFECTIVE = 2;
    public static final int ST_TERMINATED = 3;
    public static final int ST_VOIDED = 4;

    private final ContractMapper contractMapper;
    private final LeaseOrderMapper orderMapper;
    private final RentBillMapper billMapper;
    private final HouseMapper houseMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notification;
    private final JsonColumns jsonColumns;

    /** 生成电子合同：模板 + 双方信息自动填充（FR-18） */
    @Transactional
    public Contract create(long houseId, LocalDate start, LocalDate end, long tenantId) {
        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() != HouseService.ST_ONLINE) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        if (tenantId == house.getLandlordId()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "不能签约自己的房源");
        }
        if (!end.isAfter(start) || start.isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "租期不合法：起止日期需晚于今天且起止有序");
        }
        long active = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getHouseId, houseId).eq(Contract::getTenantId, tenantId)
                .in(Contract::getStatus, ST_TENANT_CONFIRM, ST_LANDLORD_CONFIRM, ST_EFFECTIVE));
        if (active > 0) {
            throw new BizException(ErrorCode.CONTRACT_STATUS_INVALID.getCode(), "已存在进行中的合同，请勿重复发起");
        }
        SysUser tenant = userMapper.selectById(tenantId);
        SysUser landlord = userMapper.selectById(house.getLandlordId());
        Contract c = new Contract();
        c.setHouseId(houseId);
        c.setTenantId(tenantId);
        c.setLandlordId(house.getLandlordId());
        c.setStartDate(start);
        c.setEndDate(end);
        c.setMonthlyRent(house.getRent());
        c.setDeposit(house.getRent());
        c.setStatus(ST_TENANT_CONFIRM);
        c.setClauses(buildClauses(house, tenant, landlord, start, end));
        contractMapper.insert(c);
        notification.send(c.getLandlordId(), 3, "新的签约请求",
                tenant.getNickname() + " 对「" + house.getTitle() + "」发起签约，请确认", "contract", c.getId());
        return c;
    }

    /** 双方签署：租客 sign 后进入待房东确认；房东 sign 后合同生效并派生订单 */
    @Transactional
    public TradeDto.ContractActionResult act(long id, String action, long uid, int role) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        boolean isTenant = c.getTenantId() == uid;
        boolean isLandlord = c.getLandlordId() == uid;
        Long orderId = null;
        switch (action) {
            case "sign" -> {
                if (isTenant && c.getStatus() == ST_TENANT_CONFIRM) {
                    c.setStatus(ST_LANDLORD_CONFIRM);
                    c.setSignedTenantAt(LocalDateTime.now());
                    notification.send(c.getLandlordId(), 3, "租客已确认合同",
                            "「" + houseTitle(c) + "」合同待您确认签署", "contract", c.getId());
                } else if (isLandlord && c.getStatus() == ST_LANDLORD_CONFIRM) {
                    c.setStatus(ST_EFFECTIVE);
                    c.setSignedLandlordAt(LocalDateTime.now());
                    orderId = createOrder(c).getId();
                } else {
                    throw new BizException(ErrorCode.CONTRACT_STATUS_INVALID);
                }
            }
            case "reject" -> {
                require(isTenant || isLandlord, ErrorCode.FORBIDDEN);
                if (c.getStatus() != ST_TENANT_CONFIRM && c.getStatus() != ST_LANDLORD_CONFIRM) {
                    throw new BizException(ErrorCode.CONTRACT_STATUS_INVALID);
                }
                c.setStatus(ST_VOIDED);
            }
            case "terminate" -> {
                require(isTenant || isLandlord, ErrorCode.FORBIDDEN);
                if (c.getStatus() != ST_EFFECTIVE) {
                    throw new BizException(ErrorCode.CONTRACT_STATUS_INVALID);
                }
                c.setStatus(ST_TERMINATED);
                LeaseOrder order = orderByContract(c.getId());
                order.setStatus(1);
                orderMapper.updateById(order);
                House house = houseMapper.selectById(c.getHouseId());
                house.setStatus(HouseService.ST_ONLINE);
                houseMapper.updateById(house);
                long other = isTenant ? c.getLandlordId() : c.getTenantId();
                notification.send(other, 3, "合同已退租", "「" + houseTitle(c) + "」合同已退租，房源重新上架", "contract", c.getId());
                orderId = order.getId();
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID);
        }
        contractMapper.updateById(c);
        return new TradeDto.ContractActionResult(c.getId(), c.getStatus(), orderId);
    }

    /** 生效订单 + 按月生成租金账单计划（FR-19） */
    private LeaseOrder createOrder(Contract c) {
        LeaseOrder order = new LeaseOrder();
        order.setContractId(c.getId());
        order.setHouseId(c.getHouseId());
        order.setTenantId(c.getTenantId());
        order.setLandlordId(c.getLandlordId());
        order.setStartDate(c.getStartDate());
        order.setEndDate(c.getEndDate());
        order.setMonthlyRent(c.getMonthlyRent());
        order.setDeposit(c.getDeposit());
        order.setStatus(0);
        orderMapper.insert(order);

        long months = Math.max(1, ChronoUnit.MONTHS.between(c.getStartDate(), c.getEndDate()));
        for (int i = 0; i < months; i++) {
            RentBill bill = new RentBill();
            bill.setLeaseOrderId(order.getId());
            bill.setPeriodNo(i + 1);
            bill.setDueDate(c.getStartDate().plusMonths(i));
            bill.setAmount(c.getMonthlyRent());
            bill.setStatus(0);
            billMapper.insert(bill);
        }
        House house = houseMapper.selectById(c.getHouseId());
        house.setStatus(HouseService.ST_RENTED);
        houseMapper.updateById(house);
        notification.send(c.getTenantId(), 3, "合同已生效",
                "「" + houseTitle(c) + "」签约完成，共生成 " + months + " 期租金账单", "order", order.getId());
        return order;
    }

    /**
     * 合同详情：仅合同双方与管理员可见。开放给任意登录用户会形成越权读取
     * （合同正文含双方姓名、房屋地址与租金，遍历 id 即可批量拉取）。
     */
    public Contract detail(long id, long uid, int role) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (c.getTenantId() != uid && c.getLandlordId() != uid && role != 3) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return c;
    }

    /**
     * 我的合同。列表直接带房源标题：前端由此不再需要按 houseId 逐个请求 /houses/{id}
     * （那既有 N+1 次往返，也会因详情接口的浏览计数副作用把房东自己的浏览量刷高）。
     */
    public PageVO<TradeDto.ContractVO> mine(long uid, long page, long size) {
        Page<Contract> p = contractMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Contract>()
                .eq(Contract::getTenantId, uid).or().eq(Contract::getLandlordId, uid)
                .orderByDesc(Contract::getId));
        return PageVO.map(p, c -> new TradeDto.ContractVO(c, houseTitle(c)));
    }

    public LeaseOrder orderByContract(long contractId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<LeaseOrder>()
                .eq(LeaseOrder::getContractId, contractId));
    }

    public PageVO<TradeDto.OrderVO> orders(long uid, int role, long page, long size) {
        LambdaQueryWrapper<LeaseOrder> w = new LambdaQueryWrapper<LeaseOrder>();
        if (role == 1) {
            w.eq(LeaseOrder::getTenantId, uid);
        } else if (role == 2) {
            w.eq(LeaseOrder::getLandlordId, uid);
        }
        w.orderByDesc(LeaseOrder::getId);
        Page<LeaseOrder> p = orderMapper.selectPage(new Page<>(page, size), w);
        return PageVO.map(p, o -> {
            Contract c = contractMapper.selectById(o.getContractId());
            return new TradeDto.OrderVO(o, c == null ? null : c.getStatus(), houseTitleById(o.getHouseId()),
                    billMapper.selectCount(new LambdaQueryWrapper<RentBill>()
                            .eq(RentBill::getLeaseOrderId, o.getId())),
                    billMapper.selectCount(new LambdaQueryWrapper<RentBill>()
                            .eq(RentBill::getLeaseOrderId, o.getId()).eq(RentBill::getStatus, 0)));
        });
    }

    public List<RentBill> bills(long orderId, long uid) {
        LeaseOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getTenantId() != uid && order.getLandlordId() != uid && !isAdmin())) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return billMapper.selectList(new LambdaQueryWrapper<RentBill>()
                .eq(RentBill::getLeaseOrderId, orderId).orderByAsc(RentBill::getPeriodNo));
    }

    /** 标记账单支付（仅记录，FR-19 范围不含真实支付渠道） */
    public void payBill(long billId, long uid) {
        RentBill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        LeaseOrder order = orderMapper.selectById(bill.getLeaseOrderId());
        if (order == null || order.getTenantId() != uid) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (bill.getStatus() != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "账单状态不允许支付");
        }
        bill.setStatus(1);
        bill.setPaidAt(LocalDateTime.now());
        billMapper.updateById(bill);
        notification.send(order.getLandlordId(), 4, "租金已支付",
                "第 " + bill.getPeriodNo() + " 期租金 ¥" + bill.getAmount() + " 已确认支付", "order", order.getId());
    }

    /** 当前登录用户是否为管理员（未登录时为 false） */
    private boolean isAdmin() {
        return Integer.valueOf(3).equals(UserContext.role());
    }

    private String houseTitle(Contract c) {
        return houseTitleById(c.getHouseId());
    }

    private String houseTitleById(Long houseId) {
        House h = houseMapper.selectById(houseId);
        return h == null ? "房源" : h.getTitle();
    }

    private void require(boolean ok, ErrorCode code) {
        if (!ok) {
            throw new BizException(code);
        }
    }

    /** 合同模板渲染：条款集合 JSON（演示模板，条款含风险示例由 AI 解读标红） */
    private String buildClauses(House house, SysUser tenant, SysUser landlord, LocalDate start, LocalDate end) {
        List<Map<String, String>> clauses = new ArrayList<>();
        clauses.add(Map.of("title", "租赁标的", "text", "甲方（房东）" + landlord.getNickname() + "将位于" +
                house.getCity() + house.getDistrict() + house.getCommunity() + "的房屋（" + house.getLayout() +
                "，" + house.getArea() + "㎡）出租给乙方（租客）" + tenant.getNickname() + "居住使用。"));
        clauses.add(Map.of("title", "租期", "text", "租赁期自 " + start + " 至 " + end + "，共 " +
                ChronoUnit.MONTHS.between(start, end) + " 个月。"));
        clauses.add(Map.of("title", "租金与押金", "text", "月租金人民币 " + house.getRent() + " 元，押付方式：" +
                house.getDepositType() + "，押金人民币 " + house.getRent() + " 元，合同期满无违约无损坏全额退还。"));
        clauses.add(Map.of("title", "费用承担", "text", "租赁期内水费、电费、燃气费、物业费、网络费由乙方承担；房屋主体结构自然损坏的维修由甲方承担。"));
        clauses.add(Map.of("title", "维修责任", "text", "房屋及附属设施非因乙方原因损坏的，甲方应在接到通知后 7 日内维修。"));
        clauses.add(Map.of("title", "违约责任", "text", "任何一方提前解约的，应提前 30 日书面通知对方；乙方提前退租的押金不予退还，甲方提前收房的应向乙方支付相当于两个月租金的违约金。"));
        clauses.add(Map.of("title", "合同解除", "text", "租赁期满乙方享有优先续租权；单方涨租或单方收房视为甲方违约。"));
        return jsonColumns.write(clauses);
    }
}
