package com.rentagent.agent;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

import java.util.Iterator;

/**
 * 结构化输出的 JSON Schema 生成：由目标 Java 类型（四项 AI 分析的返回结构）直接生成，
 * 避免字段约定在"提示词"和"Schema"两处各写一份而漂移。
 * <p>
 * {@link #strict(JavaType)} 会按 OpenAI 结构化输出 strict 模式的要求规范化：
 * 每个对象都补全 {@code required}（strict 要求属性必须全部列出）并置 {@code additionalProperties=false}，
 * 同时去掉 {@code $schema} / {@code id} 这类非约束键。实测本系统四条通道的端点均接受该形态
 * （含顶层数组——合同解读返回的就是数组）。
 */
public final class JsonSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaGenerator GENERATOR = new JsonSchemaGenerator(MAPPER);

    private JsonSchemas() {
    }

    /** response_format.json_schema.name：仅允许字母数字与下划线，取类型简名（如 PricingAi / InterpAi_list） */
    public static String name(JavaType type) {
        String simple = type.isCollectionLikeType()
                ? type.containedType(0).getRawClass().getSimpleName() + "_list"
                : type.getRawClass().getSimpleName();
        return simple.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** 由类型生成 strict 模式 JSON Schema */
    public static JsonNode strict(JavaType type) {
        try {
            JsonNode node = MAPPER.valueToTree(GENERATOR.generateSchema(type));
            return normalize(node);
        } catch (JsonMappingException e) {
            // 目标类型都在本模块内定义（record + 基本类型），生成失败属编码错误而非运行时输入问题
            throw new IllegalStateException("无法为 " + type.getTypeName() + " 生成 JSON Schema", e);
        }
    }

    /** 递归规范化：对象节点补齐 required 与 additionalProperties，并剔除非约束键 */
    private static JsonNode normalize(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.remove("$schema");
            obj.remove("id");
            JsonNode properties = obj.path("properties");
            if ("object".equals(obj.path("type").asText()) && properties.isObject()) {
                ArrayNode required = obj.putArray("required");
                for (Iterator<String> names = properties.fieldNames(); names.hasNext(); ) {
                    required.add(names.next());
                }
                obj.put("additionalProperties", false);
            }
            // properties / items / definitions 的子节点都是 Schema 本身，逐层下钻
            obj.forEach(JsonSchemas::normalize);
        } else if (node.isArray()) {
            node.forEach(JsonSchemas::normalize);
        }
        return node;
    }
}
