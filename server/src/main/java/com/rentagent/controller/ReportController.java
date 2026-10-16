package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.Report;
import com.rentagent.security.UserContext;
import com.rentagent.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 举报（FR-23 用户侧入口） */
@Tag(name = "report", description = "举报")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "提交举报（targetType 1房源 2评价 3用户）")
    @PostMapping
    public R<Report> create(@Valid @RequestBody TradeDto.ReportReq req) {
        return R.ok(reportService.create(UserContext.userId(), req.targetType(), req.targetId(), req.reason()));
    }
}
