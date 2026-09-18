package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Chat Completions 适配器的请求映射与流式解析测试（无需真实端点）。
 * 重点回归：**同一轮里正文与工具调用并存**时两者都必须保留——langchain4j 0.35 的流式组装器
 * 会在正文非空时丢弃 tool_calls（BUG-02 根因），本适配器必须同时给出文本与工具请求。
 */
class OpenAiChatCompletionsModelTest {

    private final OpenAiChatCompletionsModel model = new OpenAiChatCompletionsModel(
            "https://api.example.com/v1", "test-key", "demo-model", 0.7, null,
            Duration.ofSeconds(30), Map.of("User-Agent", "curl/8.5.0"));

    // ── 请求映射 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("UT-OPENAI-01 系统/用户消息映射为 role 字符串内容")
    void mapsBasicMessages() {
        var body = model.requestBody(List.of(
                SystemMessage.from("你是租房助手"),
                UserMessage.from("帮我找房")), List.of(), false, null);

        assertEquals("demo-model", body.path("model").asText());
        assertEquals(false, body.path("stream").asBoolean());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("你是租房助手", body.path("messages").get(0).path("content").asText());
        assertEquals("user", body.path("messages").get(1).path("role").asText());
        // max_tokens 不下发（推理型模型会把思维链算进补全长度）
        assertTrue(body.path("max_tokens").isMissingNode());
    }

    @Test
    @DisplayName("UT-OPENAI-02 工具以 tools[].function 结构传递，参数为 JSON Schema")
    void mapsTools() {
        var spec = ToolSpecification.builder()
                .name("searchHouses")
                .description("检索房源")
                .parameters(ToolParameters.builder()
                        .type("object")
                        .properties(Map.of("maxRent", Map.of("type", "integer", "description", "租金上限")))
                        .required(List.of("maxRent"))
                        .build())
                .build();

        var body = model.requestBody(List.of(UserMessage.from("找房")),
                List.of(spec), true, null);

        JsonNode tool = body.path("tools").get(0);
        assertEquals("function", tool.path("type").asText());
        assertEquals("searchHouses", tool.path("function").path("name").asText());
        assertEquals("object", tool.path("function").path("parameters").path("type").asText());
        assertTrue(tool.path("function").path("parameters").path("properties").has("maxRent"));
        assertEquals("maxRent", tool.path("function").path("parameters").path("required").get(0).asText());
    }

    @Test
    @DisplayName("UT-OPENAI-03 多轮工具消息回填为 assistant.tool_calls 与 role=tool")
    void mapsMultiTurnToolMessages() {
        ToolExecutionRequest call = ToolExecutionRequest.builder()
                .id("call_1").name("searchHouses").arguments("{\"maxRent\":2500}").build();
        var body = model.requestBody(List.of(
                UserMessage.from("找房"),
                AiMessage.from("我来帮您查询", List.of(call)),
                dev.langchain4j.data.message.ToolExecutionResultMessage.from(call, "[{\"id\":1}]")
        ), List.of(), false, null);

        JsonNode assistant = body.path("messages").get(1);
        assertEquals("assistant", assistant.path("role").asText());
        assertEquals("我来帮您查询", assistant.path("content").asText());
        assertEquals("searchHouses", assistant.path("tool_calls").get(0).path("function").path("name").asText());

        JsonNode toolResult = body.path("messages").get(2);
        assertEquals("tool", toolResult.path("role").asText());
        assertEquals("call_1", toolResult.path("tool_call_id").asText());
        assertEquals("[{\"id\":1}]", toolResult.path("content").asText());
    }

    @Test
    @DisplayName("UT-OPENAI-04 response_format 组装为 json_schema 结构")
    void buildsJsonSchemaResponseFormat() {
        JsonNode schema = new ObjectMapper().createObjectNode().put("type", "object");
        var body = model.requestBody(List.of(UserMessage.from("分析")),
                List.of(), false, model.responseFormat("pricingai", schema, true));

        JsonNode rf = body.path("response_format");
        assertEquals("json_schema", rf.path("type").asText());
        assertEquals("pricingai", rf.path("json_schema").path("name").asText());
        assertEquals(true, rf.path("json_schema").path("strict").asBoolean());
        assertEquals("object", rf.path("json_schema").path("schema").path("type").asText());
    }

