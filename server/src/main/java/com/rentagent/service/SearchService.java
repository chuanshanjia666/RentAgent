package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.Favorite;
import com.rentagent.entity.House;
import com.rentagent.mapper.FavoriteMapper;
import com.rentagent.mapper.HouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/** 检索（FR-09/10）与收藏（FR-25） */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final HouseMapper houseMapper;
    private final FavoriteMapper favoriteMapper;
    private final HouseService houseService;

    /** 地图找房一次最多返回的点数：地图是"看一眼分布"的场景，不做分页但要有硬上限 */
    private static final int MAP_LIMIT = 300;

    /** FR-09：关键词 + 多条件组合筛选 + 排序，仅返回已上架房源 */
    public Page<House> search(HouseDto.SearchReq req) {
        long page = req.page() == null ? 1 : req.page();
        long size = Math.min(req.size() == null ? 10 : req.size(), 50);
        return houseMapper.selectPage(new Page<>(page, size), filter(req));
    }

    /** FR-10：地图找房（指定经纬度范围内的已上架房源，不分页） */
    public List<House> mapHouses(HouseDto.SearchReq req) {
        // 早先直接复用 search() 取第一页，默认 size=10 且上限 50：地图上永远只有零星几个点
        return houseMapper.selectList(filter(req).last("LIMIT " + MAP_LIMIT));
    }

    /** 搜索/地图共用的筛选与排序条件 */
    private LambdaQueryWrapper<House> filter(HouseDto.SearchReq req) {
        LambdaQueryWrapper<House> w = new LambdaQueryWrapper<House>()
                .eq(House::getStatus, HouseService.ST_ONLINE);
        if (notBlank(req.keyword())) {
            String kw = req.keyword().trim();
            w.and(q -> q.like(House::getTitle, kw).or().like(House::getCommunity, kw).or().like(House::getAddress, kw));
        }
        w.eq(notBlank(req.district()), House::getDistrict, req.district())
                // 户型前缀匹配："1室" 可命中 "1室1厅"/"1室0厅"（AI 解析出的户型关键词）
                .likeRight(notBlank(req.layout()), House::getLayout, req.layout())
                .eq(notBlank(req.orientation()), House::getOrientation, req.orientation())
                .ge(req.rentMin() != null, House::getRent, req.rentMin())
                .le(req.rentMax() != null, House::getRent, req.rentMax())
                .ge(req.lngMin() != null, House::getLng, req.lngMin())
                .le(req.lngMax() != null, House::getLng, req.lngMax())
                .ge(req.latMin() != null, House::getLat, req.latMin())
                .le(req.latMax() != null, House::getLat, req.latMax());
        if (req.facilities() != null) {
            for (String f : req.facilities()) {
                if (notBlank(f)) {
                    w.apply("JSON_CONTAINS(facilities, JSON_QUOTE({0}))", f);
                }
            }
        }
        switch (req.sort() == null ? "new" : req.sort()) {
            case "rent_asc" -> w.orderByAsc(House::getRent);
            case "rent_desc" -> w.orderByDesc(House::getRent);
            case "hot" -> w.orderByDesc(House::getViewCount);
            default -> w.orderByDesc(House::getId);
        }
        return w;
    }

    /** FR-25：收藏/取消收藏 */
    public void addFavorite(long uid, long houseId) {
        try {
            Favorite f = new Favorite();
            f.setUserId(uid);
            f.setHouseId(houseId);
            favoriteMapper.insert(f);
        } catch (DuplicateKeyException e) {
            // 已收藏则幂等处理
        }
    }

    public void removeFavorite(long uid, long houseId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getHouseId, houseId));
    }

    public Page<House> favorites(long uid, long page, long size) {
        Page<Favorite> favPage = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, uid).orderByDesc(Favorite::getId));
        List<Long> ids = favPage.getRecords().stream().map(Favorite::getHouseId).toList();
        Page<House> result = new Page<>(favPage.getCurrent(), favPage.getSize(), favPage.getTotal());
        result.setRecords(ids.isEmpty() ? List.of() : houseMapper.selectBatchIds(ids));
        return result;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
