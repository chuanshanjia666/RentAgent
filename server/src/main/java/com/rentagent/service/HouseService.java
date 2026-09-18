package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.Favorite;
import com.rentagent.entity.House;
import com.rentagent.entity.HouseImage;
import com.rentagent.entity.Review;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.FavoriteMapper;
import com.rentagent.mapper.HouseImageMapper;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.mapper.ReviewMapper;
import com.rentagent.mapper.SysUserMapper;
import com.rentagent.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 房源管理（FR-05/06/07）与检索（FR-09/10/11） */
@Service
@RequiredArgsConstructor
public class HouseService {

    public static final int ST_PENDING = 0;
    public static final int ST_PASSED = 1;
    public static final int ST_REJECTED = 2;
    public static final int ST_ONLINE = 3;
    public static final int ST_OFFLINE = 4;
    public static final int ST_RENTED = 5;

    private final HouseMapper houseMapper;
    private final HouseImageMapper imageMapper;
    private final SysUserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /** FR-05：发布房源，须已实名，初始状态待审核 */
    @Transactional
    public House create(HouseDto.SaveReq req, long landlordId) {
        if (!authService.realnamePassed(landlordId)) {
            throw new BizException(ErrorCode.REALNAME_REQUIRED);
        }
        House house = new House();
        copy(req, house);
        house.setLandlordId(landlordId);
        house.setStatus(ST_PENDING);
        house.setViewCount(0);
        houseMapper.insert(house);
        house.setCoverUrl(saveImages(house.getId(), req.images()));
        return house;
    }

    /**
     * FR-06：编辑后重新进入待审核。
     * <p>
     * {@code images} 为 null 表示"本次不改动图片"：列表页等场景提交的请求不带该字段，
     * 若无条件按它重写，已有照片会被整个删光。传空数组才是显式清空。
     */
    @Transactional
    public void update(long id, HouseDto.SaveReq req, long uid) {
        House house = owned(id, uid);
        copy(req, house);
        house.setStatus(ST_PENDING);
        house.setRejectReason(null);
        houseMapper.updateById(house);
        if (req.images() != null) {
            imageMapper.delete(new LambdaQueryWrapper<HouseImage>().eq(HouseImage::getHouseId, id));
            saveImages(id, req.images());
        }
    }

    /** FR-06：上架/下架 */
    public void changeStatus(long id, String action, long uid) {
        House house = owned(id, uid);
        int target = switch (action) {
            case "online" -> {
                if (house.getStatus() != ST_PASSED && house.getStatus() != ST_OFFLINE) {
                    throw new BizException(ErrorCode.HOUSE_STATUS_INVALID);
                }
                yield ST_ONLINE;
            }
            case "offline" -> {
                if (house.getStatus() != ST_ONLINE) {
                    throw new BizException(ErrorCode.HOUSE_STATUS_INVALID);
                }
                yield ST_OFFLINE;
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID);
        };
        house.setStatus(target);
        houseMapper.updateById(house);
    }

    /** FR-07：管理员审核（通过/驳回），留痕与通知在控制器层完成 */
    @Transactional
    public void audit(long id, boolean pass, String reason, long adminId) {
        House house = houseMapper.selectById(id);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        if (house.getStatus() != ST_PENDING) {
            throw new BizException(ErrorCode.HOUSE_STATUS_INVALID);
        }
        house.setStatus(pass ? ST_PASSED : ST_REJECTED);
        house.setRejectReason(pass ? null : reason);
        houseMapper.updateById(house);
    }

    /** 房源详情：租客侧仅可见已上架；浏览计数（FR-11 推荐热度因子） */
    public HouseDto.Item detail(long id) {
        House house = houseMapper.selectById(id);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        UserContext.User user = UserContext.get();
        if (user == null || user.getRole() != 3 && user.getRole() != 2) {
            if (house.getStatus() != ST_ONLINE && house.getStatus() != ST_RENTED) {
                throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
            }
        }
        houseMapper.update(null, new LambdaUpdateWrapper<House>()
                .eq(House::getId, id).setSql("view_count = view_count + 1"));
        return toItem(house, user);
    }

