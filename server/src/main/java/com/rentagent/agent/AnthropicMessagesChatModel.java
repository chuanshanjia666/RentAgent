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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 原生协议适配器：POST {base-url}/v1/messages，SSE 流式。
 * 实现.langchain4j 0.35 的 ChatLanguageModel / StreamingChatLanguageModel，
 * 支持 Function Calling（tools 为扁平 name/description/input_schema 结构）与多轮工具消息回填。
 *
 * 与 langchain4j-anthropic 0.35 的差异：本适配器对 content[] 中的未知块（如 GLM-5 的
 * "thinking" 思维链块）按设计跳过，只提取 text 与 tool_use——这是选择自研适配器的核心原因。
 * 消息映射：System → system；User → text 块；Ai 文本 → text 块；Ai 工具调用 → tool_use 块；
 * 工具结果 → tool_result 块。
 */
public class AnthropicMessagesChatModel implements ChatLanguageModel, StreamingChatLanguageModel {

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

    public AnthropicMessagesChatModel(String baseUrl, String apiKey, String model,
                                      Double temperature, Integer maxTokens, Duration timeout) {
        this.baseUrl = trimSlash(baseUrl == null || baseUrl.isBlank() ? "https://api.anthropic.com" : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens == null ? 2048 : maxTokens;
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
            HttpResponse<String> resp = http.send(request(requestBody(messages, tools, false)),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Messages API HTTP " + resp.statusCode() + ": " + abbreviate(resp.body()));
            }
            return Response.from(parseContent(mapper.readTree(resp.body()).path("content")));
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Messages API 调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Messages API 调用被中断", e);
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
            // 流式过程中的会话级状态（每次调用独立，避免并发串扰）
            StringBuilder text = new StringBuilder();
            Map<Integer, ToolBuffer> toolBuffers = new LinkedHashMap<>();
            String[] eventName = {null};
            http.sendAsync(request(requestBody(messages, tools, true)), HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        try {
                            response.body().forEach(line -> consumeSseLine(line, eventName, text, toolBuffers, handler));
                            List<ToolExecutionRequest> requests = new ArrayList<>();
                            for (ToolBuffer tb : toolBuffers.values()) {
                                requests.add(ToolExecutionRequest.builder()
                                        .id(tb.id).name(tb.name)
                                        .arguments(tb.json.length() == 0 ? "{}" : tb.json.toString())
                                        .build());
                            }
                            AiMessage message;
                            if (!requests.isEmpty()) {
                                // 纯工具调用响应无文本：必须用 List 构造器（两参构造器会校验 text 非空）
                                message = text.length() == 0
                                        ? new AiMessage(requests)
                                        : new AiMessage(text.toString(), requests);
                            } else if (text.length() > 0) {
                                message = new AiMessage(text.toString());
                            } else {
                                // 思维链耗尽 max_tokens 等场景：content 为空，禁止抛 "text cannot be blank"
                                message = new AiMessage("抱歉，这次没有生成有效回答，请换个说法再试一次。");
                            }
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

    private static class ToolBuffer {
        String id;
        String name;
        StringBuilder json = new StringBuilder();
    }

    /** SSE 行协议：event: <name> + data: <json>。text_delta 逐字上抛；tool_use 由 content_block_start + input_json_delta 组装 */
    private void consumeSseLine(String line, String[] eventName, StringBuilder text,
                                Map<Integer, ToolBuffer> toolBuffers,
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
            if (payload.isEmpty()) {
                return;
            }
            JsonNode data = mapper.readTree(payload);
            switch (eventName[0] == null ? "" : eventName[0]) {
                case "content_block_start" -> {
                    JsonNode block = data.path("content_block");
                    if ("tool_use".equals(block.path("type").asText())) {
                        ToolBuffer tb = new ToolBuffer();
                        tb.id = block.path("id").asText();
                        tb.name = block.path("name").asText();
                        toolBuffers.put(data.path("index").asInt(), tb);
                    }
                }
                case "content_block_delta" -> {
                    JsonNode delta = data.path("delta");
                    String type = delta.path("type").asText();
                    if ("text_delta".equals(type)) {
                        String t = delta.path("text").asText("");
                        if (!t.isEmpty()) {
                            text.append(t);
                            handler.onNext(t);
                        }
                    } else if ("input_json_delta".equals(type)) {
                        ToolBuffer tb = toolBuffers.get(data.path("index").asInt());
                        if (tb != null) {
                            tb.json.append(delta.path("partial_json").asText(""));
                        }
                    }
                    // thinking_delta / signature_delta：按设计跳过
                }
                case "error" -> handler.onError(new IllegalStateException(
                        "Messages API 流式错误: " + abbreviate(payload)));
                default -> {
                }
            }
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    // ── 请求/响应映射 ──────────────────────────────────────────────────

    private ObjectNode requestBody(List<ChatMessage> messages, List<ToolSpecification> tools, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        // GLM-5 的 Anthropic 兼容层默认输出 thinking 思维链块（拖慢首字且挤占 max_tokens），
        // 实测传 thinking=disabled 可关闭且不影响 Function Calling
        body.putObject("thinking").put("type", "disabled");
        ArrayNode msgArr = body.putArray("messages");
        StringBuilder system = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m instanceof SystemMessage s) {
                if (system.length() > 0) {
                    system.append('\n');
                }
                system.append(s.text());
            } else if (m instanceof UserMessage u) {
                ObjectNode item = msgArr.addObject();
                item.put("role", "user");
                ArrayNode content = item.putArray("content");
                content.addObject().put("type", "text").put("text", u.singleText());
            } else if (m instanceof AiMessage a) {
                ObjectNode item = msgArr.addObject();
                item.put("role", "assistant");
                ArrayNode content = item.putArray("content");
                if (a.text() != null && !a.text().isEmpty()) {
                    content.addObject().put("type", "text").put("text", a.text());
                }
                for (ToolExecutionRequest r : a.toolExecutionRequests()) {
                    ObjectNode call = content.addObject();
                    call.put("type", "tool_use");
                    call.put("id", r.id());
                    call.put("name", r.name());
                    call.set("input", parseJsonOrEmptyObject(r.arguments()));
                }
            } else if (m instanceof ToolExecutionResultMessage r) {
                ObjectNode item = msgArr.addObject();
                item.put("role", "user");
                ArrayNode content = item.putArray("content");
                content.addObject().put("type", "tool_result").put("tool_use_id", r.id()).put("content", r.text());
            }
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArr = body.putArray("tools");
            for (ToolSpecification t : tools) {
                ObjectNode tool = toolArr.addObject();
                tool.put("name", t.name());
                if (t.description() != null) {
                    tool.put("description", t.description());
                }
                dev.langchain4j.agent.tool.ToolParameters p = t.parameters();
                ObjectNode schema = tool.putObject("input_schema");
                schema.put("type", p == null || p.type() == null ? "object" : p.type());
                if (p != null && p.properties() != null) {
                    schema.set("properties", mapper.valueToTree(p.properties()));
                }
                if (p != null && p.required() != null && !p.required().isEmpty()) {
                    schema.set("required", mapper.valueToTree(p.required()));
                }
            }
        }
        body.put("stream", stream);
        return body;
    }

    /** 解析 content[]：text 块拼接为文本，tool_use 块转为工具调用请求；其余块（thinking 等）跳过 */
    private AiMessage parseContent(JsonNode content) {
        StringBuilder text = new StringBuilder();
        List<ToolExecutionRequest> requests = new ArrayList<>();
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    text.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    requests.add(ToolExecutionRequest.builder()
                            .id(block.path("id").asText())
                            .name(block.path("name").asText())
                            .arguments(serialize(block.path("input")))
                            .build());
                }
                // thinking / 其他未知块：跳过
            }
        }
        if (requests.isEmpty()) {
            return new AiMessage(text.toString());
        }
        return text.length() == 0
                ? new AiMessage(requests)
                : new AiMessage(text.toString(), requests);
    }

    private HttpRequest request(ObjectNode body) throws java.io.IOException {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
    }

    private JsonNode parseJsonOrEmptyObject(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private String serialize(JsonNode node) {
        return node == null || node.isMissingNode() ? "{}" : node.toString();
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
