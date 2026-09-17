package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.agent.LlmAgent;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AdminChatDto;
import com.rentagent.entity.AiChatMessage;
import com.rentagent.entity.AiChatSession;
import com.rentagent.entity.SysUser;
import com.rentagent.mapper.AiChatMessageMapper;
import com.rentagent.mapper.AiChatSessionMapper;
import com.rentagent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台 AI 对话审计（NFR-05 对话日志可追溯）：
 * 管理员可查看全站会话列表与单会话完整轨迹——归属用户（人物）、多轮对话、工具调用（名称/入参/返回/耗时）、
 * 引用来源与 token 用量，形态对齐主流 Agent 工具的 Trace 视图。
 * 默认只读，不提供修改/删除入口，保证日志只增不改（FR-23 留痕口径）。
 */
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private static final Map<Integer, String> SCENES = Map.of(1, "找房助手", 2, "智能客服", 3, "合同解读");
    private static final Map<Integer, String> ROLES = Map.of(1, "租客", 2, "房东", 3, "管理员");
    private static final Map<Integer, String> MSG_ROLES = Map.of(1, "用户", 2, "AI 助手", 3, "工具调用");
    /** 列表页最后一条消息的预览长度 */
    private static final int PREVIEW_CHARS = 60;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** 全站会话列表：可按场景/转人工/用户/关键词（标题、昵称、账号、手机号）过滤 */
    public Page<AdminChatDto.SessionVO> sessions(String keyword, Integer scene, Boolean transferred,
                                                 Long userId, long page, long size) {
        LambdaQueryWrapper<AiChatSession> w = new LambdaQueryWrapper<>();
        if (scene != null) {
            w.eq(AiChatSession::getScene, scene);
        }
        if (transferred != null) {
            w.eq(AiChatSession::getIsTransferred, transferred ? 1 : 0);
        }
        if (userId != null) {
            w.eq(AiChatSession::getUserId, userId);
        }
        if (keyword != null && !keyword.isBlank()) {
            List<Long> hitUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getNickname, keyword).or().like(SysUser::getUsername, keyword)
                            .or().like(SysUser::getPhone, keyword))
                    .stream().map(SysUser::getId).toList();
            if (hitUsers.isEmpty()) {
                w.like(AiChatSession::getTitle, keyword);
            } else {
                w.and(q -> q.like(AiChatSession::getTitle, keyword).or().in(AiChatSession::getUserId, hitUsers));
            }
        }
        w.orderByDesc(AiChatSession::getUpdatedAt);
        Page<AiChatSession> p = sessionMapper.selectPage(new Page<>(page, size), w);
        return toVoPage(p);
    }

    /** 单会话完整轨迹 */
    public AdminChatDto.DetailVO detail(long sessionId) {
        AiChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) {
            throw new BizException(ErrorCode.AI_SESSION_NOT_FOUND);
        }
        List<AiChatMessage> raw = messagesOf(sessionId);
        Map<Long, SysUser> users = usersOf(List.of(s.getUserId()));
        return new AdminChatDto.DetailVO(
                vo(s, users.get(s.getUserId()), aggregateOf(raw), lastPreview(raw)),
                chatService.engineName(), LlmAgent.SYSTEM_PROMPT,
                raw.stream().map(this::toMessage).toList(),
                stats(raw));
    }

    // ---------- 会话 → VO ----------

    private Page<AdminChatDto.SessionVO> toVoPage(Page<AiChatSession> p) {
        List<Long> sessionIds = p.getRecords().stream().map(AiChatSession::getId).toList();
        Map<Long, SessionAgg> agg = aggregate(sessionIds);
        Map<Long, SysUser> users = usersOf(p.getRecords().stream().map(AiChatSession::getUserId).toList());
        Page<AdminChatDto.SessionVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream()
                .map(s -> vo(s, users.get(s.getUserId()), agg.get(s.getId()),
                        agg.get(s.getId()) == null ? null : agg.get(s.getId()).lastMessage))
                .toList());
        return result;
    }

    private AdminChatDto.SessionVO vo(AiChatSession s, SysUser u, SessionAgg agg, String lastMessage) {
        int msgCount = agg == null ? 0 : agg.messageCount;
        return new AdminChatDto.SessionVO(s.getId(), s.getScene(), SCENES.getOrDefault(s.getScene(), "未知"),
                s.getTitle(), s.getUserId(),
                u == null ? "（用户已注销）" : u.getNickname(), u == null ? null : u.getUsername(),
                u == null ? null : u.getPhone(), u == null ? null : u.getRole(),
                s.getIsTransferred(), msgCount, agg == null ? 0 : agg.roundCount,
                agg == null ? 0 : agg.toolCallCount, agg == null ? 0 : agg.totalTokens,
                lastMessage, s.getCreatedAt(), s.getUpdatedAt());
    }

    private List<AiChatMessage> messagesOf(long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId).orderByAsc(AiChatMessage::getId));
    }

    private Map<Long, SysUser> usersOf(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    // ---------- 聚合（避免逐会话查询造成 N+1） ----------

    private record SessionAgg(int messageCount, int roundCount, int toolCallCount, int totalTokens,
                              String lastMessage) {
    }

    private Map<Long, SessionAgg> aggregate(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<AiChatMessage> w = new QueryWrapper<AiChatMessage>()
                .select("session_id AS sid", "COUNT(*) AS msg_count",
                        "SUM(CASE WHEN role = 1 THEN 1 ELSE 0 END) AS round_count",
                        "SUM(CASE WHEN role = 3 THEN 1 ELSE 0 END) AS tool_count",
                        "COALESCE(SUM(token_count), 0) AS tokens",
                        "MAX(id) AS last_id")
                .in("session_id", sessionIds)
                .groupBy("session_id");
        Map<Long, Long> lastIds = new HashMap<>();
        Map<Long, SessionAgg> agg = new HashMap<>();
        for (Map<String, Object> row : messageMapper.selectMaps(w)) {
            long sid = num(row.get("sid"));
            Long lastId = row.get("last_id") == null ? null : num(row.get("last_id"));
            if (lastId != null) {
                lastIds.put(sid, lastId);
            }
            agg.put(sid, new SessionAgg((int) num(row.get("msg_count")), (int) num(row.get("round_count")),
                    (int) num(row.get("tool_count")), (int) num(row.get("tokens")), null));
        }
        Map<Long, String> previews = lastPreviews(lastIds);
        agg.replaceAll((sid, a) -> new SessionAgg(a.messageCount, a.roundCount, a.toolCallCount, a.totalTokens,
                previews.get(sid)));
        return agg;
    }

    /** 批量取各会话最后一条消息，做列表预览 */
    private Map<Long, String> lastPreviews(Map<Long, Long> lastIds) {
        if (lastIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AiChatMessage> byId = messageMapper.selectBatchIds(lastIds.values()).stream()
                .collect(Collectors.toMap(AiChatMessage::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> out = new HashMap<>();
        lastIds.forEach((sid, mid) -> out.put(sid, preview(byId.get(mid))));
        return out;
    }

    private String preview(AiChatMessage m) {
        return m == null ? null : preview(List.of(m));
    }

    private String preview(List<AiChatMessage> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        AiChatMessage m = messages.get(messages.size() - 1);
        String text = m.getRole() == 3
                ? "🔧 " + m.getToolName()
                : (m.getContent() == null ? "" : m.getContent().replaceAll("\\s+", " ").trim());
        if (m.getRole() == 1) {
            text = "🙋 " + text;
        }
        return text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) + "…" : text;
    }

    private String lastPreview(List<AiChatMessage> messages) {
        return preview(messages);
    }

    // ---------- 消息 / 统计 ----------

    private AdminChatDto.MessageVO toMessage(AiChatMessage m) {
        return new AdminChatDto.MessageVO(m.getId(), m.getRole(),
                MSG_ROLES.getOrDefault(m.getRole(), "未知"), m.getContent(), m.getToolName(),
                parseJson(m.getToolArgs()), parseJson(m.getToolResult()), citationsOf(m),
                m.getTokenCount(), m.getLatencyMs(), m.getCreatedAt());
    }

    private AdminChatDto.StatsVO stats(List<AiChatMessage> messages) {
        int rounds = 0;
        int assistants = 0;
        int tokens = 0;
        int latencySum = 0;
        int latencyCount = 0;
        Map<String, int[]> byTool = new LinkedHashMap<>();
        for (AiChatMessage m : messages) {
            if (m.getRole() == 1) {
                rounds++;
            } else if (m.getRole() == 2) {
                assistants++;
                // 平均时延只统计助手回答（用户消息恒 0，工具耗时另在 tools 明细里看）
                if (m.getLatencyMs() != null && m.getLatencyMs() > 0) {
                    latencySum += m.getLatencyMs();
                    latencyCount++;
                }
            }
            if (m.getTokenCount() != null) {
                tokens += m.getTokenCount();
            }
            if (m.getRole() == 3 && m.getToolName() != null) {
                int[] acc = byTool.computeIfAbsent(m.getToolName(), k -> new int[3]);
                acc[0]++;
                acc[1] += m.getLatencyMs() == null ? 0 : m.getLatencyMs();
            }
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        byTool.forEach((name, acc) -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", name);
            t.put("count", acc[0]);
            t.put("avgLatencyMs", acc[0] == 0 ? 0 : acc[1] / acc[0]);
            tools.add(t);
        });
        return new AdminChatDto.StatsVO(rounds, assistants, tools.stream().mapToInt(t -> (int) t.get("count")).sum(),
                tokens, latencyCount == 0 ? 0 : latencySum / latencyCount, tools);
    }

    private SessionAgg aggregateOf(List<AiChatMessage> messages) {
        AdminChatDto.StatsVO st = stats(messages);
        return new SessionAgg(messages.size(), st.roundCount(), st.toolCallCount(), st.totalTokens(),
                preview(messages));
    }

    // ---------- JSON 字段解析 ----------

    /** 库内是 JSON 字符串，转成对象供前端结构化展示；解析失败原样返回字符串 */
    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> citationsOf(AiChatMessage m) {
        if (m.getCitations() == null || m.getCitations().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(m.getCitations(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    /** 供数据看板复用：AI 对话量与其时间趋势（FR-24） */
    public Map<String, Object> dashboardMetrics(String periodFormat) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("chatCount", sessionMapper.selectCount(null));
        vo.put("chatMessageCount", messageMapper.selectCount(null));
        vo.put("toolCallCount", messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getRole, 3)));
        vo.put("transferredCount", sessionMapper.selectCount(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getIsTransferred, 1)));
        vo.put("chatTrend", sessionsPerPeriod(periodFormat));
        return vo;
    }

    private List<Map<String, Object>> sessionsPerPeriod(String fmt) {
        QueryWrapper<AiChatSession> w = new QueryWrapper<AiChatSession>()
                .select("DATE_FORMAT(created_at, '" + fmt + "') AS period", "COUNT(*) AS cnt")
                .groupBy("period").orderByAsc("period").last("LIMIT 30");
        return sessionMapper.selectMaps(w);
    }
}
