package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.ViewingAppointment;
import com.rentagent.security.UserContext;
import com.rentagent.service.AppointmentService;
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


/** 看房预约（FR-17） */
@Tag(name = "appointment", description = "看房预约")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "发起看房预约（租客）")
    @PostMapping("/appointments")
    public R<ViewingAppointment> create(@Valid @RequestBody TradeDto.AppointmentCreateReq req) {
        return R.ok(appointmentService.create(req.houseId(), req.appointmentTime(), req.remark(),
                UserContext.userId()));
    }

    @Operation(summary = "预约操作：confirm/reject（房东）、cancel（租客）、complete（房东）")
    @PatchMapping("/appointments/{id}")
    public R<Void> act(@PathVariable long id, @Valid @RequestBody TradeDto.AppointmentActionReq req) {
        UserContext.User user = UserContext.get();
        appointmentService.act(id, req.action(), req.reason(), user.getId(), user.getRole());
        return R.ok();
    }

    @Operation(summary = "我的预约（租客视角）")
    @GetMapping("/appointments/mine")
    public R<PageVO<TradeDto.AppointmentVO>> mine(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return R.ok(appointmentService.pageFor(UserContext.userId(), false, page, size));
    }

    @Operation(summary = "收到的预约（房东视角）")
    @GetMapping("/landlord/appointments")
    public R<PageVO<TradeDto.AppointmentVO>> received(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return R.ok(appointmentService.pageFor(UserContext.userId(), true, page, size));
    }
}
