package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI Chat Completions 协议适配器（自研，替代 langchain4j-open-ai 的模型类）：
 * POST {base-url}/chat/completions，SSE 流式，支持 Function Calling 与多轮工具消息回填。
 * <p>
 * 为什么自研：langchain4j-open-ai 0.35 的流式响应组装器（OpenAiStreamingResponseBuilder）在
 * **同一轮里同时出现正文与工具调用**时，只要正文非空就直接返回纯文本消息、把 tool_calls 全部丢弃，
 * 结果是模型先叙述"我来为您查询…"再发起工具调用的响应退化为一句过程语，工具永远不会被执行
 * （实测该协议下模型普遍采用"先说话再调工具"的输出风格）。本适配器对正文与工具调用分别累积、
 * 合并为一个 {@link AiMessage}，与 Anthropic / Responses 两个自研适配器行为一致。
 * <p>
 * 另支持 {@code response_format=json_schema} 结构化输出（见 {@link #generateJson}），
 * 供 AiJsonClient 约束四项 AI 分析的返回结构。
 */
public class OpenAiChatCompletionsModel implements ChatLanguageModel, StreamingChatLanguageModel, JsonSchemaChatModel {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final Duration timeout;
    private final Map<String, String> customHeaders;
    private final ObjectMapper mapper = new ObjectMapper();
    /** 非 2xx 时读多少行错误体用于异常信息（错误体通常只有一行 JSON，留几行防多行 HTML） */
    private static final long ERROR_BODY_LINES = 4;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiChatCompletionsModel(String baseUrl, String apiKey, String model,
                                     Double temperature, Integer maxTokens, Duration timeout,
                                     Map<String, String> customHeaders) {
        this.baseUrl = trimSlash(baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.customHeaders = customHeaders == null ? Map.of() : customHeaders;
    }

    // ── 同步 ───────────────────────────────────────────────────────────

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        try {
            HttpResponse<String> resp = http.send(request(requestBody(messages, tools, false, null)),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new LlmHttpException(resp.statusCode(),
                        "Chat Completions HTTP " + resp.statusCode() + ": " + abbreviate(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode choice = root.path("choices").path(0);
            AiMessage message = parseMessage(choice.path("message"));
            return Response.from(message, tokenUsage(root.path("usage")), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Chat Completions 调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat Completions 调用被中断", e);
        }
    }

    // ── 结构化输出（response_format=json_schema）─────────────────────────

    /**
     * 以 {@code response_format={"type":"json_schema","json_schema":{...,"strict":…}}}
     * 约束模型只输出符合给定 JSON Schema 的内容，返回其 JSON 文本。
     * 端点/模型不支持该参数时会以 4xx 失败（上层据 {@code strict} 逐级降级）。
     */
    @Override
    public String generateJson(List<ChatMessage> messages, String schemaName, JsonNode schema, boolean strict) {
        try {
            ObjectNode responseFormat = responseFormat(schemaName, schema, strict);
            HttpResponse<String> resp = http.send(request(requestBody(messages, List.of(), false, responseFormat)),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new LlmHttpException(resp.statusCode(),
                        "Chat Completions HTTP " + resp.statusCode() + ": " + abbreviate(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String text = message.path("content").isTextual() ? message.path("content").asText() : null;
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("结构化输出返回内容为空");
            }
            return text;
        } catch (IOException e) {
            throw new UncheckedIOException("Chat Completions 结构化调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat Completions 结构化调用被中断", e);
        }
    }

    /** response_format 节点：strict 模式要求 schema 内对象字段全部 required 且 additionalProperties=false */
    ObjectNode responseFormat(String schemaName, JsonNode schema, boolean strict) {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.set("schema", schema);
        jsonSchema.put("strict", strict);
        return responseFormat;
    }

    // ── 流式 ───────────────────────────────────────────────────────────

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> tools,
                         StreamingResponseHandler<AiMessage> handler) {
        try {
            // 会话级状态（每次调用独立，避免并发串扰）
            StringBuilder text = new StringBuilder();
            Map<Integer, ToolCallBuffer> toolBuffers = new LinkedHashMap<>();
            TokenUsage[] usage = {null};
            String[] finishReason = {null};
            http.sendAsync(request(requestBody(messages, tools, true, null)), HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        try (Stream<String> lines = response.body()) {
                            if (response.statusCode() / 100 != 2) {
                                // 非 2xx（限流/鉴权/网关故障）的响应体通常是一行错误 JSON 而非 SSE：
                                // 逐行解析一行都认不出，会被当成"模型没说话"而把真实故障吞掉，故必须先判状态码
                                String body = lines.limit(ERROR_BODY_LINES).collect(Collectors.joining(" "));
                                handler.onError(new LlmHttpException(response.statusCode(),
                                        "Chat Completions HTTP " + response.statusCode() + ": " + abbreviate(body)));
                                return;
                            }
                            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                                if (!consumeSseLine(it.next(), text, toolBuffers, usage, finishReason, handler)) {
                                    return;
                                }
                            }
                        }
                        handler.onComplete(Response.from(assemble(text, toolBuffers, finishReason, usage[0]), usage[0], null));
                    })
                    .exceptionally(ex -> {
                        handler.onError(LlmErrors.unwrap(ex));
                        return null;
                    });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    static class ToolCallBuffer {
        String id;
        StringBuilder name = new StringBuilder();
        StringBuilder arguments = new StringBuilder();
    }

    /**
     * SSE 分片：delta.content 逐字上抛；delta.tool_calls 按 index 累积 id / function.name / function.arguments
     * （参数是分片拼接的 JSON 片段，必须收完整个流才能解析）。reasoning 等其它字段按设计跳过。
     *
     * @return false 表示本行解析失败、已向 handler 上报 {@code onError}——终止回调已经发出，
     *         调用方必须立即停止解析并且**不得**再调用 {@code onComplete}，否则一次调用会收到两个终止回调
     */
    boolean consumeSseLine(String line, StringBuilder text, Map<Integer, ToolCallBuffer> toolBuffers,
                           TokenUsage[] usage, String[] finishReason, StreamingResponseHandler<AiMessage> handler) {
        try {
            if (!line.startsWith("data:")) {
                return true;
            }
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                return true;
            }
            JsonNode data = mapper.readTree(payload);
            if (data.hasNonNull("usage")) {
                usage[0] = tokenUsage(data.path("usage"));
            }
            JsonNode choice = data.path("choices").path(0);
            if (choice.hasNonNull("finish_reason")) {
                finishReason[0] = choice.path("finish_reason").asText();
            }
            JsonNode delta = choice.path("delta");
            if (delta.hasNonNull("content")) {
                String chunk = delta.path("content").asText("");
                if (!chunk.isEmpty()) {
                    text.append(chunk);
                    handler.onNext(chunk);
                }
            }
            JsonNode calls = delta.path("tool_calls");
            if (calls.isArray()) {
                for (JsonNode call : calls) {
                    ToolCallBuffer buf = toolBuffers.computeIfAbsent(call.path("index").asInt(), i -> new ToolCallBuffer());
                    if (call.hasNonNull("id")) {
                        buf.id = call.path("id").asText();
                    }
                    JsonNode fn = call.path("function");
                    if (fn.hasNonNull("name")) {
                        buf.name.append(fn.path("name").asText());
                    }
                    if (fn.hasNonNull("arguments")) {
                        buf.arguments.append(fn.path("arguments").asText());
                    }
                }
            }
            return true;
        } catch (Exception e) {
            handler.onError(e);
            return false;
        }
    }

    /**
     * 正文与工具调用合并为一个 AiMessage：两者可以同时存在（这正是 langchain4j 0.35 流式丢失的部分）。
     * <p>
     * 正文与工具调用都为空时**抛错而不是编一句话**：典型成因是推理内容占满 max_tokens
     * （{@code finish_reason=length}）或网关把流截断。此时模型并没有给出任何结论，
     * 伪造"没有生成有效回答"会让界面显示一条不存在的回答、CI 也查不出原因。
     */
    AiMessage assemble(StringBuilder text, Map<Integer, ToolCallBuffer> toolBuffers, String[] finishReason,
                       TokenUsage usage) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (ToolCallBuffer buf : toolBuffers.values()) {
            if (buf.name.length() == 0) {
                continue;
            }
            requests.add(ToolExecutionRequest.builder()
                    .id(buf.id == null || buf.id.isBlank() ? "call_" + requests.size() : buf.id)
                    .name(buf.name.toString())
                    .arguments(buf.arguments.length() == 0 ? "{}" : buf.arguments.toString())
                    .build());
        }
        String content = text.toString();
        if (!requests.isEmpty()) {
            return content.isEmpty() ? new AiMessage(requests) : new AiMessage(content, requests);
        }
        if (!content.isEmpty()) {
            return new AiMessage(content);
        }
        throw new LlmEmptyResponseException("模型未返回正文与工具调用（finish_reason=" + finishReason[0]
                + ", usage=" + describe(usage) + "）；若为 length 说明推理占满 max_tokens，请调大 ai.max-tokens 后重试");
    }

    /** 空返回时的用量描述：reasoning 占满预算的情况据此可一眼定位 */
    private static String describe(TokenUsage usage) {
        return usage == null ? "无" : usage.inputTokenCount() + "/" + usage.outputTokenCount()
                + "/" + usage.totalTokenCount();
    }

    // ── 请求/响应映射 ──────────────────────────────────────────────────

    ObjectNode requestBody(List<ChatMessage> messages, List<ToolSpecification> tools, boolean stream,
                           ObjectNode responseFormat) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        ArrayNode msgArr = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode item = msgArr.addObject();
            if (m instanceof SystemMessage s) {
                item.put("role", "system");
                item.put("content", s.text());
            } else if (m instanceof UserMessage u) {
                item.put("role", "user");
                item.put("content", u.singleText());
            } else if (m instanceof AiMessage a) {
                item.put("role", "assistant");
                item.put("content", a.text());
                List<ToolExecutionRequest> calls = a.toolExecutionRequests();
                if (calls != null && !calls.isEmpty()) {
                    ArrayNode callArr = item.putArray("tool_calls");
                    for (ToolExecutionRequest r : calls) {
                        ObjectNode call = callArr.addObject();
                        call.put("id", r.id());
                        call.put("type", "function");
                        ObjectNode fn = call.putObject("function");
                        fn.put("name", r.name());
                        fn.put("arguments", r.arguments());
                    }
                }
            } else if (m instanceof ToolExecutionResultMessage r) {
                item.put("role", "tool");
                item.put("tool_call_id", r.id());
                item.put("content", r.text());
            }
        }
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArr = body.putArray("tools");
            for (ToolSpecification t : tools) {
                ObjectNode tool = toolArr.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.name());
                if (t.description() != null) {
                    fn.put("description", t.description());
                }
                ToolParameters p = t.parameters();
                ObjectNode schema = fn.putObject("parameters");
                schema.put("type", p == null || p.type() == null ? "object" : p.type());
                if (p != null && p.properties() != null) {
                    schema.set("properties", mapper.valueToTree(p.properties()));
                }
                if (p != null && p.required() != null && !p.required().isEmpty()) {
                    schema.set("required", mapper.valueToTree(p.required()));
                }
            }
        }
        if (responseFormat != null) {
            body.set("response_format", responseFormat);
        }
        body.put("stream", stream);
        return body;
    }

    /** 同步响应的 message：content 与 tool_calls 同时解析（content 常为 null，不能当异常） */
    AiMessage parseMessage(JsonNode message) {
        StringBuilder text = new StringBuilder();
        if (message.path("content").isTextual()) {
            text.append(message.path("content").asText());
        }
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode fn = call.path("function");
            requests.add(ToolExecutionRequest.builder()
                    .id(call.path("id").asText())
                    .name(fn.path("name").asText())
                    .arguments(fn.path("arguments").asText("{}"))
                    .build());
        }
        String content = text.toString();
        if (requests.isEmpty() && content.isEmpty()) {
            // 与流式分支同一口径：既无正文也无工具调用＝本次调用失败，不得伪造回答
            throw new LlmEmptyResponseException("模型未返回正文与工具调用（同步响应）");
        }
        return requests.isEmpty() ? new AiMessage(content)
                : (content.isEmpty() ? new AiMessage(requests) : new AiMessage(content, requests));
    }

    private TokenUsage tokenUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        return new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(),
                usage.path("total_tokens").asInt());
    }

    private HttpRequest request(ObjectNode body) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeout);
        customHeaders.forEach(builder::header);
        return builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
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
