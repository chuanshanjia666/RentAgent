package com.rentagent.service;

import com.rentagent.agent.HousingToolProvider;
import com.rentagent.config.AiProps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 超时预算测试。
 * <p>
 * 关键口径：SSE 超时是"一轮最坏耗时"的上界，**不能短于一轮合法的对话**——短了会在正常的长回答
 * 中途把连接切断，用户一个字都收不到（而模型还在继续跑）；同时必须按 {@code ai.timeout-seconds}
 * 推导，换模型或调大上游超时后预算要跟着变，而不是留一个写死的常量。
 */
class ChatServiceTest {

    private static AiProps props(Integer timeoutSeconds) {
        AiProps props = new AiProps();
        props.setProtocol("openai-chat-completions");
        props.setBaseUrl("https://api.example.com/v1");
        props.setApiKey("test-key");
        props.setModel("gpt-4.1-mini");
        props.setTimeoutSeconds(timeoutSeconds);
        return props;
    }

    @Test
    @DisplayName("UT-CHAT-01 预算覆盖“工具上限 + 终止作答 + 兜底重跑”的最坏轮次")
    void sseTimeoutCoversWorstCaseRound() {
        long perCallSeconds = 60L;
        long worstUpstreamCalls = (HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND + 2) * 2L;

        long budget = ChatService.sseTimeoutMillis(props((int) perCallSeconds));

        assertTrue(budget > worstUpstreamCalls * perCallSeconds * 1000L,
                "预算必须严格大于最坏耗时（每次上游调用都可能跑满 ai.timeout-seconds）：" + budget);
        assertTrue(budget >= 600_000L,
                "默认配置下不得短于 10 分钟——原先写死的 5 分钟短于最坏合法轮次，慢一点但正常的回答会被截断");
    }

    @Test
    @DisplayName("UT-CHAT-02 预算按 ai.timeout-seconds 推导（换模型/调超时后自动跟随）")
    void sseTimeoutFollowsConfiguredUpstreamTimeout() {
        long perCallSeconds = 30L;
        long worstUpstreamCalls = (HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND + 2) * 2L;

        long shortTimeout = ChatService.sseTimeoutMillis(props((int) perCallSeconds));
        long longTimeout = ChatService.sseTimeoutMillis(props((int) (perCallSeconds * 2)));

        assertEquals(worstUpstreamCalls * perCallSeconds * 1000L, longTimeout - shortTimeout,
                "上游单次超时翻倍，预算的增量必须正好是最坏轮次的那部分");
    }
}
