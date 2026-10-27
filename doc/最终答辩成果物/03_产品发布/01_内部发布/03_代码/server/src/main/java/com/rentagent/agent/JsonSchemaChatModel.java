package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 结构化输出能力（response_format=json_schema）：由支持该参数的协议适配器实现。
 * <p>
 * 当前仅 OpenAI Chat Completions 协议提供；Anthropic Messages / OpenAI Responses
 * 两个适配器不实现该接口，{@link LlmGateway#jsonSchema()} 返回空，
 * 结构化分析调用退回"提示词声明字段 + 解析容错"的既有路径。
 * <p>
 * 与提示词约束的区别：json_schema 由服务端在解码阶段强制约束，模型无法输出不合规结构，
 * 因此不再需要"格式不合规就重试一次"的补救。
 */
public interface JsonSchemaChatModel {

    /**
     * 以给定 JSON Schema 约束输出，返回模型输出的 JSON 文本。
     *
     * @param schemaName response_format.json_schema.name，同一端点内用于区分结构
     * @param schema     严格模式下的 JSON Schema（对象必填字段齐全、additionalProperties=false）
     * @param strict     true 由服务端强制约束；端点不支持时以 4xx 失败，调用方据此降级
     */
    String generateJson(List<ChatMessage> messages, String schemaName, JsonNode schema, boolean strict);
}
