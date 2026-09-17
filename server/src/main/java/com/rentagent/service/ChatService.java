package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.agent.AgentEngine;
import com.rentagent.agent.LlmAgent;
import com.rentagent.agent.MockAgent;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AiDto;
import com.rentagent.entity.AiChatMessage;
import com.rentagent.entity.AiChatSession;
import com.rentagent.mapper.AiChatMessageMapper;
import com.rentagent.mapper.AiChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 会话服务（FR-12/13）：SSE 流式对话编排，
 * 消息全量落库（NFR-05 可追溯），引擎按 Key 有无自动选择 LlmAgent（真模型）/ MockAgent（规则引擎）。
 */
@Slf4j
@Service
public class ChatService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final LlmAgent llmAgent;
    private final MockAgent mockAgent;
    private final ObjectMapper objectMapper;

    public ChatService(AiChatSessionMapper sessionMapper, AiChatMessageMapper messageMapper,
                       LlmAgent llmAgent, MockAgent mockAgent, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.llmAgent = llmAgent;
        this.mockAgent = mockAgent;
        this.objectMapper = objectMapper;
    }

    public String engineName() {
        return llmAgent.describe();
    }

    public AiChatSession createSession(long uid, int scene, String title) {
        AiChatSession s = new AiChatSession();
        s.setUserId(uid);
        s.setScene(scene);
        s.setTitle(title == null || title.isBlank() ? defaultTitle(scene) : title);
        s.setIsTransferred(0);
        sessionMapper.insert(s);
        return s;
    }

    public Page<AiChatSession> sessions(long uid, long page, long size) {
        return sessionMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, uid).orderByDesc(AiChatSession::getUpdatedAt));
    }

    public List<AiDto.MessageVO> history(long sessionId, long uid) {
        AiChatSession s = owned(sessionId, uid);
        List<AiDto.MessageVO> list = new ArrayList<>();
        for (AiChatMessage m : messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, s.getId()).orderByAsc(AiChatMessage::getId))) {
            list.add(new AiDto.MessageVO(m.getId(), m.getRole(), m.getContent(), citationsOf(m), ts(m)));
        }
        return list;
    }

    /** 发送消息：SSE 流式返回（delta* + done），异步回调中落库（NFR-02 首字流式） */
    public SseEmitter send(long sessionId, String content, long uid) {
        AiChatSession s = owned(sessionId, uid);
        saveMessage(s.getId(), 1, content, null, null, 0);

        SseEmitter emitter = new SseEmitter(120_000L);
        long start = System.currentTimeMillis();
        StringBuilder full = new StringBuilder();
        String[] citationsJson = {null};
        boolean[] transferred = {false};
        // token 用量：找房重试时模型会多次上报，累加计为本轮总消耗（NFR-05 留痕）
        int[] tokens = {0};

        engine().stream(s.getId(), uid, s.getScene(), content, new AgentEngine.Callback() {
            @Override
            public void onToken(String token) {
                full.append(token);
                try {
                    emitter.send(SseEmitter.event().name("delta")
                            .data(objectMapper.writeValueAsString(Map.of("delta", token))));
                } catch (Exception e) {
                    log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
                }
            }

            @Override
            public void onUsage(Integer totalTokens) {
                if (totalTokens != null) {
                    tokens[0] += totalTokens;
                }
            }

            @Override
            public void onComplete(String text, String citations, boolean isTransferred) {
                citationsJson[0] = citations;
                transferred[0] = isTransferred;
                long latency = System.currentTimeMillis() - start;
                AiChatMessage saved = saveMessage(s.getId(), 2, full.toString(), citations, null, (int) latency,
                        tokens[0] == 0 ? null : tokens[0]);
                if (isTransferred) {
                    s.setIsTransferred(1);
                }
                if ((s.getTitle() == null || s.getTitle().isBlank() || s.getTitle().startsWith("新会话"))
                        && content.length() >= 4) {
                    s.setTitle(content.substring(0, Math.min(20, content.length())));
                }
                sessionMapper.updateById(s);
                try {
                    emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(
                            Map.of("messageId", saved.getId(), "citations", citationsOf(saved),
                                    "transferred", isTransferred, "latencyMs", latency))));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("AI 引擎异常", t);
                String fallback = "智能助手暂时繁忙，请稍后再试；押金/退租等紧急问题可转人工客服。";
                try {
                    emitter.send(SseEmitter.event().name("delta").data(
                            objectMapper.writeValueAsString(Map.of("delta", fallback))));
                    emitter.send(SseEmitter.event().name("done").data(
                            objectMapper.writeValueAsString(Map.of("messageId", 0, "transferred", true))));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(t);
                }
            }
        });
        return emitter;
    }

    private AgentEngine engine() {
        return llmAgent.available() ? llmAgent : mockAgent;
    }

    private AiChatSession owned(long sessionId, long uid) {
        AiChatSession s = sessionMapper.selectById(sessionId);
        if (s == null || s.getUserId() != uid) {
            throw new BizException(ErrorCode.AI_SESSION_NOT_FOUND);
        }
        return s;
    }

    private AiChatMessage saveMessage(long sessionId, int role, String content, String citations,
                                      String toolName, int latencyMs) {
        return saveMessage(sessionId, role, content, citations, toolName, latencyMs, null);
    }

    private AiChatMessage saveMessage(long sessionId, int role, String content, String citations,
                                      String toolName, int latencyMs, Integer tokenCount) {
        AiChatMessage m = new AiChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setCitations(citations);
        m.setToolName(toolName);
        m.setLatencyMs(latencyMs);
        m.setTokenCount(tokenCount);
        messageMapper.insert(m);
        return m;
    }

    private List<Map<String, String>> citationsOf(AiChatMessage m) {
        try {
            if (m.getCitations() == null || m.getCitations().isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(m.getCitations(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, HashMap.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Long ts(AiChatMessage m) {
        return m.getCreatedAt() == null ? null : m.getCreatedAt()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String defaultTitle(int scene) {
        return switch (scene) {
            case 2 -> "智能客服咨询";
            case 3 -> "合同解读";
            default -> "新会话";
        };
    }
}
