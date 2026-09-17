package com.rentagent.agent;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 结构化输出 JSON Schema 生成测试：strict 模式的必需项与字段全量 required 约束 */
class JsonSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    public record Pricing(BigDecimal low, BigDecimal high, String basis, String note) {
    }

    public record Interp(Integer index, Boolean risk, String explanation) {
    }

    public record Detect(Integer riskScore, List<String> suspicions, String suggestion) {
    }

    private JavaType type(Class<?> clazz) {
        return mapper.getTypeFactory().constructType(clazz);
    }

    @Test
    @DisplayName("UT-SCHEMA-01 对象类型：属性齐全、required 列出全部字段、additionalProperties=false")
    void 对象严格化() {
        JsonNode schema = JsonSchemas.strict(type(Pricing.class));

        assertEquals("object", schema.path("type").asText());
        assertEquals(4, schema.path("properties").size());
        assertEquals(4, schema.path("required").size());
        assertTrue(schema.path("properties").has("low"));
        assertEquals("number", schema.path("properties").path("low").path("type").asText());
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertTrue(schema.path("$schema").isMissingNode(), "$schema 必须去掉");
        assertTrue(schema.path("id").isMissingNode(), "id 必须去掉");
    }

    @Test
    @DisplayName("UT-SCHEMA-02 数组字段：items 类型正确且嵌套对象同样严格化")
    void 数组字段() {
        JsonNode schema = JsonSchemas.strict(type(Detect.class));

        JsonNode items = schema.path("properties").path("suspicions").path("items");
        assertEquals("string", items.path("type").asText());
        assertEquals("integer", schema.path("properties").path("riskScore").path("type").asText());
    }

    @Test
    @DisplayName("UT-SCHEMA-03 顶层数组（合同解读）：根为 array，items 内的对象同样补齐 required")
    void 顶层数组() {
        JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, Interp.class);

        JsonNode schema = JsonSchemas.strict(listType);

        assertEquals("array", schema.path("type").asText());
        JsonNode item = schema.path("items");
        assertEquals("object", item.path("type").asText());
        assertEquals(3, item.path("required").size());
        assertFalse(item.path("additionalProperties").asBoolean(true));
    }

    @Test
    @DisplayName("UT-SCHEMA-04 Schema 名称只含字母数字下划线（顶层数组加 _list 后缀）")
    void schema名称() {
        assertEquals("Pricing", JsonSchemas.name(type(Pricing.class)));
        assertEquals("Interp_list",
                JsonSchemas.name(mapper.getTypeFactory().constructCollectionType(List.class, Interp.class)));
    }
}