    public Page<House> myHouses(long uid, long page, long size) {
        return houseMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<House>()
                .eq(House::getLandlordId, uid).orderByDesc(House::getId));
    }

    private House owned(long id, long uid) {
        House house = houseMapper.selectById(id);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        if (house.getLandlordId() != uid) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return house;
    }

    private void copy(HouseDto.SaveReq req, House house) {
        house.setTitle(req.title());
        house.setCommunity(req.community());
        house.setCity(req.city());
        house.setDistrict(req.district());
        house.setAddress(req.address());
        house.setLayout(req.layout());
        house.setArea(req.area());
        house.setOrientation(req.orientation());
        house.setFloorDesc(req.floorDesc());
        house.setRent(req.rent());
        house.setDepositType(req.depositType());
        house.setFacilities(toJson(req.facilities()));
        house.setDescription(req.description());
        house.setLng(req.lng());
        house.setLat(req.lat());
    }

    /**
     * 落库房源图片，并把首图同步为 {@code house.cover_url}，返回写入的封面 URL（无图时为 null）。
     * 列表卡片（{@code HouseCard}、预约 VO）只读 {@code house.cover_url}，
     * 不同步的话即便图片齐全也一律显示占位图。用 {@code set} 显式赋值，清空图片时封面才会真的置空。
     */
    private String saveImages(long houseId, List<String> urls) {
        if (urls == null) {
            return null;
        }
        int sort = 0;
        for (String url : urls) {
            HouseImage img = new HouseImage();
            img.setHouseId(houseId);
            img.setUrl(url);
            img.setSort(sort++);
            img.setIsCover(sort == 1 ? 1 : 0);
            imageMapper.insert(img);
        }
        String cover = urls.isEmpty() ? null : urls.get(0);
        houseMapper.update(null, new LambdaUpdateWrapper<House>()
                .eq(House::getId, houseId)
                .set(House::getCoverUrl, cover));
        return cover;
    }

    public HouseDto.Item toItem(House house, UserContext.User viewer) {
        SysUser landlord = userMapper.selectById(house.getLandlordId());
        List<HouseDto.Item.Image> images = imageMapper.selectList(new LambdaQueryWrapper<HouseImage>()
                        .eq(HouseImage::getHouseId, house.getId()).orderByAsc(HouseImage::getSort)).stream()
                .map(i -> new HouseDto.Item.Image(i.getId(), i.getUrl(), i.getSort(), i.getIsCover() == 1))
                .toList();
        boolean favorited = viewer != null && favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, viewer.getId()).eq(Favorite::getHouseId, house.getId())) > 0;
        long reviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getHouseId, house.getId())
                .eq(Review::getStatus, 0));
        return new HouseDto.Item(house, images, landlord == null ? null : landlord.getNickname(),
                favorited, reviewCount);
    }

    public String toJson(List<String> list) {
        try {
            return list == null ? null : objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> toList(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** FR-11 个性化推荐：同区域/同户型偏好加权 + 热度兜底（返回已上架房源） */
    public List<House> recommend(Long uid, int limit) {
        LambdaQueryWrapper<House> base = new LambdaQueryWrapper<House>().eq(House::getStatus, ST_ONLINE);
        List<House> hot = houseMapper.selectList(base.orderByDesc(House::getViewCount).last("LIMIT " + (limit * 3)));
        if (uid == null) {
            return hot.stream().limit(limit).toList();
        }
        // 用户偏好：收藏 + 已预约房源的 district/layout 分布
        Map<String, Long> districtWeight = weightOf(uid, limit);
        if (districtWeight.isEmpty()) {
            return hot.stream().limit(limit).toList();
        }
        return hot.stream()
                .sorted((a, b) -> Double.compare(score(b, districtWeight), score(a, districtWeight)))
                .limit(limit)
                .toList();
    }

    private Map<String, Long> weightOf(long uid, int limit) {
        List<Long> houseIds = new ArrayList<>();
        favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, uid))
                .forEach(f -> houseIds.add(f.getHouseId()));
        if (houseIds.isEmpty()) {
            return Map.of();
        }
        return houseMapper.selectBatchIds(houseIds).stream()
                .collect(Collectors.groupingBy(House::getDistrict, Collectors.counting()));
    }

    private double score(House h, Map<String, Long> weight) {
        long w = weight.getOrDefault(h.getDistrict(), 0L) * 10;
        double hot = h.getViewCount() == null ? 0 : Math.log1p(h.getViewCount());
        double rate = h.getAvgScore() == null ? 0 : h.getAvgScore().doubleValue();
        return w + hot + rate;
    }
}
