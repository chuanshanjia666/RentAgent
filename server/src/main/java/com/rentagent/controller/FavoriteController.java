package com.rentagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.R;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.House;
import com.rentagent.security.UserContext;
import com.rentagent.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 房源收藏（FR-25） */
@Tag(name = "favorite", description = "房源收藏")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FavoriteController {

    private final SearchService searchService;

    @Operation(summary = "收藏房源")
    @PostMapping("/favorites/{houseId}")
    public R<Void> add(@PathVariable long houseId) {
        searchService.addFavorite(UserContext.userId(), houseId);
        return R.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{houseId}")
    public R<Void> remove(@PathVariable long houseId) {
        searchService.removeFavorite(UserContext.userId(), houseId);
        return R.ok();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorites")
    public R<PageVO<House>> list(@RequestParam(defaultValue = "1") long page,
                                 @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(searchService.favorites(UserContext.userId(), page, size)));
    }
}
