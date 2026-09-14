package com.rentagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rentagent.common.R;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.AuditLog;
import com.rentagent.entity.House;
import com.rentagent.entity.Report;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.security.RequireRole;
import com.rentagent.security.UserContext;
import com.rentagent.service.AdminService;
import com.rentagent.service.AnalysisService;
import com.rentagent.service.AuditLogService;
import com.rentagent.service.HouseService;
import com.rentagent.service.NotificationService;
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

import java.util.HashMap;
import java.util.Map;

/** 后台管理（FR-07/16/22/23/24，管理员） */
@Tag(name = "admin", description = "后台管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequireRole(3)
public class AdminController {

    private final AdminService adminService;
    private final AnalysisService analysisService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ReportService reportService;
    private final HouseService houseService;
    private final HouseMapper houseMapper;

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public R<PageVO<SysUser>> users(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageVO.of(adminService.users(keyword, page, size)));
    }

    @Operation(summary = "禁用/启用用户")
    @PatchMapping("/users/{id}/status")
    public R<Void> setUserStatus(@PathVariable long id, @RequestParam boolean enabled) {
        long adminId = UserContext.userId();
        adminService.setUserStatus(id, enabled, adminId);
        auditLogService.log(adminId, enabled ? "USER_ENABLE" : "USER_BAN", "user", id,
                Map.of("enabled", enabled), null);
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
    public R<Void> audit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        String reason = (String) body.get("reason");
        long adminId = UserContext.userId();
        houseService.audit(id, pass, reason, adminId);
        House house = houseMapper.selectById(id);
        if (!pass) {
            notificationService.send(house.getLandlordId(), 2, "房源审核未通过",
                    "「" + house.getTitle() + "」未通过审核：" + reason, "house", house.getId());
        } else {
            notificationService.send(house.getLandlordId(), 2, "房源审核通过",
                    "「" + house.getTitle() + "」已通过审核，可上架出租", "house", house.getId());
        }
        auditLogService.log(adminId, "HOUSE_AUDIT", "house", id, Map.of("pass", pass, "reason", reason == null ? "" : reason), null);
        return R.ok();
    }

    @Operation(summary = "AI 虚假房源检测（FR-16，辅助审核）")
    @PostMapping("/houses/{id}/fake-detect")
    public R<AiDto.DetectVO> fakeDetect(@PathVariable long id) {
        return R.ok(analysisService.fakeDetect(id, UserContext.userId()));
    }

    @Operation(summary = "举报列表")
    @GetMapping("/reports")
    public R<PageVO<Map<String, Object>>> reports(@RequestParam(defaultValue = "-1") int status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        Page<Report> p = reportService.page(status, page, size);
        Page<Map<String, Object>> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(reportService::toVO).toList());
        return R.ok(new PageVO<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @Operation(summary = "处理举报")
    @PatchMapping("/reports/{id}/handle")
    public R<Void> handleReport(@PathVariable long id, @RequestBody Map<String, String> body) {
        long adminId = UserContext.userId();
        reportService.handle(id, body.getOrDefault("remark", "已核实处理"), adminId);
        auditLogService.log(adminId, "REPORT_HANDLE", "report", id, new HashMap<>(body), null);
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
    public R<Map<String, Object>> dashboard(@RequestParam(defaultValue = "day") String granularity) {
        return R.ok(adminService.dashboard(granularity));
    }
}
