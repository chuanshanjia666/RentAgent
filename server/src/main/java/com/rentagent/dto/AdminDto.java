package com.rentagent.dto;

import com.rentagent.entity.Report;
import com.rentagent.entity.SysUser;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 后台管理 DTO 集合（FR-07/22/23）。与 {@link AdminChatDto} 分开：这里是审核/处置类的写操作入参 */
public class AdminDto {

    /**
     * 房源审核：{@code pass} 必须显式给出。
     * <p>
     * 早先用 {@code Map} 收参并以 {@code Boolean.TRUE.equals(body.get("pass"))} 判定，
     * 请求体漏传该字段会被静默当成"驳回"并通知房东——一次字段缺失就误伤一条房源，
     * 故改为必填 + 参数校验，缺字段统一返回 1000。
     */
    public record HouseAuditReq(@NotNull(message = "缺少审核结论 pass") Boolean pass, String reason) {
    }

    /** 举报处理：remark 为空时沿用默认处理意见 */
    public record ReportHandleReq(String remark) {
    }

    /**
     * 实名认证审核：与房源审核同口径，{@code pass} 必填（缺字段返回 1000），
     * 驳回必须给出理由（控制器侧校验），避免漏传字段被当成"驳回"误伤用户。
     */
    public record RealnameAuditReq(@NotNull(message = "缺少审核结论 pass") Boolean pass, String reason) {
    }

    // ── 响应 VO（record 化：OpenAPI 有 schema，前端类型可对齐）──

    /** 举报列表行：举报实体 + 被举报对象的展示标题 */
    public record ReportVO(Report report, String targetTitle) {
    }

    /**
     * 实名认证审核列表行：{@code maskedIdCard} 是脱敏后的身份证号，
     * 明文只以 AES 密文落库（id_card_no_enc），接口任何时候都不回吐密文或明文。
     */
    public record RealnameRow(Long id, Long userId, String nickname, String phone, String realName,
                              String maskedIdCard, Integer status, String rejectReason,
                              LocalDateTime auditTime, LocalDateTime createdAt) {
    }

    /** 账号展示 VO：只暴露前端需要的字段，不把实体（含 password/deleted 等）整条吐出去 */
    public record UserVO(Long id, String username, String phone, String email, String nickname,
                         String avatarUrl, Integer role, Integer status, LocalDateTime createdAt) {
        public static UserVO of(SysUser u) {
            return new UserVO(u.getId(), u.getUsername(), u.getPhone(), u.getEmail(), u.getNickname(),
                    u.getAvatarUrl(), u.getRole(), u.getStatus(), u.getCreatedAt());
        }
    }

    /** FR-24 数据看板：统计口径固定，故用 record 明确字段而不是动态 Map */
    public record DashboardVO(long userCount, long landlordCount, long houseCount, long onlineCount,
                              long pendingCount, long rentedCount, long orderCount,
                              List<Map<String, Object>> userTrend, List<Map<String, Object>> houseTrend,
                              List<Map<String, Object>> orderTrend, long chatCount, long chatMessageCount,
                              long toolCallCount, long transferredCount,
                              List<Map<String, Object>> chatTrend) {
    }

    // ── AI 供应商动态配置（管理员界面可视化维护）──

    /** AI 供应商配置保存/更新请求 */
    public record AiConfigSaveReq(
            @jakarta.validation.constraints.NotBlank(message = "供应商名称不可为空") String name,
            String category, // "LLM" 或 "EMBEDDING"，缺省为 "LLM"
            @jakarta.validation.constraints.NotBlank(message = "通信协议不可为空") String protocol,
            @jakarta.validation.constraints.NotBlank(message = "接口基址不可为空") String baseUrl,
            String apiKey,
            @jakarta.validation.constraints.NotBlank(message = "模型名称不可为空") String model,
            String embeddingModel,
            Double temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String userAgent,
            String customHeaders,
            Boolean isActive
    ) {}

    /** AI 供应商配置展示行（API Key 脱敏） */
    public record AiConfigVO(
            Long id,
            String name,
            String category, // "LLM" 或 "EMBEDDING"
            String protocol,
            String baseUrl,
            String maskedApiKey,
            boolean hasApiKey,
            String model,
            String embeddingModel,
            Double temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String userAgent,
            String customHeaders,
            Integer isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** AI 配置列表与当前运行时状态概览 */
    public record AiConfigOverviewVO(
            String activeSource, // "DATABASE", "CONFIG_FILE", "NONE"
            String activeDescribe,
            Long activeConfigId,
            String activeEmbeddingSource, // "DATABASE", "CONFIG_FILE", "NONE"
            String activeEmbeddingDescribe,
            Long activeEmbeddingConfigId,
            List<AiConfigVO> configs
    ) {}

    /** AI 供应商连通性测试入参 */
    public record AiConfigTestReq(
            Long id,
            String category, // "LLM" 或 "EMBEDDING"
            String protocol,
            String baseUrl,
            String apiKey,
            String model,
            String embeddingModel,
            Double temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String userAgent,
            String customHeaders
    ) {}

    /** AI 供应商连通性测试结果 */
    public record AiConfigTestVO(
            boolean success,
            long latencyMs,
            String message
    ) {}
}

