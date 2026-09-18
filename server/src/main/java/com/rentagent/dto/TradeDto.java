package com.rentagent.dto;

import com.rentagent.entity.Contract;
import com.rentagent.entity.LeaseOrder;
import com.rentagent.entity.ViewingAppointment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 交易模块 DTO 集合（FR-17~20） */
public class TradeDto {

    public record AppointmentCreateReq(@NotNull Long houseId, @NotNull LocalDateTime appointmentTime, String remark) {
    }

    public record AppointmentActionReq(@NotBlank String action, String reason) { // confirm/reject/cancel/complete
    }

    public record ContractCreateReq(@NotNull Long houseId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    public record ContractActionReq(@NotBlank String action) { // sign / reject / terminate（按当前用户角色判定语义）
    }

    public record BillPayReq(String remark) {
    }

    public record ReviewReq(@NotNull Long leaseOrderId, @NotNull @Min(1) @Max(5) Integer houseScore,
            @NotNull @Min(1) @Max(5) Integer landlordScore, String content) {
    }

    public record ReportReq(@NotNull Integer targetType, @NotNull Long targetId, @NotBlank String reason) {
    }

    // ── 列表响应 VO（用 record 而非 Map：OpenAPI 能出 schema，前端可据此对齐类型）──

    /** 预约列表行：预约实体 + 房源/租客摘要（一次取齐，前端不必逐行回查房源） */
    public record AppointmentVO(ViewingAppointment appointment, String houseTitle, String houseCover,
                                String tenantName, BigDecimal rent) {
    }

    /** 评价列表行：租客昵称已拼接为展示名（如"租客小陈"） */
    public record ReviewVO(Long id, Integer houseScore, Integer landlordScore, String content,
                           LocalDateTime createdAt, String tenantName) {
    }

    /**
     * 合同列表行：合同实体 + 房源标题。
     * 带上标题后前端无需再按 houseId 逐个请求 /houses/{id}——那条路径不仅有 N+1 次往返，
     * 还会因为详情接口的浏览计数副作用把房东自己的浏览量刷高。
     */
    public record ContractVO(Contract contract, String houseTitle) {
    }

    /** 合同操作结果：生效/退租时会派生出订单，orderId 仅在这两种情况下非空 */
    public record ContractActionResult(long contractId, Integer status, Long orderId) {
    }

    /** 订单列表行：订单 + 合同状态 + 房源标题 + 账单计数（前端"n/m 已付"直接用） */
    public record OrderVO(LeaseOrder order, Integer contractStatus, String houseTitle,
                          long billCount, long unpaidCount) {
    }
}
