package com.rentagent.controller;

import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.common.R;
import com.rentagent.dto.AdminChatDto;
import com.rentagent.dto.AdminDto;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.AuditLog;
import com.rentagent.entity.House;
import com.rentagent.security.RequireRole;
import com.rentagent.security.UserContext;
import com.rentagent.service.AdminChatService;
import com.rentagent.service.AdminService;
import com.rentagent.service.AnalysisService;
import com.rentagent.service.HouseService;
import com.rentagent.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/** 后台管理（FR-07/16/22/23/24，管理员） */
@Tag(name = "admin", description = "后台管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequireRole(3)
public class AdminController {

    private final AdminService adminService;
    private final AdminChatService adminChatService;
    private final AnalysisService analysisService;
    private final ReportService reportService;
    private final HouseService houseService;

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public R<PageVO<AdminDto.UserVO>> users(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.map(adminService.users(keyword, page, size), AdminDto.UserVO::of));
    }

    @Operation(summary = "禁用/启用用户")
    @PatchMapping("/users/{id}/status")
    public R<Void> setUserStatus(@PathVariable long id, @RequestParam boolean enabled) {
        adminService.setUserStatus(id, enabled, UserContext.userId());
        return R.ok();
    }

    @Operation(summary = "待审核房源列表")
    @GetMapping("/houses/pending")
    public R<PageVO<House>> pending(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(adminService.pendingHouses(page, size)));
    }

    @Operation(summary = "房源审核：通过/驳回")
    @PatchMapping("/houses/{id}/audit")
    public R<Void> audit(@PathVariable long id, @Valid @RequestBody AdminDto.HouseAuditReq req) {
        if (!req.pass() && (req.reason() == null || req.reason().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "驳回必须填写理由");
        }
        houseService.audit(id, req.pass(), req.reason(), UserContext.userId());
        return R.ok();
    }

    @Operation(summary = "AI 虚假房源检测（FR-16，辅助审核）")
    @PostMapping("/houses/{id}/fake-detect")
    public R<AiDto.DetectVO> fakeDetect(@PathVariable long id) {
        return R.ok(analysisService.fakeDetect(id, UserContext.userId()));
    }

    @Operation(summary = "举报列表")
    @GetMapping("/reports")
    public R<PageVO<AdminDto.ReportVO>> reports(@RequestParam(defaultValue = "-1") int status,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.map(reportService.page(status, page, size), reportService::toVO));
    }

    @Operation(summary = "处理举报")
    @PatchMapping("/reports/{id}/handle")
    public R<Void> handleReport(@PathVariable long id, @RequestBody AdminDto.ReportHandleReq req) {
        reportService.handle(id, req.remark(), UserContext.userId());
        return R.ok();
    }

    @Operation(summary = "审计日志")
    @GetMapping("/audits")
    public R<PageVO<AuditLog>> audits(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(PageVO.of(adminService.auditLogs(page, size)));
    }

    @Operation(summary = "数据统计看板（日/周/月）")
    @GetMapping("/dashboard")
    public R<AdminDto.DashboardVO> dashboard(@RequestParam(defaultValue = "day") String granularity) {
        return R.ok(adminService.dashboard(granularity));
    }

    @Operation(summary = "AI 对话列表（全站会话，含归属用户与工具调用量，NFR-05）")
    @GetMapping("/chats")
    public R<PageVO<AdminChatDto.SessionVO>> chats(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer scene,
                                                   @RequestParam(required = false) Boolean transferred,
                                                   @RequestParam(required = false) Long userId,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(adminChatService.sessions(keyword, scene, transferred, userId, page, size)));
    }

    @Operation(summary = "AI 对话详情（多轮对话 + 工具调用轨迹 + 人物与角色设定，NFR-05）")
    @GetMapping("/chats/{id}")
    public R<AdminChatDto.DetailVO> chatDetail(@PathVariable long id) {
        return R.ok(adminChatService.detail(id));
    }
}
