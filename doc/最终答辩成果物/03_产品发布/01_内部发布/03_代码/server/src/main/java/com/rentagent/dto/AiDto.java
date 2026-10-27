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

    /**
     * 会话历史里的一条消息。
     * <p>
     * createdAt 用 epoch 毫秒：前端只做展示排序，不参与日期运算；其余接口的时间字段统一是
     * ISO 字符串，这里的差异仅因历史消息复用同一 VO 承载 SSE 与列表两条路径。
     */
    public record MessageVO(Long id, Integer role, String content, List<Map<String, Object>> citations, Long createdAt) {
    }

    /**
     * SSE 事件载荷（流式对话的真实协议）。
     * <p>
     * delta 事件：{@code {"delta":"<增量>"}}；done 事件：{@code {"messageId":…,"content":…,"citations":[…],
     * "transferred":…,"latencyMs":…}}；失败事件：{@code {"error":"<原因>"}}。
     * 早先这里有一个从未被引用的 StreamEvent 记录，字段还与实际载荷不一致（type/suggestion），
     * 会误导阅读者以为协议是"带 type 字段的判别联合"，故删除并以文字约定取代。
     */
    public static final String SSE_DELTA = "delta";
    public static final String SSE_DONE = "done";
    public static final String SSE_ERROR = "error";

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
