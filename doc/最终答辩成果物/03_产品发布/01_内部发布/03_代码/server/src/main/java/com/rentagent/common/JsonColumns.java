package com.rentagent.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JSON 列读写工具（facilities / clauses / risk_flags / citations / tool_args / tool_result 等）。
 * <p>
 * 早先这套"序列化 + 容错解析"逻辑在 HouseService、ChatService、AdminChatService、DataInitializer
 * 里各写了一份（连解析失败的兜底值都不一致），改动一处就容易漏掉另一处；
 * 统一到这里后，各处的容错口径只有一个来源。
 * <p>
 * 约定：容错解析**不抛异常**——一条脏数据不应该把整个页面/接口打挂，失败时退化为空集合或原字符串。
 */
@Component
public class JsonColumns {

    private final ObjectMapper mapper;

    public JsonColumns(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 对象 → JSON 字符串；入参为 null 返回 null，序列化失败同样返回 null */
    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 数组字符串 → 指定元素类型的列表；null / 空 / 非法 JSON / 非数组一律返回空列表 */
    public <T> List<T> readList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** JSON 数组字符串 → Map 列表（引用/工具入参等结构化字段）；失败返回空列表 */
    public List<Map<String, Object>> readMapList(String json) {
        return readList(json, Map.class).stream().map(m -> (Map<String, Object>) m).toList();
    }

    /**
     * JSON 字符串 → 任意对象，**解析失败原样返回字符串**。
     * 与 {@link #readList} 的差别：这里要保住原文（工具入参不是合法 JSON 时，
     * 审计视图仍应把原始片段展示出来，而不是显示"无"）。
     */
    public Object readAny(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
