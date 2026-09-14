package com.rentagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 网关配置：多后端（协议）适配。protocol 取值（支持旧别名 openai/anthropic）：
 * <ul>
 *   <li>anthropic-messages —— Anthropic Messages API 原生（Claude 官方及兼容中转）；</li>
 *   <li>openai-chat-completions —— OpenAI Chat Completions（GLM / DeepSeek / 通义 等兼容此协议）；</li>
 *   <li>openai-responses —— OpenAI Responses API 新接口（内置适配器）。</li>
 * </ul>
 * 两种配置方式：
 * <ol>
 *   <li>快捷单后端：直接配 ai.api-key / ai.base-url / ai.model（环境变量 AI_API_KEY 等）；</li>
 *   <li>命名多后端：ai.backends.&lt;name&gt;.* 注册多个后端，ai.active（环境变量 AI_BACKEND）选择，
 *       演示与评测时可一键切换厂商。</li>
 * </ol>
 * 生效后端无 api-key 时网关不可用，上层智能体自动降级为规则引擎（风险 R1 应对）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProps {

    /** 命名后端选择；为空时取 backends 中第一个有 Key 的 */
    private String active;

    /** 命名后端注册表：&lt;name&gt; → {protocol, base-url, api-key, model} */
    private Map<String, Backend> backends = new LinkedHashMap<>();

    // ── 快捷单后端（backends 均无 Key 时生效）──
    private String protocol;
    private String baseUrl;
    private String apiKey;
    private String model;

    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private Integer timeoutSeconds = 60;

    @Data
    public static class Backend {
        /** openai | anthropic */
        private String protocol;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Integer timeoutSeconds;
    }

    /**
     * 解析当前生效的后端；无可用的（未配置或 Key 为空）返回 null。
     * 优先级：ai.active 指定的命名后端 → backends 中第一个有 Key 的 → 快捷单后端 → null。
     */
    public Backend activeBackend() {
        Backend named = null;
        if (!backends.isEmpty()) {
            String name = active == null || active.isBlank() ? null : active;
            if (name != null) {
                Backend picked = backends.get(name);
                if (picked == null || picked.getApiKey() == null || picked.getApiKey().isBlank()) {
                    named = null; // 显式指定的后端缺 Key，不静默换厂商
                } else {
                    named = picked;
                }
            } else {
                named = backends.values().stream()
                        .filter(b -> b.getApiKey() != null && !b.getApiKey().isBlank())
                        .findFirst().orElse(null);
            }
        }
        if (named != null) {
            return withDefaults(named);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            Backend b = new Backend();
            b.setProtocol(protocol);
            b.setBaseUrl(baseUrl);
            b.setApiKey(apiKey);
            b.setModel(model);
            return withDefaults(b);
        }
        return null;
    }

    private Backend withDefaults(Backend b) {
        if (b.getProtocol() == null || b.getProtocol().isBlank()) {
            b.setProtocol("openai-chat-completions");
        }
        if (b.getTemperature() == null) {
            b.setTemperature(temperature);
        }
        if (b.getMaxTokens() == null) {
            b.setMaxTokens(maxTokens);
        }
        if (b.getTimeoutSeconds() == null) {
            b.setTimeoutSeconds(timeoutSeconds);
        }
        return b;
    }
}
