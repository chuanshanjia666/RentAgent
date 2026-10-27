package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.HouseDto;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.House;
import com.rentagent.security.UserContext;
import com.rentagent.service.HouseService;
import com.rentagent.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 房源发布/管理/审核状态操作 + 检索/地图/推荐/收藏（FR-05~11、FR-25） */
@Tag(name = "house", description = "房源与检索")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HouseController {

    private final HouseService houseService;
    private final SearchService searchService;

    @Operation(summary = "发布房源（房东，须已实名）")
    @PostMapping("/houses")
    public R<House> create(@Valid @RequestBody HouseDto.SaveReq req) {
        return R.ok(houseService.create(req, UserContext.userId()));
    }

    @Operation(summary = "编辑房源（重新进入待审核）")
    @PutMapping("/houses/{id}")
    public R<Void> update(@PathVariable long id, @Valid @RequestBody HouseDto.SaveReq req) {
        houseService.update(id, req, UserContext.userId());
        return R.ok();
    }

    @Operation(summary = "上架/下架（房东）")
    @PatchMapping("/houses/{id}/status")
    public R<Void> changeStatus(@PathVariable long id, @Valid @RequestBody HouseDto.StatusReq req) {
        houseService.changeStatus(id, req.action(), UserContext.userId());
        return R.ok();
    }

    @Operation(summary = "我的房源（房东）")
    @GetMapping("/landlord/houses")
    public R<PageVO<House>> myHouses(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(houseService.myHouses(UserContext.userId(), page, size)));
    }

    /**
     * 多条件搜索（公开，仅已上架）。
     * <p>
     * 返回裸实体页，与 /favorites、/landlord/houses、/recommendations 保持同一形状：
     * 早先这里返回 Item{house, images, landlordName, favorited, reviewCount}，
     * 而列表卡片只读实体字段，那 4 个附加字段等于每行白做 4 次查询（一页 12 行约 48 次），
     * 前端还得写 `it.house || it` 之类的形状嗅探。详情接口仍返回 Item。
     */
    @GetMapping("/houses")
    public R<PageVO<House>> search(HouseDto.SearchReq req) {
        return R.ok(PageVO.of(searchService.search(req)));
    }

    @Operation(summary = "地图找房（经纬度范围内房源点）")
    @GetMapping("/houses/map")
    public R<List<House>> map(HouseDto.SearchReq req) {
        return R.ok(searchService.mapHouses(req));
    }

    @Operation(summary = "房源详情")
    @GetMapping("/houses/{id}")
    public R<HouseDto.Item> detail(@PathVariable long id) {
        return R.ok(houseService.detail(id));
    }

    @Operation(summary = "个性化推荐（未登录返回热门）")
    @GetMapping("/recommendations")
    public R<List<House>> recommend(@RequestParam(defaultValue = "6") int limit) {
        UserContext.User user = UserContext.get();
        return R.ok(houseService.recommend(user == null ? null : user.getId(), limit));
    }
}
