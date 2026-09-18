package com.rentagent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 智能体重试策略测试（2026-09-18 CI「客服回答为空」回归）。
 * <p>
 * 关键口径：模型"什么都没返回"（推理占满 max_tokens、流被截断、限流）属于可自愈的传输层故障，
 * 必须用非流式通道重跑一次——拿到的仍是模型真实回答；而鉴权/参数类 4xx 重跑没有意义，须直接如实报错。
 */
class LlmAgentTest {

    private static final String CITATIONS = "[{\"title\":\"押金规则\"}]";

    /** 记录回调结果的测试用 Callback */
    private static class Recorder implements AgentEngine.Callback {
        final StringBuilder text = new StringBuilder();
        String citations;
        Throwable error;
        boolean completed;

        @Override
        public void onToken(String token) {
            text.append(token);
        }

        @Override
        public void onComplete(String fullText, String citationsJson, boolean transferred) {
            this.completed = true;
            this.citations = citationsJson;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }
    }

    /** 流式模型桩：要么按脚本上报错误，要么按脚本回调正文 */
    private static class StubStreaming implements StreamingChatLanguageModel {
        private final Throwable error;
        private final String content;
        /** 为 true 时只在终止消息里给正文、全程不回调 onNext（部分网关的实际行为） */
        private final boolean terminalOnly;

        StubStreaming(Throwable error, String content, boolean terminalOnly) {
            this.error = error;
            this.content = content;
            this.terminalOnly = terminalOnly;
        }

        static StubStreaming failing(Throwable error) {
            return new StubStreaming(error, null, false);
        }

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            generate(messages, List.of(), handler);
        }

        @Override
        public void generate(List<ChatMessage> messages, List<ToolSpecification> tools,
                             StreamingResponseHandler<AiMessage> handler) {
            if (error != null) {
                handler.onError(error);
                return;
            }
            if (!terminalOnly) {
                handler.onNext(content);
            }
            handler.onComplete(Response.from(new AiMessage(content)));
        }
    }

    /** 非流式模型桩：重跑通道，记录调用次数 */
    private static class StubSync implements ChatLanguageModel {
        final AtomicInteger calls = new AtomicInteger();
        private final String content;
        private final RuntimeException error;

        StubSync(String content, RuntimeException error) {
            this.content = content;
            this.error = error;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return generate(messages, List.of());
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools) {
            calls.incrementAndGet();
            if (error != null) {
                throw error;
            }
            return Response.from(new AiMessage(content));
        }
    }

    /** 装配一个工具集为空、知识库引用固定的智能体 */
    private LlmAgent agent(LlmGateway gateway) {
        HousingToolProvider provider = mock(HousingToolProvider.class);
        when(provider.provideTools(any())).thenReturn(new ToolProviderResult(Map.of()));
        when(provider.takeKnowledgeCitations(anyLong())).thenReturn(CITATIONS);
        when(provider.takeRoundTools(anyLong())).thenReturn(Set.of());
        return new LlmAgent(gateway, provider);
    }

    @Test
    @DisplayName("UT-AGENT-01 流式空返回（推理占满预算）→ 非流式重跑一次并正常收尾")
    void retriesOnceWhenStreamingReturnsNothing() {
        LlmGateway gateway = mock(LlmGateway.class);
        StubSync sync = new StubSync("押金在退租验收后 3 个工作日内全额退还。", null);
        when(gateway.streaming()).thenReturn(StubStreaming.failing(
                new LlmEmptyResponseException("模型未返回正文与工具调用（finish_reason=length）")));
        when(gateway.chat()).thenReturn(sync);

        Recorder recorder = new Recorder();
        agent(gateway).stream(7L, 1L, 2, "押金怎么退", recorder);

        assertEquals(1, sync.calls.get(), "空返回必须触发且只触发一次非流式重跑");
        assertNull(recorder.error, "重跑成功就不该报错");
        assertTrue(recorder.completed, "重跑成功后应正常收尾");
        assertEquals("押金在退租验收后 3 个工作日内全额退还。", recorder.text.toString());
        assertEquals(CITATIONS, recorder.citations, "重跑期间的工具引用要一并带出");
    }

    @Test
    @DisplayName("UT-AGENT-02 限流（429）同样重跑一次")
    void retriesOnceOnRateLimit() {
        LlmGateway gateway = mock(LlmGateway.class);
        StubSync sync = new StubSync("已为您查到结果。", null);
        when(gateway.streaming()).thenReturn(StubStreaming.failing(new LlmHttpException(429, "HTTP 429")));
        when(gateway.chat()).thenReturn(sync);

        Recorder recorder = new Recorder();
        agent(gateway).stream(7L, 1L, 2, "押金怎么退", recorder);

        assertEquals(1, sync.calls.get());
        assertNull(recorder.error);
        assertTrue(recorder.completed);
    }

    @Test
    @DisplayName("UT-AGENT-03 鉴权类 4xx 不重跑，直接如实报错（不把配置问题伪装成偶发故障）")
    void doesNotRetryOnClientError() {
        LlmGateway gateway = mock(LlmGateway.class);
        StubSync sync = new StubSync("不该被调用", null);
        when(gateway.streaming()).thenReturn(StubStreaming.failing(new LlmHttpException(401, "HTTP 401: invalid api key")));
        when(gateway.chat()).thenReturn(sync);

        Recorder recorder = new Recorder();
        agent(gateway).stream(7L, 1L, 2, "押金怎么退", recorder);

        assertEquals(0, sync.calls.get(), "4xx 不得重跑");
        assertInstanceOf(LlmHttpException.class, recorder.error);
        assertFalse(recorder.completed);
    }

    @Test
    @DisplayName("UT-AGENT-04 正文只在终止消息里给出时补走 delta 通道（前端不出现空白气泡）")
    void replaysTerminalTextAsDeltas() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.streaming()).thenReturn(new StubStreaming(null, "押金在验收后全额退还。", true));
        StubSync sync = new StubSync("不该被调用", null);
        when(gateway.chat()).thenReturn(sync);

        Recorder recorder = new Recorder();
        agent(gateway).stream(7L, 1L, 2, "押金怎么退", recorder);

        assertEquals(0, sync.calls.get());
        assertEquals("押金在验收后全额退还。", recorder.text.toString(), "终止消息里的正文必须补发为 delta");
        assertTrue(recorder.completed);
        assertNull(recorder.error);
    }

    @Test
    @DisplayName("UT-AGENT-05 非流式重跑也失败时如实上报错误，不伪造回答")
    void reportsErrorWhenRetryAlsoFails() {
        LlmGateway gateway = mock(LlmGateway.class);
        StubSync sync = new StubSync(null, new LlmEmptyResponseException("同步响应也为空"));
        when(gateway.streaming()).thenReturn(StubStreaming.failing(new LlmEmptyResponseException("流式响应为空")));
        when(gateway.chat()).thenReturn(sync);

        Recorder recorder = new Recorder();
        agent(gateway).stream(7L, 1L, 2, "押金怎么退", recorder);

        assertEquals(1, sync.calls.get());
        assertFalse(recorder.completed, "两次都失败时不得给出任何回答");
        assertTrue(recorder.text.toString().isBlank(), recorder.text.toString());
        assertInstanceOf(LlmEmptyResponseException.class, recorder.error);
    }
}
