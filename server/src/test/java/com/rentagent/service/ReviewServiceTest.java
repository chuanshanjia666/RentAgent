package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.BizException;
import com.rentagent.entity.House;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.Review;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.LeaseOrderMapper;
import com.rentagent.mapper.ReviewMapper;
import com.rentagent.mapper.SysUserMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价模块单元测试（FR-20）：仅履行完成的订单可评、一单一评、房源均分回填。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-REVIEW-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    private static final long TENANT = 2L;
    private static final long LANDLORD = 7L;

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private LeaseOrderMapper orderMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private NotificationService notification;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        EntityMetadataHelper.init(House.class);
        service = new ReviewService(reviewMapper, orderMapper, houseMapper, userMapper, notification);
        when(houseMapper.update(any(), any())).thenReturn(1);
        when(reviewMapper.insert(any(Review.class))).thenAnswer(inv -> {
            ((Review) inv.getArgument(0)).setId(9L);
            return 1;
        });
    }

    private LeaseOrder order(int status, long tenantId) {
        LeaseOrder o = new LeaseOrder();
        o.setId(66L);
        o.setContractId(88L);
        o.setHouseId(101L);
        o.setTenantId(tenantId);
        o.setLandlordId(LANDLORD);
        o.setStatus(status);
        return o;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    @Test
    @DisplayName("UT-REVIEW-01 订单不存在或非本人订单不可评价（3005）")
    void rejectsNonOwnOrder() {
        when(orderMapper.selectById(66L)).thenReturn(null);
        assertCode(3005, () -> service.create(66L, 5, 5, "很好", TENANT));

        when(orderMapper.selectById(66L)).thenReturn(order(1, 3L));
        assertCode(3005, () -> service.create(66L, 5, 5, "很好", TENANT));

        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("UT-REVIEW-02 在租中（订单未完成）不可评价（3005）")
    void rejectsActiveLease() {
        when(orderMapper.selectById(66L)).thenReturn(order(0, TENANT));

        BizException e = assertThrows(BizException.class, () -> service.create(66L, 5, 5, "很好", TENANT));
        assertEquals(3005, e.getCode());
        assertEquals("合同履行完成后方可评价", e.getMessage());
        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("UT-REVIEW-03 同一订单重复评价被唯一键拦截（3005）")
    void rejectsDuplicateReview() {
        when(orderMapper.selectById(66L)).thenReturn(order(1, TENANT));
        when(reviewMapper.insert(any(Review.class))).thenThrow(new DuplicateKeyException("uk_order"));

        BizException e = assertThrows(BizException.class, () -> service.create(66L, 5, 5, "很好", TENANT));
        assertEquals(3005, e.getCode());
        assertEquals("该订单已评价过", e.getMessage());
    }

    @Test
    @DisplayName("UT-REVIEW-04 已退租订单可评价：字段取订单快照、状态正常、通知房东")
    void allowsReviewAfterTermination() {
        when(orderMapper.selectById(66L)).thenReturn(order(1, TENANT));
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        Review r = service.create(66L, 5, 4, "房东很好沟通", TENANT);

        assertEquals(66L, r.getLeaseOrderId());
        assertEquals(101L, r.getHouseId());
        assertEquals(LANDLORD, r.getLandlordId());
        assertEquals(TENANT, r.getTenantId());
        assertEquals(5, r.getHouseScore());
        assertEquals(4, r.getLandlordScore());
        assertEquals(0, r.getStatus());
        verify(notification).send(eq(LANDLORD), eq(9), eq("收到新评价"), anyString(), eq("review"), eq(9L));
    }

    @Test
    @DisplayName("UT-REVIEW-05 评价后房源均分按 HALF_UP 保留 1 位回填")
    void backfillsAvgScoreWithOneDecimal() {
        when(orderMapper.selectById(66L)).thenReturn(order(1, TENANT));
        Review old = new Review();
        old.setHouseScore(4);
        Review fresh = new Review();
        fresh.setHouseScore(5);
        when(reviewMapper.selectList(any())).thenReturn(List.of(old, fresh));

        service.create(66L, 5, 5, "不错", TENANT);

        ArgumentCaptor<Wrapper<House>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(houseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<House> wrapper = (LambdaUpdateWrapper<House>) captor.getValue();
        assertTrue(wrapper.getSqlSet().contains("avg_score"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(new BigDecimal("4.5")),
                "均分应为 4.5，实际参数=" + wrapper.getParamNameValuePairs().values());
    }

    @Test
    @DisplayName("UT-REVIEW-06 无有效评价时清理均分（写 null 而非 0）")
    void clearsAvgScoreWithoutReviews() {
        when(orderMapper.selectById(66L)).thenReturn(order(1, TENANT));
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        service.create(66L, 3, 3, "一般", TENANT);

        ArgumentCaptor<Wrapper<House>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(houseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<House> wrapper = (LambdaUpdateWrapper<House>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
    }

    @Test
    @DisplayName("UT-REVIEW-07 房源评价列表只展示正常状态评价并脱敏租客名")
    void assemblesReviewList() {
        Review r = new Review();
        r.setId(1L);
        r.setTenantId(TENANT);
        r.setHouseScore(5);
        r.setLandlordScore(5);
        r.setContent("很好");
        Page<Review> page = new Page<>(1, 10);
        page.setRecords(List.of(r));
        page.setTotal(1);
        doReturn(page).when(reviewMapper).selectPage(any(), any());
        SysUser tenant = new SysUser();
        tenant.setId(TENANT);
        tenant.setNickname("小陈");
        when(userMapper.selectById(anyLong())).thenReturn(tenant);

        var result = service.listByHouse(101L, 1, 10);

        assertEquals(1, result.getRecords().size());
        assertEquals("租客小陈", result.getRecords().get(0).get("tenantName"));
        assertNotNull(result.getRecords().get(0).get("houseScore"));
        assertEquals(5, result.getRecords().get(0).get("houseScore"));
    }
}
