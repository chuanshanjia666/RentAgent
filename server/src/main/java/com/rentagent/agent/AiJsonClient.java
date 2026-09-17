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
        String raw = generate(systemPrompt, payload, false);
        T parsed = parse(raw, type);
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
