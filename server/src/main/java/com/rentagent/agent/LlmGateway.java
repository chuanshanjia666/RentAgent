package com.rentagent.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.entity.AiProviderConfig;
import com.rentagent.mapper.AiProviderConfigMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 网关（风险 R1 应对）：多后端（协议）适配层。
 * 依据 ai 配置构建对应协议的模型客户端，端点 / API Key / 模型均可配置，三种协议对应业界三类接口：
 * <ul>
 *   <li>anthropic-messages —— Anthropic Messages API 原生协议（Claude 官方及兼容中转）；</li>
 *   <li>openai-chat-completions —— OpenAI Chat Completions 协议（最通用，厂商端点与聚合网关多兼容）；</li>
 *   <li>openai-responses —— OpenAI Responses API 新接口（本网关内置适配器实现）。</li>
 * </ul>
 * 三种协议均由本模块自研适配器实现（不依赖 langchain4j 的厂商模型类）：
 * 统一支持 Function Calling、多轮工具消息回填与流式"正文 + 工具调用"同轮出现的情形。
 * 兼容旧别名：openai → openai-chat-completions，anthropic → anthropic-messages。
 * <p>
 * 未配置有效模型时 available()=false，且**没有任何本地兜底**：AI 对话与四项 AI 分析能力
 * 一律返回 4001 并提示注入模型凭据（2026-09-17 口径：用真实模型，缺模型直接报错）。
 */
@Slf4j
@Component
public class LlmGateway {

    private final AiProps props;
    private final AiProviderConfigMapper configMapper;
    private volatile ChatLanguageModel chatModel;
    private volatile StreamingChatLanguageModel streamingModel;
    private volatile AiProps.Backend resolved;

    public LlmGateway(AiProps props) {
        this(props, null);
    }

    @Autowired
    public LlmGateway(AiProps props, @Autowired(required = false) AiProviderConfigMapper configMapper) {
        this.props = props;
        this.configMapper = configMapper;
    }

    @EventListener
    public synchronized void onConfigChanged(AiConfigChangedEvent event) {
        log.info("LLM 网关感知配置变更 (action={})，刷新当前生效后端", event.action());
        refresh();
    }

    /** 刷新网关缓存的后端客户端，使数据库新配置即时生效 */
    public synchronized void refresh() {
        this.resolved = null;
        this.chatModel = null;
        this.streamingModel = null;
    }

    public boolean available() {
        return backend() != null;
    }

    /** 当前生效的后端配置（优先数据库激活项，未激活则取配置文件/环境变量） */
    public synchronized AiProps.Backend resolvedBackend() {
        return backend();
    }

    /** 同步模型（结构化分析调用 AiJsonClient 使用） */
    public ChatLanguageModel chat() {
        backend();
        return chatModel;
    }

    /** 流式模型（AiServices 编排使用） */
    public StreamingChatLanguageModel streaming() {
        backend();
        return streamingModel;
    }

    /**
     * 结构化输出能力（response_format=json_schema）：仅 openai-chat-completions 协议提供，
     * 其它协议返回空，调用方退回提示词约束 + 解析容错。
     */
    public Optional<JsonSchemaChatModel> jsonSchema() {
        backend();
        return chatModel instanceof JsonSchemaChatModel model ? Optional.of(model) : Optional.empty();
    }

    /** 当前生效后端描述，如 "openai-chat-completions:deepseek/deepseek-v4.1-flash"；未启用为 "none" */
    public String describe() {
        AiProps.Backend b = backend();
        return b == null ? MODEL_NOT_CONFIGURED : normalizeProtocol(b.getProtocol()) + ":" + b.getModel();
    }

    /** 未配置模型时的引擎标识（仅用于展示/日志；AI 能力调用会直接报 4001） */
    public static final String MODEL_NOT_CONFIGURED = "none";

    private synchronized AiProps.Backend backend() {
        if (resolved == null) {
            AiProps.Backend b = loadFromDatabase();
            if (b == null) {
                b = props.activeBackend();
            }
            if (b != null) {
                build(b);
                resolved = b;
                log.info("LLM 网关启用后端: {} @ {} (model={})", b.getProtocol(), b.getBaseUrl(), b.getModel());
            }
        }
        return resolved;
    }

