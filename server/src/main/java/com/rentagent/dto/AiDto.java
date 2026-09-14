package com.rentagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** AI 智能体模块 DTO 集合（FR-08/12~16） */
public class AiDto {

    public record SessionCreateReq(@NotNull @Min(1) @Max(3) Integer scene, String title) {
    }

    public record MessageSendReq(@NotNull String content) {
    }

    public record SessionVO(Long id, Integer scene, String title, String updatedAt) {
    }

    public record MessageVO(Long id, Integer role, String content, List<Map<String, String>> citations, Long createdAt) {
    }

    /** SSE 事件载荷：delta 逐字 / done 收尾（含引用与消息 id） */
    public record StreamEvent(String type, String delta, Long messageId, List<Map<String, String>> citations,
            String suggestion) {
    }

    /** 定价建议（FR-15）：区间 + 依据 + 样本量 */
    public record PricingVO(BigDecimal low, BigDecimal high, BigDecimal avg, String basis, Integer sampleCount,
            String note, String model) {
    }

    /** 虚假房源检测（FR-16）：风险分 + 疑点列表 */
    public record DetectVO(Integer riskScore, List<String> suspicions, String suggestion, String model) {
    }

    /** 合同解读（FR-14）：逐条通俗化 + 风险标红 */
    public record InterpItem(Integer index, String clause, Boolean risk, String explanation) {
    }

    public record InterpVO(List<InterpItem> items, String disclaimer, String model) {
    }
}
