package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.PageVO;
import com.rentagent.dto.TradeDto;
import com.rentagent.entity.Contract;
import com.rentagent.entity.RentBill;
import com.rentagent.security.UserContext;
import com.rentagent.service.AnalysisService;
import com.rentagent.service.ContractService;
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

import java.util.List;

/** 在线签约 / 订单与租金 / 合同解读（FR-14/18/19） */
@Tag(name = "trade", description = "签约与订单")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final AnalysisService analysisService;

    @Operation(summary = "发起签约（租客，生成电子合同）")
    @PostMapping("/contracts")
    public R<Contract> create(@Valid @RequestBody TradeDto.ContractCreateReq req) {
        return R.ok(contractService.create(req.houseId(), req.startDate(), req.endDate(), UserContext.userId()));
    }

    @Operation(summary = "合同操作：sign 签署 / reject 拒签 / terminate 退租（按角色判定）")
    @PatchMapping("/contracts/{id}")
    public R<TradeDto.ContractActionResult> act(@PathVariable long id, @Valid @RequestBody TradeDto.ContractActionReq req) {
        UserContext.User user = UserContext.get();
        return R.ok(contractService.act(id, req.action(), user.getId(), user.getRole()));
    }

    @Operation(summary = "合同详情（仅合同双方与管理员）")
    @GetMapping("/contracts/{id}")
    public R<Contract> detail(@PathVariable long id) {
        UserContext.User user = UserContext.get();
        return R.ok(contractService.detail(id, user.getId(), user.getRole()));
    }

    @Operation(summary = "我的合同（租客与房东）")
    @GetMapping("/contracts")
    public R<PageVO<TradeDto.ContractVO>> mine(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(contractService.mine(UserContext.userId(), page, size));
    }

    @Operation(summary = "合同智能解读（AI 逐条通俗化 + 风险标红）")
    @PostMapping("/contracts/{id}/interpret")
    public R<AiDto.InterpVO> interpret(@PathVariable long id) {
        return R.ok(analysisService.interpret(id, UserContext.userId()));
    }

    @Operation(summary = "我的订单")
    @GetMapping("/orders")
    public R<PageVO<TradeDto.OrderVO>> orders(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        UserContext.User user = UserContext.get();
        return R.ok(contractService.orders(user.getId(), user.getRole(), page, size));
    }

    @Operation(summary = "订单租金账单")
    @GetMapping("/orders/{id}/bills")
    public R<List<RentBill>> bills(@PathVariable long id) {
        return R.ok(contractService.bills(id, UserContext.userId()));
    }

    @Operation(summary = "标记账单已支付（演示：仅记录）")
    @PatchMapping("/bills/{id}/pay")
    public R<Void> payBill(@PathVariable long id) {
        contractService.payBill(id, UserContext.userId());
        return R.ok();
    }
}