    private AiProps.Backend loadFromDatabase() {
        if (configMapper == null) {
            return null;
        }
        try {
            AiProviderConfig active = configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                    .eq(AiProviderConfig::getIsActive, 1)
                    .last("LIMIT 1"));
            if (active != null && active.getApiKey() != null && !active.getApiKey().isBlank()) {
                AiProps.Backend b = new AiProps.Backend();
                b.setProtocol(active.getProtocol());
                b.setBaseUrl(active.getBaseUrl());
                b.setApiKey(active.getApiKey());
                b.setModel(active.getModel());
                b.setTemperature(active.getTemperature() == null ? null : active.getTemperature().doubleValue());
                b.setMaxTokens(active.getMaxTokens());
                b.setTimeoutSeconds(active.getTimeoutSeconds());
                b.setUserAgent(active.getUserAgent());
                b.setCustomHeaders(active.getCustomHeaders());
                return props.withDefaults(b);
            }
        } catch (Exception e) {
            log.warn("从数据库读取 AI 供应商配置失败（可能表未就绪），回落到配置文件: {}", e.getMessage());
        }
        return null;
    }

    private void build(AiProps.Backend b) {
        Duration timeout = Duration.ofSeconds(b.getTimeoutSeconds());
        Map<String, String> headers = parseCustomHeaders(b);

        switch (normalizeProtocol(b.getProtocol())) {
            case "anthropic-messages" -> {
                AnthropicMessagesChatModel model = new AnthropicMessagesChatModel(
                        b.getBaseUrl(), b.getApiKey(), b.getModel(),
                        b.getTemperature(), b.getMaxTokens(), timeout, headers);
                chatModel = model;
                streamingModel = model;
            }
            case "openai-responses" -> {
                OpenAiResponsesChatModel model = new OpenAiResponsesChatModel(
                        b.getBaseUrl(), b.getApiKey(), b.getModel(),
                        b.getTemperature(), b.getMaxTokens(), timeout, headers);
                chatModel = model;
                streamingModel = model;
            }
            default -> {
                // openai-chat-completions：最通用的经典协议（OpenAI / DeepSeek / 通义 / 自建 / 聚合网关等均可）
                String base = orDefault(b.getBaseUrl(), "https://api.openai.com/v1");
                OpenAiChatCompletionsModel model = new OpenAiChatCompletionsModel(
                        base, b.getApiKey(), b.getModel(),
                        b.getTemperature(), b.getMaxTokens(), timeout, headers);
                chatModel = model;
                streamingModel = model;
            }
        }
    }

    /** 合并 User-Agent 与自定义 Header 字典 */
    public static Map<String, String> parseCustomHeaders(AiProps.Backend b) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (b.getHeaders() != null && !b.getHeaders().isEmpty()) {
            headers.putAll(b.getHeaders());
        }
        if (b.getCustomHeaders() != null && !b.getCustomHeaders().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(b.getCustomHeaders());
                if (node.isObject()) {
                    node.fields().forEachRemaining(entry -> {
                        if (entry.getValue() != null && !entry.getValue().isNull()) {
                            headers.put(entry.getKey(), entry.getValue().asText());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("解析自定义请求头 custom_headers 失败: {}", e.getMessage());
            }
        }
        if (b.getUserAgent() != null && !b.getUserAgent().isBlank()) {
            headers.put("User-Agent", b.getUserAgent());
        }
        // OpenCode GO 网关路由强制要求携带 x-opencode-session 请求头
        if (b.getBaseUrl() != null && b.getBaseUrl().contains("opencode.ai")) {
            if (!headers.containsKey("x-opencode-session")) {
                headers.put("x-opencode-session", "rentagent-session-default");
            }
        }
        return headers;
    }

    /** 协议名归一化：支持完整名与旧别名（openai / anthropic） */
    public static String normalizeProtocol(String protocol) {
        String p = protocol == null || protocol.isBlank() ? "openai-chat-completions" : protocol.trim().toLowerCase();
        return switch (p) {
            case "openai", "chat-completions" -> "openai-chat-completions";
            case "anthropic", "claude" -> "anthropic-messages";
            default -> p;
        };
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
