package com.rentagent.agent;

import com.rentagent.config.AiProps;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * LLM 网关（风险 R1 应对）：多后端（协议）适配层。
 * 依据 ai 配置构建对应协议的模型客户端，端点 / API Key / 模型均可配置，三种协议对应业界三类接口：
 * <ul>
 *   <li>anthropic-messages —— Anthropic Messages API 原生协议（Claude 官方及兼容中转）；</li>
 *   <li>openai-chat-completions —— OpenAI Chat Completions 协议（GLM / DeepSeek / 通义等均兼容）；</li>
 *   <li>openai-responses —— OpenAI Responses API 新接口（本网关内置适配器实现）。</li>
 * </ul>
 * 兼容旧别名：openai → openai-chat-completions，anthropic → anthropic-messages。
 * 未配置有效 Key 时 available()=false，上层智能体自动降级为规则引擎（MockAgent）。
 */
@Slf4j
@Component
public class LlmGateway {

    private final AiProps props;
    private volatile ChatLanguageModel chatModel;
    private volatile StreamingChatLanguageModel streamingModel;
    private volatile AiProps.Backend resolved;

    public LlmGateway(AiProps props) {
        this.props = props;
    }

    public boolean available() {
        return backend() != null;
    }

    /** 同步模型（AiServices 编排 / chatOnce 使用） */
    public ChatLanguageModel chat() {
        backend();
        return chatModel;
    }

    /** 流式模型（AiServices 编排使用） */
    public StreamingChatLanguageModel streaming() {
        backend();
        return streamingModel;
    }

    /** 当前生效后端描述，如 "openai-chat-completions:glm-4-flash"、"anthropic-messages:claude-sonnet-4-5"；未启用为 "rule-engine" */
    public String describe() {
        AiProps.Backend b = backend();
        return b == null ? "rule-engine" : normalizeProtocol(b.getProtocol()) + ":" + b.getModel();
    }

    /** 非流式单轮调用，异常返回 null（调用方降级处理） */
    public String chatOnce(String system, String user) {
        try {
            Response<AiMessage> resp = chat().generate(
                    List.of(SystemMessage.from(system), UserMessage.from(user)));
            return resp.content().text();
        } catch (Exception e) {
            log.warn("LLM 调用失败（{}），降级处理: {}", describe(), e.getMessage());
            return null;
        }
    }

    private synchronized AiProps.Backend backend() {
        if (resolved == null) {
            AiProps.Backend b = props.activeBackend();
            if (b != null) {
                build(b);
                resolved = b;
                log.info("LLM 网关启用后端: {} @ {} (model={})", b.getProtocol(), b.getBaseUrl(), b.getModel());
            }
        }
        return resolved;
    }

    private void build(AiProps.Backend b) {
        Duration timeout = Duration.ofSeconds(b.getTimeoutSeconds());
        switch (normalizeProtocol(b.getProtocol())) {
            case "anthropic-messages" -> {
                AnthropicMessagesChatModel model = new AnthropicMessagesChatModel(
                        b.getBaseUrl(), b.getApiKey(), b.getModel(),
                        b.getTemperature(), b.getMaxTokens(), timeout);
                chatModel = model;
                streamingModel = model;
            }
            case "openai-responses" -> {
                OpenAiResponsesChatModel model = new OpenAiResponsesChatModel(
                        b.getBaseUrl(), b.getApiKey(), b.getModel(),
                        b.getTemperature(), b.getMaxTokens(), timeout);
                chatModel = model;
                streamingModel = model;
            }
            default -> {
                // openai-chat-completions：GLM / DeepSeek / 通义 / OpenAI 经典协议
                String base = orDefault(b.getBaseUrl(), "https://api.openai.com/v1");
                chatModel = OpenAiChatModel.builder()
                        .apiKey(b.getApiKey())
                        .baseUrl(base)
                        .modelName(b.getModel())
                        .temperature(b.getTemperature())
                        .timeout(timeout)
                        .build();
                streamingModel = OpenAiStreamingChatModel.builder()
                        .apiKey(b.getApiKey())
                        .baseUrl(base)
                        .modelName(b.getModel())
                        .temperature(b.getTemperature())
                        .timeout(timeout)
                        .build();
            }
        }
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
