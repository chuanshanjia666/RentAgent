package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Responses API（新接口）适配器：POST {base-url}/responses，SSE 流式事件流。
 * 实现.langchain4j 0.35 的 ChatLanguageModel / StreamingChatLanguageModel，
 * 支持 Function Calling（工具以 {"type":"function"} 扁平结构传递）与多轮工具消息回填。
 * 消息映射：System → instructions；User → input_text；Ai 文本 → output_text；
 * Ai 工具调用 → function_call；工具结果 → function_call_output。
 */
public class OpenAiResponsesChatModel implements ChatLanguageModel, StreamingChatLanguageModel {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiResponsesChatModel(String baseUrl, String apiKey, String model,
                                    Double temperature, Integer maxTokens, Duration timeout) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : trimSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
    }

    // ── 同步 ───────────────────────────────────────────────────────────

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        try {
            ObjectNode body = requestBody(messages, tools, false);
            HttpResponse<String> resp = http.send(request(body), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Responses API HTTP " + resp.statusCode() + ": "
                        + abbreviate(resp.body()));
            }
            return Response.from(parseOutput(mapper.readTree(resp.body()).path("output")));
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Responses API 调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Responses API 调用被中断", e);
        }
    }

    // ── 流式 ───────────────────────────────────────────────────────────

    @Override
    public void generate(List<ChatMessage> messages, dev.langchain4j.model.StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> tools,
                         dev.langchain4j.model.StreamingResponseHandler<AiMessage> handler) {
        try {
            ObjectNode body = requestBody(messages, tools, true);
            StringBuilder text = new StringBuilder();
            String[] eventName = {null};
            JsonNode[] finalOutput = {null};
            http.sendAsync(request(body), HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        try {
                            response.body().forEach(line -> consumeSseLine(line, eventName, text, finalOutput, handler));
                            AiMessage message = finalOutput[0] != null
                                    ? parseOutput(finalOutput[0])
                                    : new AiMessage(text.toString());
                            handler.onComplete(Response.from(message));
                        } catch (Exception e) {
                            handler.onError(e);
                        }
                    })
                    .exceptionally(ex -> {
                        handler.onError(ex);
                        return null;
                    });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    /** SSE 行协议：event: <name> + data: <json>；关注 output_text.delta 与 completed（含完整 output，便于还原工具调用） */
    private void consumeSseLine(String line, String[] eventName, StringBuilder text, JsonNode[] finalOutput,
                                dev.langchain4j.model.StreamingResponseHandler<AiMessage> handler) {
        try {
            if (line.startsWith("event:")) {
                eventName[0] = line.substring(6).trim();
                return;
            }
            if (!line.startsWith("data:")) {
                return;
            }
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                return;
            }
            JsonNode data = mapper.readTree(payload);
            switch (eventName[0] == null ? "" : eventName[0]) {
                case "response.output_text.delta" -> {
                    String delta = data.path("delta").asText("");
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        handler.onNext(delta);
                    }
                }
                case "response.completed" -> finalOutput[0] = data.path("response").path("output");
                case "response.failed", "error" -> handler.onError(new IllegalStateException(
                        "Responses API 流式错误: " + abbreviate(payload)));
                default -> {
                }
            }
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    // ── 请求/响应映射 ──────────────────────────────────────────────────

    ObjectNode requestBody(List<ChatMessage> messages, List<ToolSpecification> tools, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        StringBuilder instructions = new StringBuilder();
        ArrayNode input = body.putArray("input");
        for (ChatMessage m : messages) {
            if (m instanceof SystemMessage s) {
                if (instructions.length() > 0) {
                    instructions.append('\n');
                }
                instructions.append(s.text());
            } else if (m instanceof UserMessage u) {
                ObjectNode item = input.addObject();
                item.put("role", "user");
                ArrayNode content = item.putArray("content");
                content.addObject().put("type", "input_text").put("text", u.singleText());
            } else if (m instanceof AiMessage a) {
                if (a.text() != null && !a.text().isEmpty()) {
                    ObjectNode item = input.addObject();
                    item.put("role", "assistant");
                    ArrayNode content = item.putArray("content");
                    content.addObject().put("type", "output_text").put("text", a.text());
                }
                for (ToolExecutionRequest r : a.toolExecutionRequests()) {
                    ObjectNode call = input.addObject();
                    call.put("type", "function_call");
                    call.put("call_id", r.id());
                    call.put("name", r.name());
                    call.put("arguments", r.arguments());
                }
            } else if (m instanceof ToolExecutionResultMessage r) {
                ObjectNode out = input.addObject();
                out.put("type", "function_call_output");
                out.put("call_id", r.id());
                out.put("output", r.text());
            }
        }
        if (instructions.length() > 0) {
            body.put("instructions", instructions.toString());
        }
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_output_tokens", maxTokens);
        }
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArr = body.putArray("tools");
            for (ToolSpecification t : tools) {
                ObjectNode tool = toolArr.addObject();
                tool.put("type", "function");
                tool.put("name", t.name());
                if (t.description() != null) {
                    tool.put("description", t.description());
                }
                dev.langchain4j.agent.tool.ToolParameters p = t.parameters();
                if (p != null) {
                    ObjectNode schema = tool.putObject("parameters");
                    schema.put("type", p.type() == null ? "object" : p.type());
                    if (p.properties() != null) {
                        schema.set("properties", mapper.valueToTree(p.properties()));
                    }
                    if (p.required() != null && !p.required().isEmpty()) {
                        schema.set("required", mapper.valueToTree(p.required()));
                    }
                }
            }
        }
        body.put("stream", stream);
        return body;
    }

    /** 解析 output[]：message.content[].output_text → 文本；function_call → 工具调用请求 */
    AiMessage parseOutput(JsonNode output) {
        StringBuilder text = new StringBuilder();
        List<ToolExecutionRequest> requests = new ArrayList<>();
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                String type = item.path("type").asText();
                if ("message".equals(type)) {
                    for (JsonNode c : item.path("content")) {
                        if ("output_text".equals(c.path("type").asText())) {
                            text.append(c.path("text").asText());
                        }
                    }
                } else if ("function_call".equals(type)) {
                    requests.add(ToolExecutionRequest.builder()
                            .id(item.path("call_id").asText())
                            .name(item.path("name").asText())
                            .arguments(item.path("arguments").asText("{}"))
                            .build());
                }
            }
        }
        if (requests.isEmpty()) {
            return new AiMessage(text.toString());
        }
        // 纯工具调用响应无文本：必须用 List 构造器（两参构造器会校验 text 非空）
        return text.length() == 0
                ? new AiMessage(requests)
                : new AiMessage(text.toString(), requests);
    }

    private HttpRequest request(ObjectNode body) throws java.io.IOException {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