    @Test
    @DisplayName("UT-OPENAI-05 同步响应同时解析正文与 tool_calls（content 为 null 时不报错）")
    void parsesSyncResponse() throws Exception {
        var mapper = new ObjectMapper();
        AiMessage withText = model.parseMessage(mapper.readTree(
                "{\"role\":\"assistant\",\"content\":\"我来帮您查询\","
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"searchHouses\",\"arguments\":\"{\\\"maxRent\\\":3000}\"}}]}"));
        assertEquals("我来帮您查询", withText.text());
        assertEquals(1, withText.toolExecutionRequests().size());
        assertEquals("{\"maxRent\":3000}", withText.toolExecutionRequests().get(0).arguments());

        AiMessage textNull = model.parseMessage(mapper.readTree(
                "{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_2\",\"function\":{\"name\":\"searchKnowledge\",\"arguments\":\"{}\"}}]}"));
        assertNull(textNull.text());
        assertEquals("searchKnowledge", textNull.toolExecutionRequests().get(0).name());

        AiMessage plain = model.parseMessage(mapper.readTree("{\"role\":\"assistant\",\"content\":\"您好\"}"));
        assertEquals("您好", plain.text());
        assertNull(plain.toolExecutionRequests());
    }

    // ── 流式解析 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("UT-OPENAI-06 回归：同一轮正文与工具调用并存时两者都保留（BUG-02 根因）")
    void keepsTextAndToolCallsInSameRound() {
        StreamCapture capture = stream(String.join("\n",
                chunk("{\"role\":\"assistant\"}"),
                chunk("{\"content\":\"我来帮您查询朝阳区的房源。\"}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"searchHouses\",\"arguments\":\"\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"district\\\":\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"朝阳区\\\"}\"}}]}"),
                chunk("{}", "tool_calls"),
                "data: [DONE]"));

        assertEquals("我来帮您查询朝阳区的房源。", capture.text.toString());
        assertEquals(1, capture.requests.size(), "工具调用不能被丢弃");
        assertEquals("searchHouses", capture.requests.get(0).name());
        assertEquals("{\"district\":\"朝阳区\"}", capture.requests.get(0).arguments());
        assertEquals("call_1", capture.requests.get(0).id());
        assertEquals("我来帮您查询朝阳区的房源。", capture.streamed.toString());
    }

    @Test
    @DisplayName("UT-OPENAI-07 纯工具调用轮（无正文）仍组成完整工具请求")
    void handlesToolOnlyRound() {
        StreamCapture capture = stream(String.join("\n",
                chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call_9\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"searchKnowledge\",\"arguments\":\"{}\"}}]}"),
                chunk("{}", "tool_calls"),
                "data: [DONE]"));

