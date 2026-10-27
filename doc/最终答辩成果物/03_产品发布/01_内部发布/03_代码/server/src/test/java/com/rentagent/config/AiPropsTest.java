package com.rentagent.config;

import com.rentagent.agent.LlmGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** LLM 网关多后端解析优先级测试：active 指定 → 第一个有 Key 的命名后端 → 快捷单后端 → null（无可用后端，AI 能力报 4001） */
class AiPropsTest {

    private AiProps.Backend backend(String protocol, String key, String model) {
        AiProps.Backend b = new AiProps.Backend();
        b.setProtocol(protocol);
        b.setApiKey(key);
        b.setModel(model);
        return b;
    }

    @Test
    @DisplayName("UT-PROPS-01 无任何配置时无可用后端")
    void noBackendWhenNothingConfigured() {
        assertNull(new AiProps().activeBackend());
    }

    @Test
    @DisplayName("UT-PROPS-02 快捷单后端生效并补默认协议")
    void flatConfigAppliesDefaultProtocol() {
        AiProps p = new AiProps();
        p.setApiKey("k1");
        p.setModel("demo-flash");
        AiProps.Backend b = p.activeBackend();
        assertEquals("openai-chat-completions", b.getProtocol());
        assertEquals("demo-flash", b.getModel());
    }

    @Test
    @DisplayName("UT-PROPS-03 指定 active 的命名后端优先")
    void namedBackendWinsWhenActiveSet() {
        AiProps p = new AiProps();
        p.setApiKey("flat-key");
        p.getBackends().put("demo", backend("openai-chat-completions", "demo-key", "demo-large"));
        p.getBackends().put("claude", backend("anthropic-messages", "claude-key", "claude-sonnet-4-5"));
        p.setActive("claude");
        AiProps.Backend b = p.activeBackend();
        assertEquals("anthropic-messages", b.getProtocol());
        assertEquals("claude-sonnet-4-5", b.getModel());
    }

    @Test
    @DisplayName("UT-PROPS-04 active 后端缺 Key 时不静默换厂商，而是回落快捷配置")
    void missingKeyOnActiveDoesNotSilentlySwitchVendor() {
        AiProps p = new AiProps();
        p.setApiKey("flat-key");
        p.setProtocol("openai-chat-completions");
        p.setModel("demo-flash");
        p.getBackends().put("claude", backend("anthropic-messages", "", "claude-sonnet-4-5"));
        p.setActive("claude");
        assertEquals("demo-flash", p.activeBackend().getModel());
    }

    @Test
    @DisplayName("UT-PROPS-05 未指定 active 时取第一个有 Key 的命名后端")
    void firstNamedBackendWithKeyWhenActiveUnset() {
        AiProps p = new AiProps();
        p.getBackends().put("empty", backend("openai-chat-completions", "", "m0"));
        p.getBackends().put("demo", backend("openai-chat-completions", "k", "demo-large"));
        assertEquals("demo-large", p.activeBackend().getModel());
    }

    @Test
    @DisplayName("UT-PROPS-06 协议名归一化兼容旧别名")
    void normalizeProtocolAcceptsLegacyAliases() {
        assertEquals("openai-chat-completions", LlmGateway.normalizeProtocol("openai"));
        assertEquals("openai-chat-completions", LlmGateway.normalizeProtocol("chat-completions"));
        assertEquals("openai-chat-completions", LlmGateway.normalizeProtocol("OpenAI-Chat-Completions"));
        assertEquals("anthropic-messages", LlmGateway.normalizeProtocol("anthropic"));
        assertEquals("anthropic-messages", LlmGateway.normalizeProtocol("claude"));
        assertEquals("openai-responses", LlmGateway.normalizeProtocol("openai-responses"));
        assertEquals("openai-chat-completions", LlmGateway.normalizeProtocol(null));
    }
}
