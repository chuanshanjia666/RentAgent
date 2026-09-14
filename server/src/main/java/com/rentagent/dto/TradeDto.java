package com.rentagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
}
