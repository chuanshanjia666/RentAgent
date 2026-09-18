package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.Review;
import com.rentagent.security.UserContext;
import com.rentagent.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/** 评价（FR-20） */
@Tag(name = "review", description = "评价体系")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价（仅完成合同可评，一单一评）")
    @PostMapping("/reviews")
    public R<Review> create(@Valid @RequestBody TradeDto.ReviewReq req) {
        return R.ok(reviewService.create(req.leaseOrderId(), req.houseScore(), req.landlordScore(),
                req.content(), UserContext.userId()));
    }

    @Operation(summary = "房源评价列表")
    @GetMapping("/houses/{id}/reviews")
    public R<PageVO<TradeDto.ReviewVO>> list(@PathVariable long id,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return R.ok(reviewService.listByHouse(id, page, size));
    }
}