        assertNull(capture.message.text());
        assertEquals("searchKnowledge", capture.requests.get(0).name());
    }

    @Test
    @DisplayName("UT-OPENAI-08 多个工具调用按 index 分别累积，顺序稳定")
    void accumulatesParallelToolCalls() {
        StreamCapture capture = stream(String.join("\n",
                chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"function\":{\"name\":\"searchHouses\",\"arguments\":\"{\"}},"
                        + "{\"index\":1,\"id\":\"c1\",\"function\":{\"name\":\"searchKnowledge\",\"arguments\":\"{\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":1,\"function\":{\"arguments\":\"\\\"q\\\":1}\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"d\\\":1}\"}}]}"),
                "data: [DONE]"));

        assertEquals(2, capture.requests.size());
        assertEquals("searchHouses", capture.requests.get(0).name());
        assertEquals("{\"d\":1}", capture.requests.get(0).arguments());
        assertEquals("searchKnowledge", capture.requests.get(1).name());
        assertEquals("{\"q\":1}", capture.requests.get(1).arguments());
    }

    @Test
    @DisplayName("UT-OPENAI-09 流式返回 token 用量（末片携带 usage）")
    void readsStreamingTokenUsage() {
        StreamCapture capture = stream(String.join("\n",
                chunk("{\"content\":\"您好\"}"),
                "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                "data: [DONE]"));

        assertEquals(15, capture.usage[0].totalTokenCount());
        assertEquals("您好", capture.text.toString());
    }

    /**
     * 回归（2026-09-18 CI 客服场景三条断言失败根因）：推理内容占满 max_tokens 时正文与工具调用可能全空。
     * 此时模型并没有给出任何结论，必须按调用失败上报——早先的实现会伪造一句
     * "抱歉，这次没有生成有效回答"并只放在终止消息里（不发 delta），
     * 界面因此停在空白气泡，CI 也查不出原因。
     */
    @Test
    @DisplayName("UT-OPENAI-10 空返回（思维链耗尽预算）必须报错，不得伪造回答")
    void failsOnEmptyResponse() {
        LlmEmptyResponseException ex = assertThrows(LlmEmptyResponseException.class,
                () -> stream(String.join("\n",
                        chunk("{\"reasoning\":\"思考中\"}"),
                        chunk("{}", "length"),
                        "data: [DONE]")));

        assertTrue(ex.getMessage().contains("length"), ex.getMessage());
        assertTrue(ex.getMessage().contains("max-tokens"), ex.getMessage());
    }

    /** 同步通道同一口径：既无 content 也无 tool_calls 的响应同样是失败 */
    @Test
    @DisplayName("UT-OPENAI-12 同步响应为空时同样报错")
    void failsOnEmptySyncResponse() throws Exception {
        JsonNode message = new ObjectMapper().readTree("{\"content\":null}");
        assertThrows(LlmEmptyResponseException.class, () -> model.parseMessage(message));
    }

    /**
     * 回归：分片解析失败时 {@code consumeSseLine} 必须返回 false，调用方据此立即停止解析。
     * 少了这条约定，流里出现一个坏分片后循环仍会跑完并调用 onComplete，
     * 一次请求就会收到 onError + onComplete 两个终止回调。
     */
    @Test
    @DisplayName("UT-OPENAI-11 分片解析失败返回 false，调用方不得再发 onComplete")
    void stopsParsingAfterMalformedChunk() {
        StringBuilder text = new StringBuilder();
        Map<Integer, OpenAiChatCompletionsModel.ToolCallBuffer> buffers = new LinkedHashMap<>();
        TokenUsage[] usage = {null};
        String[] finishReason = {null};
        List<Throwable> errors = new ArrayList<>();
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                throw new AssertionError("解析已失败，不得再发终止回调 onComplete");
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        };

        boolean keepGoing = model.consumeSseLine("data: {\"choices\":[", text, buffers, usage, finishReason, handler);

        assertFalse(keepGoing, "解析失败必须返回 false 以终止后续解析");
        assertEquals(1, errors.size(), "错误只上报一次");
    }

    // ── 测试脚手架 ─────────────────────────────────────────────────────

    /** 一行 SSE 分片：delta 为片段 JSON，finish_reason 可选 */
    private static String chunk(String deltaJson) {
        return chunk(deltaJson, null);
    }

    private static String chunk(String deltaJson, String finishReason) {
        return "data: {\"choices\":[{\"index\":0,\"delta\":" + deltaJson + ",\"finish_reason\":"
                + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]}";
    }

    private static class StreamCapture {
        final StringBuilder streamed = new StringBuilder();
        final StringBuilder text = new StringBuilder();
        final Map<Integer, OpenAiChatCompletionsModel.ToolCallBuffer> buffers = new LinkedHashMap<>();
        final TokenUsage[] usage = {null};
        final String[] finishReason = {null};
        final List<ToolExecutionRequest> requests = new ArrayList<>();
        AiMessage message;

        String text() {
            return text.toString();
        }
    }

    /** 逐行喂 SSE 文本，返回聚合结果（与生产链路共用同一个 consumeSseLine + assemble） */
    private StreamCapture stream(String sse) {
        StreamCapture capture = new StreamCapture();
        List<String> forwarded = new ArrayList<>();
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                forwarded.add(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
            }

            @Override
            public void onError(Throwable error) {
                throw new IllegalStateException("解析失败", error);
            }
        };
        for (String line : sse.split("\n")) {
            model.consumeSseLine(line, capture.text, capture.buffers, capture.usage, capture.finishReason, handler);
        }
        capture.streamed.append(String.join("", forwarded));
        capture.message = model.assemble(capture.text, capture.buffers, capture.finishReason, capture.usage[0]);
        if (capture.message.toolExecutionRequests() != null) {
            capture.requests.addAll(capture.message.toolExecutionRequests());
        }
        return capture;
    }
}
