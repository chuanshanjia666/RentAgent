package com.rentagent.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台"AI 对话审计"DTO（NFR-05 对话日志可追溯；FR-24 AI 对话量）。
 * 与面向用户的 {@link AiDto} 分开：这里会暴露工具名/工具入参/工具返回与 token 用量，
 * 仅管理员可访问。
 */
public class AdminChatDto {

    /** 列表行：会话 + 归属用户（人物）+ 规模指标 */
    public record SessionVO(Long id, Integer scene, String sceneName, String title,
                            Long userId, String nickname, String username, String phone, Integer userRole,
                            Integer isTransferred, Integer messageCount, Integer roundCount,
                            Integer toolCallCount, Integer totalTokens,
                            String lastMessage, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * @param currentEngine 查看时刻生效的引擎（历史消息未逐条快照引擎，故不代表本会话当时的引擎）
     */
    public record DetailVO(SessionVO session, String currentEngine,
                           String systemPrompt, List<MessageVO> messages, StatsVO stats) {
    }

    /** 一条对话消息：role 1用户 2助手 3工具调用；工具消息带 toolName/toolArgs/toolResult */
    public record MessageVO(Long id, Integer role, String roleName, String content,
                            String toolName, Object toolArgs, Object toolResult,
                            List<Map<String, Object>> citations, Integer tokenCount, Integer latencyMs,
                            LocalDateTime createdAt) {
    }

    /** 会话规模统计：轮次 / 工具调用 / token / 平均时延 + 工具使用明细 */
    public record StatsVO(Integer roundCount, Integer assistantCount, Integer toolCallCount,
                          Integer totalTokens, Integer avgLatencyMs, List<Map<String, Object>> tools) {
    }
}
