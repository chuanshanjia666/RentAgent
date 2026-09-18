package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OpenAI Responses API 适配器的请求映射与响应解析测试（无需真实端点） */
class OpenAiResponsesChatModelTest {

    private final OpenAiResponsesChatModel model = new OpenAiResponsesChatModel(
            "https://api.openai.com/v1", "test-key", "gpt-4.1-mini", 0.7, 2048, Duration.ofSeconds(30));

    @Test
    @DisplayName("UT-RESP-01 系统消息进 instructions，用户消息进 input")
    void mapsSystemToInstructionsAndUserToInput() {
        var body = model.requestBody(
                List.of(SystemMessage.from("你是租房助手"), UserMessage.from("帮我找房")), List.of(), false);
        assertEquals("你是租房助手", body.path("instructions").asText());
        assertEquals(false, body.path("stream").asBoolean());
        JsonNode input = body.path("input");
        assertEquals(1, input.size());
        assertEquals("user", input.get(0).path("role").asText());
        assertEquals("帮我找房", input.get(0).path("content").get(0).path("text").asText());
        assertEquals("input_text", input.get(0).path("content").get(0).path("type").asText());
    }

    @Test
    @DisplayName("UT-RESP-02 工具映射为扁平 function 结构")
    void mapsToolsToFlatFunctionStructure() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("searchHouses")
                .description("检索房源")
                .parameters(ToolParameters.builder()
                        .type("object")
                        .properties(Map.of(
                                "maxRent", Map.of("type", "integer", "description", "租金上限")))
                        .required(List.of())
                        .build())
                .build();
        var body = model.requestBody(List.of(UserMessage.from("找房")), List.of(spec), true);
        assertEquals(true, body.path("stream").asBoolean());
        JsonNode tool = body.path("tools").get(0);
        assertEquals("function", tool.path("type").asText());
        assertEquals("searchHouses", tool.path("name").asText());
        assertEquals("object", tool.path("parameters").path("type").asText());
        assertTrue(tool.path("parameters").path("properties").has("maxRent"));
    }

    @Test
    @DisplayName("UT-RESP-03 多轮工具消息映射与输出解析")
    void mapsToolRoundTripAndParsesOutput() {
        ToolExecutionRequest call = ToolExecutionRequest.builder()
                .id("call_1").name("searchHouses").arguments("{\"maxRent\":2500}").build();
        var body = model.requestBody(List.of(
                UserMessage.from("找房"),
                AiMessage.from(call),
                ToolExecutionResultMessage.from(call, "[{\"id\":1}]")
        ), List.of(), false);
        JsonNode input = body.path("input");
        assertEquals("function_call", input.get(1).path("type").asText());
        assertEquals("call_1", input.get(1).path("call_id").asText());
        assertEquals("function_call_output", input.get(2).path("type").asText());
        assertEquals("call_1", input.get(2).path("call_id").asText());

        var output = JsonMapper.builder().build().createArrayNode();
        var msg = output.addObject();
        msg.put("type", "message");
        var content = msg.putArray("content");
        content.addObject().put("type", "output_text").put("text", "为你找到1套");
        var fn = output.addObject();
        fn.put("type", "function_call");
        fn.put("call_id", "call_2");
        fn.put("name", "searchHouses");
        fn.put("arguments", "{}");

        AiMessage ai = model.parseOutput(output);
        assertEquals("为你找到1套", ai.text());
        assertEquals(1, ai.toolExecutionRequests().size());
        assertEquals("call_2", ai.toolExecutionRequests().get(0).id());
    }
}
