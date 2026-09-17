package com.rentagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.entity.AiChatMessage;
import com.rentagent.mapper.AiChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用留痕（NFR-05 可追溯）：智能体每调用一次工具，就写入一条 role=3 的消息，
 * 后台"AI 对话审计"据此回放工具链（工具名 / 入参 / 返回 / 耗时）。
 * 留痕由 {@code HousingToolProvider} 在每次工具执行时自动完成；留痕失败只告警，不影响对话主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolTraceService {

    /** tool_result 落库上限：检索类工具一次可返回多张房源卡片，超出只留预览 */
    private static final int MAX_JSON_CHARS = 8000;

    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** 记录一次工具调用（入参/返回为 JSON 字符串，可为 null） */
    public void record(long sessionId, String toolName, String argsJson, String resultJson, int latencyMs) {
        try {
            AiChatMessage m = new AiChatMessage();
            m.setSessionId(sessionId);
            m.setRole(3);
            m.setContent("调用工具 " + toolName);
            m.setToolName(toolName);
            m.setToolArgs(normalize(argsJson));
            m.setToolResult(normalize(resultJson));
            m.setLatencyMs(latencyMs);
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("工具调用留痕失败（不影响对话）：tool={} session={} err={}", toolName, sessionId, e.getMessage());
        }
    }

    /**
     * tool_args / tool_result 是 JSON 列，落库前必须保证是合法 JSON：
     * 合法且不超长则原样保留；超长或非 JSON 则包一层（保留预览），避免 MySQL 拒绝或内容膨胀。
     */
    private String normalize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String s = json.trim();
        boolean valid = isJson(s);
        if (valid && s.length() <= MAX_JSON_CHARS) {
            return s;
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        if (valid) {
            wrap.put("truncated", true);
            wrap.put("chars", s.length());
            wrap.put("preview", s.substring(0, MAX_JSON_CHARS));
        } else {
            wrap.put("raw", s.length() > MAX_JSON_CHARS ? s.substring(0, MAX_JSON_CHARS) : s);
        }
        try {
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isJson(String s) {
        try {
            objectMapper.readTree(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
