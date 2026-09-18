package com.rentagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.BizException;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.Favorite;
import com.rentagent.entity.House;
import com.rentagent.entity.HouseImage;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.FavoriteMapper;
import com.rentagent.mapper.HouseImageMapper;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.ReviewMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房源模块单元测试（FR-05/06/07/11）：发布实名校验、审核状态机、上下架迁移、详情可见性与权限归属。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-HOUSE-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HouseServiceTest {

    @Mock
    private HouseMapper houseMapper;
    @Mock
    private HouseImageMapper imageMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private AuthService authService;

    private HouseService service;

    @BeforeEach
    void setUp() {
        service = new HouseService(houseMapper, imageMapper, userMapper, favoriteMapper,
                reviewMapper, authService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ── 测试替身构造 ──

    private House house(long id, long landlordId, int status) {
        House h = new House();
        h.setId(id);
        h.setLandlordId(landlordId);
        h.setTitle("软件园公寓 1 室 1 厅");
        h.setCommunity("软件园公寓");
        h.setCity("大连市");
        h.setDistrict("高新园区");
        h.setLayout("1室1厅");
        h.setArea(new BigDecimal("45"));
        h.setRent(new BigDecimal("2100"));
        h.setDepositType("押一付三");
        h.setStatus(status);
        h.setViewCount(0);
        return h;
    }

    private HouseDto.SaveReq req() {
        return new HouseDto.SaveReq("软件园公寓 1 室 1 厅", "软件园公寓", "大连市", "高新园区", "黄浦路 50 号",
                "1室1厅", new BigDecimal("45"), "南", "中层", new BigDecimal("2100"), "押一付三",
                List.of("近地铁", "精装修"), "近软件园地铁站，精装修家电齐全，适合上班族。",
                new BigDecimal("121.54"), new BigDecimal("38.85"), List.of("/uploads/a.jpg", "/uploads/b.jpg"));
    }

    private void assertCode(int expected, Executable action) {
        BizException e = assertThrows(BizException.class, () -> action.run());
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    private interface Executable {
        void run();
    }

    // ── FR-05 发布房源 ──

    @Test
    @DisplayName("UT-HOUSE-01 未实名房东发布房源被拒（1008）")
    void rejectsUnverifiedLandlord() {
        when(authService.realnamePassed(7L)).thenReturn(false);

        assertCode(1008, () -> service.create(req(), 7L));

        verify(houseMapper, never()).insert(any(House.class));
    }

    @Test
    @DisplayName("UT-HOUSE-02 实名房东发布成功，初始状态为待审核且浏览数为 0")
    void publishesAsPendingAfterRealname() {
        when(authService.realnamePassed(7L)).thenReturn(true);
        when(houseMapper.insert(any(House.class))).thenAnswer(inv -> {
            ((House) inv.getArgument(0)).setId(101L);
            return 1;
        });

        House created = service.create(req(), 7L);

        assertEquals(HouseService.ST_PENDING, created.getStatus());
        assertEquals(0, created.getViewCount());
        assertEquals(7L, created.getLandlordId());
        assertEquals(new BigDecimal("2100"), created.getRent());
        assertEquals("[\"近地铁\",\"精装修\"]", created.getFacilities());
        // 图片按顺序落库，首图为封面（列表卡片只读 cover_url）
        verify(imageMapper, times(2)).insert(any(HouseImage.class));
        assertEquals("/uploads/a.jpg", created.getCoverUrl());
    }

    @Test
    @DisplayName("UT-HOUSE-03 编辑房源后重新回到待审核、清空驳回原因并重写图片与封面")
    void editResetsToPending() {
        House existing = house(101L, 7L, HouseService.ST_REJECTED);
        existing.setRejectReason("图片不清晰");
        when(houseMapper.selectById(101L)).thenReturn(existing);

        service.update(101L, req(), 7L);

        assertEquals(HouseService.ST_PENDING, existing.getStatus());
        assertNull(existing.getRejectReason());
        verify(houseMapper).updateById(existing);
        // 旧图片整体替换，首图回写为封面
        verify(imageMapper).delete(any());
        verify(imageMapper, times(2)).insert(any(HouseImage.class));
    }

    @Test
    @DisplayName("UT-HOUSE-20 images 为 null 表示不改动图片：既不删旧图，也不清空封面")
    void keepsImagesWhenFieldAbsent() {
        House existing = house(101L, 7L, HouseService.ST_ONLINE);
        existing.setCoverUrl("/uploads/old.jpg");
        when(houseMapper.selectById(101L)).thenReturn(existing);

        HouseDto.SaveReq noImages = new HouseDto.SaveReq("软件园公寓 1 室 1 厅", "软件园公寓", "大连市",
                "高新园区", "黄浦路 50 号", "1室1厅", new BigDecimal("45"), "南", "中层",
                new BigDecimal("2100"), "押一付三", List.of("近地铁"), "描述", new BigDecimal("121.54"),
                new BigDecimal("38.85"), null);

        service.update(101L, noImages, 7L);

        verify(imageMapper, never()).delete(any());
        verify(imageMapper, never()).insert(any(HouseImage.class));
        assertEquals("/uploads/old.jpg", existing.getCoverUrl());
    }

    @Test
    @DisplayName("UT-HOUSE-04 非房主编辑他人房源被拒（1007）")
    void rejectsEditByNonOwner() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_ONLINE));

        assertCode(1007, () -> service.update(101L, req(), 8L));

        verify(houseMapper, never()).updateById(any(House.class));
    }

    // ── FR-06 上下架状态机 ──

    @Test
    @DisplayName("UT-HOUSE-05 已通过/已下架房源可上架")
    void allowsOnlineFromPassedOrOffline() {
        House passed = house(101L, 7L, HouseService.ST_PASSED);
        when(houseMapper.selectById(101L)).thenReturn(passed);
        service.changeStatus(101L, "online", 7L);
        assertEquals(HouseService.ST_ONLINE, passed.getStatus());

        House offline = house(102L, 7L, HouseService.ST_OFFLINE);
        when(houseMapper.selectById(102L)).thenReturn(offline);
        service.changeStatus(102L, "online", 7L);
        assertEquals(HouseService.ST_ONLINE, offline.getStatus());
    }

    @Test
    @DisplayName("UT-HOUSE-06 已上架房源可下架")
    void allowsOfflineFromOnline() {
        House online = house(101L, 7L, HouseService.ST_ONLINE);
        when(houseMapper.selectById(101L)).thenReturn(online);

        service.changeStatus(101L, "offline", 7L);

        assertEquals(HouseService.ST_OFFLINE, online.getStatus());
    }

    @Test
    @DisplayName("UT-HOUSE-07 非法上下架迁移一律拒绝（2002）")
    void rejectsIllegalStatusTransition() {
        // 待审核/已驳回/已出租 不允许直接上架
        for (int status : new int[]{HouseService.ST_PENDING, HouseService.ST_REJECTED, HouseService.ST_RENTED}) {
            when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, status));
            assertCode(2002, () -> service.changeStatus(101L, "online", 7L));
        }
        // 非上架状态不允许下架（含重复下架）
        for (int status : new int[]{HouseService.ST_PENDING, HouseService.ST_PASSED,
                HouseService.ST_RENTED, HouseService.ST_OFFLINE}) {
            when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, status));
            assertCode(2002, () -> service.changeStatus(101L, "offline", 7L));
        }
    }

    @Test
    @DisplayName("UT-HOUSE-08 未知动作参数被拒（1000）")
    void rejectsUnknownStatusAction() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_PASSED));

        assertCode(1000, () -> service.changeStatus(101L, "publish", 7L));
    }

    @Test
    @DisplayName("UT-HOUSE-09 非房主不可上下架他人房源（1007）")
    void rejectsStatusChangeByNonOwner() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_PASSED));

        assertCode(1007, () -> service.changeStatus(101L, "online", 8L));
    }

    @Test
    @DisplayName("UT-HOUSE-10 房源不存在返回 2001")
    void returns2001WhenHouseMissing() {
        when(houseMapper.selectById(999L)).thenReturn(null);

        assertCode(2001, () -> service.changeStatus(999L, "online", 7L));
        assertCode(2001, () -> service.update(999L, req(), 7L));
    }

    // ── FR-07 管理员审核 ──

    @Test
    @DisplayName("UT-HOUSE-11 待审核房源审核通过/驳回")
    void approvesOrRejectsHouse() {
        House pending = house(101L, 7L, HouseService.ST_PENDING);
        when(houseMapper.selectById(101L)).thenReturn(pending);

        service.audit(101L, true, null, 1L);
        assertEquals(HouseService.ST_PASSED, pending.getStatus());
        assertNull(pending.getRejectReason());

        House pending2 = house(102L, 7L, HouseService.ST_PENDING);
        when(houseMapper.selectById(102L)).thenReturn(pending2);
        service.audit(102L, false, "房源图片与描述不符", 1L);
        assertEquals(HouseService.ST_REJECTED, pending2.getStatus());
        assertEquals("房源图片与描述不符", pending2.getRejectReason());
    }

    @Test
    @DisplayName("UT-HOUSE-12 重复审核非待审核房源被拒（2002）")
    void rejectsDuplicateAudit() {
        for (int status : new int[]{HouseService.ST_PASSED, HouseService.ST_REJECTED,
                HouseService.ST_ONLINE, HouseService.ST_RENTED}) {
            when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, status));
            assertCode(2002, () -> service.audit(101L, true, null, 1L));
        }
    }

    @Test
    @DisplayName("UT-HOUSE-13 审核不存在的房源返回 2001")
    void rejectsAuditOfMissingHouse() {
        when(houseMapper.selectById(999L)).thenReturn(null);

        assertCode(2001, () -> service.audit(999L, true, null, 1L));
    }

    // ── FR-09/10 详情可见性 ──

    @Test
    @DisplayName("UT-HOUSE-14 匿名人只可见已上架/已出租房源，其余伪装为不存在（2001）")
    void hidesNonOnlineFromAnonymous() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_ONLINE));
        when(houseMapper.update(any(), any())).thenReturn(1);
        when(imageMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectById(7L)).thenReturn(null);

        HouseDto.Item online = service.detail(101L);
        assertEquals(HouseService.ST_ONLINE, online.house().getStatus());
        assertFalse(online.favorited());
        assertEquals(0L, online.reviewCount());

        when(houseMapper.selectById(102L)).thenReturn(house(102L, 7L, HouseService.ST_RENTED));
        service.detail(102L);

        for (int status : new int[]{HouseService.ST_PENDING, HouseService.ST_PASSED,
                HouseService.ST_REJECTED, HouseService.ST_OFFLINE}) {
            when(houseMapper.selectById(103L)).thenReturn(house(103L, 7L, status));
            assertCode(2001, () -> service.detail(103L));
        }
    }

    @Test
    @DisplayName("UT-HOUSE-15 房东与管理员可查看任意状态房源")
    void letsLandlordAndAdminSeeAnyStatus() {
        when(houseMapper.update(any(), any())).thenReturn(1);
        when(imageMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectById(7L)).thenReturn(null);

        UserContext.set(new UserContext.User(9L, 2));
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_PENDING));
        assertNotNull(service.detail(101L));

        UserContext.set(new UserContext.User(1L, 3));
        when(houseMapper.selectById(102L)).thenReturn(house(102L, 7L, HouseService.ST_REJECTED));
        assertNotNull(service.detail(102L));
    }

    @Test
    @DisplayName("UT-HOUSE-16 详情浏览计数自增，收藏状态与评价数按当前用户返回")
    void detailCountsViewAndFavorites() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, 7L, HouseService.ST_ONLINE));
        when(houseMapper.update(any(), any())).thenReturn(1);
        HouseImage img = new HouseImage();
        img.setId(5L);
        img.setHouseId(101L);
        img.setUrl("/uploads/a.jpg");
        img.setSort(0);
        img.setIsCover(1);
        when(imageMapper.selectList(any())).thenReturn(List.of(img));
        when(reviewMapper.selectCount(any())).thenReturn(3L);
        SysUser landlord = new SysUser();
        landlord.setId(7L);
        landlord.setNickname("李房东");
        when(userMapper.selectById(7L)).thenReturn(landlord);
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        UserContext.set(new UserContext.User(2L, 1));
        HouseDto.Item item = service.detail(101L);

        assertTrue(item.favorited());
        assertEquals("李房东", item.landlordName());
        assertEquals(1, item.images().size());
        assertTrue(item.images().get(0).isCover());
        assertEquals(3L, item.reviewCount());
        // 浏览计数走 SQL 自增，不读改写
        verify(houseMapper).update(any(), any());
    }

    @Test
    @DisplayName("UT-HOUSE-17 facilities JSON 序列化与容错解析")
    void serializesFacilitiesJson() {
        assertEquals("[\"近地铁\"]", service.toJson(List.of("近地铁")));
        assertNull(service.toJson(null));
        assertEquals(List.of("近地铁"), service.toList("[\"近地铁\"]"));
        assertEquals(List.of(), service.toList(null));
        assertEquals(List.of(), service.toList("{不是合法JSON"));
    }

    @Test
    @DisplayName("UT-HOUSE-18 推荐位只取已上架房源且匿名时按热度返回")
    void recommendsOnlyOnlineHouses() {
        when(houseMapper.selectList(any())).thenReturn(List.of(house(1L, 7L, HouseService.ST_ONLINE)));

        List<House> result = service.recommend(null, 6);

        assertEquals(1, result.size());
        assertEquals(HouseService.ST_ONLINE, result.get(0).getStatus());
        verify(favoriteMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("UT-HOUSE-19 登录用户推荐按收藏区域加权排序")
    void weightsRecommendationByPreference() {
        House preferred = house(1L, 7L, HouseService.ST_ONLINE);
        preferred.setDistrict("高新园区");
        preferred.setViewCount(0);
        House other = house(2L, 8L, HouseService.ST_ONLINE);
        other.setDistrict("中山区");
        other.setViewCount(100);
        when(houseMapper.selectList(any())).thenReturn(List.of(other, preferred));

        Favorite fav = new Favorite();
        fav.setUserId(2L);
        fav.setHouseId(1L);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(fav));
        when(houseMapper.selectBatchIds(any())).thenReturn(List.of(preferred));

        List<House> result = service.recommend(2L, 1);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }
}
