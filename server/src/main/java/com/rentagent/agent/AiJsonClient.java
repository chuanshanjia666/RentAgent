package com.rentagent.agent;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结构化模型调用（FR-08/14/15/16 的统一入口）：提示词 → 模型 → JSON → 目标类型。
 * <p>
 * 输出结构有两道保障，按可用性择优：
 * <ol>
 *   <li>首选 {@code response_format=json_schema}（见 {@link JsonSchemaChatModel}）：Schema 由目标类型生成，
 *       服务端在解码阶段强制约束，结构不合规的返回不会出现；仅 openai-chat-completions 协议提供该能力；</li>
 *   <li>回退到提示词声明字段 + 解析容错（剥代码块围栏、截取 JSON 主体），首次解析失败再追加格式强化说明重试一次。</li>
 * </ol>
 * 设计口径（2026-09-17 用户拍板"移除所有模拟数据、用真实 API，没有真实模型就直接报错"）：
 * 本系统**不提供任何本地规则兜底或模拟实现**。模型未配置、调用失败、返回不是合法 JSON、
 * 或结构不符合约定时，一律抛出 {@link ErrorCode#AI_UNAVAILABLE}（4001）并附带可读原因，
 * 由前端明确提示"AI 能力不可用"，而不是返回看似正常的假结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJsonClient {

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    /** 模型返回 JSON 时常见的代码块包裹/前后缀说明，解析前先剥离 */
    private static final String RETRY_HINT = "\n\n（注意：上一次的返回不是合法 JSON。本次请只输出 JSON 本身，不要任何说明文字或 Markdown 代码块。）";

    /** 端点明确拒绝 json_schema 参数后不再逐次尝试（进程内记忆，避免每次分析都白跑一次请求） */
    private volatile boolean jsonSchemaRejected;

    /**
     * 调用模型并解析为指定类型。
     *
     * @param systemPrompt 能力说明与输出字段约定（见 {@link AiPrompts}）
     * @param payload      业务输入（房源、样本统计、条款等）
     * @param type         目标类型（record 亦可）
     * @throws BizException 4001：模型不可用、调用失败或两次返回均无法解析
     */
    public <T> T call(String systemPrompt, String payload, Class<T> type) {
        return call(systemPrompt, payload, objectMapper.getTypeFactory().constructType(type));
    }

    /**
     * 调用模型并解析为泛型类型（如 {@code List<InterpItem>}）。
     */
    public <T> T call(String systemPrompt, String payload, JavaType type) {
        String raw = generateStructured(systemPrompt, payload, type);
        T parsed = raw == null ? null : parse(raw, type);
        if (parsed != null) {
            return parsed;
        }
        if (raw != null) {
            log.warn("结构化输出解析为 {} 失败，退回提示词约束重试；原始返回前 200 字：{}",
                    type.getTypeName(), abbreviate(raw));
        }
        raw = generate(systemPrompt, payload, false);
        parsed = parse(raw, type);
        if (parsed != null) {
            return parsed;
        }
        log.warn("模型返回无法解析为 {}，追加格式强化后重试一次；原始返回前 200 字：{}",
                type.getTypeName(), abbreviate(raw));
        raw = generate(systemPrompt, payload, true);
        parsed = parse(raw, type);
        if (parsed == null) {
            log.warn("模型重试后仍无法解析，原始返回前 200 字：{}", abbreviate(raw));
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(),
                    "模型返回内容不是约定的 JSON 结构，请稍后重试或更换模型");
        }
        return parsed;
    }

    /**
     * 首选路径：以 response_format=json_schema 强约束输出。不可用（协议不支持 / 端点拒绝 / 调用失败）返回 null，
     * 由上层退回提示词约束路径——两条路径拿到的都是模型真实输出，只是约束方式不同。
     */
    private String generateStructured(String systemPrompt, String payload, JavaType type) {
        if (jsonSchemaRejected || !llmGateway.available()) {
            return null;
        }
        JsonSchemaChatModel model = llmGateway.jsonSchema().orElse(null);
        if (model == null) {
            return null;
        }
        try {
            return model.generateJson(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(payload)),
                    JsonSchemas.name(type), JsonSchemas.strict(type), true);
        } catch (Exception e) {
            if (schemaRejectedByEndpoint(e)) {
                jsonSchemaRejected = true;
                log.warn("端点未接受 response_format=json_schema（{}），后续结构化分析改用提示词约束", e.getMessage());
            } else {
                log.warn("结构化输出调用失败，本次改用提示词约束：{}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 端点是否**明确拒绝** response_format=json_schema 这一参数（据此才值得进程内长期降级）。
     * <p>
     * 不能把任意 4xx 都当成该信号：上下文超长、请求体过大等 400 同样落在 4xx，
     * 一旦据此置位 {@link #jsonSchemaRejected}，本进程后续所有结构化调用都会被永久降级为提示词约束，
     * 而真正的原因（本次输入过长）与结构化输出能力毫无关系。故只有报错文本点名了该参数才算数。
     */
    private boolean schemaRejectedByEndpoint(Exception e) {
        if (!(e instanceof LlmHttpException http)) {
            return false;
        }
        int status = http.status();
        if (status == 429 || status < 400 || status >= 500) {
            return false;
        }
        String detail = http.getMessage() == null ? "" : http.getMessage().toLowerCase();
        return detail.contains("response_format") || detail.contains("json_schema");
    }

    /** 一次模型调用；未配置模型时明确报错，不做任何本地兜底 */
    private String generate(String systemPrompt, String payload, boolean strict) {
        if (!llmGateway.available()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(),
                    "未配置大模型服务：请注入模型凭据（AI_API_KEY / AGGREGATOR_API_KEY 等）后使用 AI 能力");
        }
        String user = strict ? payload + RETRY_HINT : payload;
        try {
            Response<AiMessage> resp = llmGateway.chat().generate(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(user)));
            String text = resp.content() == null ? null : resp.content().text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("模型返回内容为空");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("模型调用失败（{}）：{}", llmGateway.describe(), e.getMessage());
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "大模型调用失败：" + e.getMessage());
        }
    }

    /** 解析失败返回 null（交由调用方重试），不在此处抛异常 */
    private <T> T parse(String raw, JavaType type) {
        String json = extractJson(raw);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.debug("JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从模型输出里截出 JSON 主体：剥掉 ```json 代码块围栏，再按首个 '{'/'[' 与其配对结尾截取。
     * 兼容"这里是要点：{...}（以上仅供参考）"这类前后夹带说明的返回。
     */
    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstLineEnd = s.indexOf('\n');
            if (firstLineEnd > 0) {
                s = s.substring(firstLineEnd + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start = objStart < 0 ? arrStart : arrStart < 0 ? objStart : Math.min(objStart, arrStart);
        if (start < 0) {
            return null;
        }
        char open = s.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = s.lastIndexOf(close);
        if (end <= start) {
            return null;
        }
        return s.substring(start, end + 1).trim();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }
}
