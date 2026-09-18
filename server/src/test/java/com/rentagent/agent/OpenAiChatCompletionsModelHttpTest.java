package com.rentagent.agent;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 适配器真实 HTTP 行为测试（用 JDK 内置 HttpServer 打桩端点）。
 * <p>
 * 覆盖 2026-09-18 CI「客服场景回答为空」的两个传输层根因：
 * ① 非 2xx（限流/网关故障）的响应体不是 SSE，不判状态码就会被当成"模型没说话"而静默吞掉；
 * ② 推理占满预算时空返回必须报错，不得伪造回答。
 */
class OpenAiChatCompletionsModelHttpTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private OpenAiChatCompletionsModel modelAgainst(int status, String contentType, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new OpenAiChatCompletionsModel("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "demo-model", 0.7, 2048, Duration.ofSeconds(10), Map.of());
    }

    /** 收集流式回调结果 */
    private static class Captured {
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder streamed = new StringBuilder();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Response<AiMessage>> completed = new AtomicReference<>();

        StreamingResponseHandler<AiMessage> handler() {
            return new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    streamed.append(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    completed.set(response);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    error.set(t);
                    latch.countDown();
                }
            };
        }

        boolean await() throws InterruptedException {
            return latch.await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("UT-OPENAI-13 流式遇非 2xx（限流）上报错误而不是当成空回答")
    void reportsHttpErrorOnStreaming() throws Exception {
        OpenAiChatCompletionsModel model = modelAgainst(429, "application/json",
                "{\"error\":{\"message\":\"rate limit exceeded\"}}");
        Captured captured = new Captured();

        model.generate(List.of(UserMessage.from("你好")), List.of(ToolSpecification.builder().name("t").build()),
                captured.handler());

        assertTrue(captured.await(), "必须在超时前给出终止回调");
        LlmHttpException error = assertInstanceOf(LlmHttpException.class, captured.error.get());
        assertEquals(429, error.status());
        assertTrue(error.retryable(), "限流属于可重试故障");
        assertTrue(error.getMessage().contains("rate limit"), error.getMessage());
        assertNull(captured.completed.get(), "已上报错误就不得再发 onComplete");
    }

    @Test
    @DisplayName("UT-OPENAI-14 流式只有推理、没有正文与工具调用时按空返回报错")
    void reportsEmptyResponseOnStreaming() throws Exception {
        OpenAiChatCompletionsModel model = modelAgainst(200, "text/event-stream", String.join("\n",
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning\":\"思考中\"}}]}",
                "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"length\"}],"
                        + "\"usage\":{\"prompt_tokens\":1600,\"completion_tokens\":4096,\"total_tokens\":5696}}",
                "data: [DONE]",
                ""));
        Captured captured = new Captured();

        model.generate(List.of(UserMessage.from("押金怎么退")), List.of(), captured.handler());

        assertTrue(captured.await(), "必须在超时前给出终止回调");
        LlmEmptyResponseException error = assertInstanceOf(LlmEmptyResponseException.class, captured.error.get());
        assertTrue(error.getMessage().contains("length"), error.getMessage());
        assertTrue(error.getMessage().contains("4096"), error.getMessage());
        assertEquals("", captured.streamed.toString(), "空返回不应产生任何 delta");
        assertNull(captured.completed.get(), "已上报错误就不得再发 onComplete");
    }

    @Test
    @DisplayName("UT-OPENAI-15 正常流式：正文逐片回调并正常收尾（防修复引入回归）")
    void streamsContentNormally() throws Exception {
        OpenAiChatCompletionsModel model = modelAgainst(200, "text/event-stream", String.join("\n",
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"押金\"}}]}",
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"全额退还\"}}]}",
                "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
                ""));
        Captured captured = new Captured();

        model.generate(List.of(UserMessage.from("押金怎么退")), List.of(), captured.handler());

        assertTrue(captured.await(), "必须在超时前给出终止回调");
        assertNull(captured.error.get(), "正常流不应报错");
        assertEquals("押金全额退还", captured.streamed.toString());
        assertEquals("押金全额退还", captured.completed.get().content().text());
    }
}
