package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.common.BizException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结构化模型调用层单元测试（FR-08/14/15/16 的公共底座）。
 * 覆盖：JSON 提取（含代码块围栏与前后夹带说明）、解析失败重试一次、
 * 未配置模型/调用失败/两次都不合法时的 4001 硬报错（本系统不做本地兜底）。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-AIJSON-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiJsonClientTest {

    @Mock
    private LlmGateway llmGateway;
    @Mock
    private ChatLanguageModel chatModel;

    private AiJsonClient client;

    /** 与 AnalysisService.PricingAi 同形的测试结构 */
    public record Pricing(BigDecimal low, BigDecimal high, String basis) {
    }

    public record Interp(Integer index, Boolean risk, String explanation) {
    }

    @BeforeEach
    void setUp() {
        client = new AiJsonClient(llmGateway, new ObjectMapper());
        when(llmGateway.available()).thenReturn(true);
        when(llmGateway.describe()).thenReturn("openai-chat-completions:demo-model");
        when(llmGateway.chat()).thenReturn(chatModel);
    }

    private void modelSays(String... texts) {
        Response<AiMessage>[] responses = Arrays.stream(texts)
                .map(t -> Response.from(AiMessage.from(t)))
                .toArray(Response[]::new);
        when(chatModel.generate(anyList())).thenReturn(responses[0],
                Arrays.copyOfRange(responses, 1, responses.length));
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    @Test
    @DisplayName("UT-AIJSON-01 模型直接返回 JSON 对象时正常解析")
    void parsesPlainJsonObject() {
        modelSays("{\"low\":2000,\"high\":2400,\"basis\":\"同小区样本均价 2200\"}");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals(new BigDecimal("2000"), vo.low());
        assertEquals(new BigDecimal("2400"), vo.high());
        assertEquals("同小区样本均价 2200", vo.basis());
    }

    @Test
    @DisplayName("UT-AIJSON-02 返回被 Markdown 代码块包裹时先剥围栏再解析")
    void stripsMarkdownFence() {
        modelSays("```json\n{\"low\":1900,\"high\":2100,\"basis\":\"保守区间\"}\n```");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals(new BigDecimal("1900"), vo.low());
        assertEquals("保守区间", vo.basis());
    }

    @Test
    @DisplayName("UT-AIJSON-03 返回前后夹带说明文字时按首个 JSON 主体截取")
    void extractsJsonSurroundedByProse() {
        modelSays("好的，以下是建议：{\"low\":2000,\"high\":2300,\"basis\":\"依据\"} 以上仅供参考。");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals(new BigDecimal("2300"), vo.high());
    }

    @Test
    @DisplayName("UT-AIJSON-04 数组结果解析为泛型 List")
    void parsesGenericList() {
        modelSays("[{\"index\":0,\"risk\":false,\"explanation\":\"第一条\"},"
                + "{\"index\":1,\"risk\":true,\"explanation\":\"第二条有风险\"}]");

        List<Interp> items = client.call(AiPrompts.CONTRACT_INTERPRET, "payload",
                new ObjectMapper().getTypeFactory().constructCollectionType(List.class, Interp.class));

        assertEquals(2, items.size());
        assertEquals(true, items.get(1).risk());
        assertEquals("第二条有风险", items.get(1).explanation());
    }

    @Test
    @DisplayName("UT-AIJSON-05 首次返回非法 JSON 时追加格式强化说明重试一次并成功")
    void retriesOnceAfterInvalidJson() {
        modelSays("这不是 JSON，我重新给你。", "{\"low\":2000,\"high\":2200,\"basis\":\"重试后的依据\"}");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals("重试后的依据", vo.basis());
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel, times(2)).generate(captor.capture());
        String second = ((UserMessage) captor.getAllValues().get(1).get(1)).singleText();
        assertTrue(second.contains("只输出 JSON"), second);
        assertTrue(second.contains("payload"), second);
    }

    @Test
    @DisplayName("UT-AIJSON-06 两次返回都不合法时报 4001（不返回任何兜底数据）")
    void failsAfterTwoInvalidResponses() {
        modelSays("抱歉，我无法给出建议。", "仍然不是 JSON。");

        assertCode(4001, () -> client.call(AiPrompts.PRICING, "payload", Pricing.class));

        verify(chatModel, times(2)).generate(anyList());
    }

    @Test
    @DisplayName("UT-AIJSON-07 未配置模型时立即报 4001 且不发起调用")
    void failsFastWhenModelNotConfigured() {
        when(llmGateway.available()).thenReturn(false);

        assertCode(4001, () -> client.call(AiPrompts.PRICING, "payload", Pricing.class));

        verify(chatModel, never()).generate(anyList());
    }

    @Test
    @DisplayName("UT-AIJSON-08 模型调用抛异常时报 4001 并带上原因")
    void failsWhenModelCallThrows() {
        when(chatModel.generate(anyList())).thenThrow(new IllegalStateException("connect timed out"));

        BizException e = assertThrows(BizException.class,
                () -> client.call(AiPrompts.PRICING, "payload", Pricing.class));

        assertEquals(4001, e.getCode());
        assertTrue(e.getMessage().contains("connect timed out"), e.getMessage());
    }

    @Test
    @DisplayName("UT-AIJSON-09 模型返回空内容时重试后报 4001")
    void failsOnEmptyModelResponse() {
        modelSays("   ", "");

        assertCode(4001, () -> client.call(AiPrompts.PRICING, "payload", Pricing.class));
    }

    @Test
    @DisplayName("UT-AIJSON-10 缺字段可解析为 null，交由调用方按业务校验")
    void missingFieldsParseAsNull() {
        modelSays("{\"low\":2000}");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals(new BigDecimal("2000"), vo.low());
        assertNull(vo.high());
        assertNull(vo.basis());
    }

    // ── 结构化输出（response_format=json_schema）────────────────────────

    @Mock
    private JsonSchemaChatModel jsonSchemaModel;

    /** 结构化路径生效：直接取回 JSON，不再走提示词调用；Schema 按目标类型生成 */
    @Test
    @DisplayName("UT-AIJSON-11 端点支持 json_schema 时以强约束输出且不触发提示词调用")
    void prefersJsonSchemaOutput() {
        when(llmGateway.jsonSchema()).thenReturn(Optional.of(jsonSchemaModel));
        when(jsonSchemaModel.generateJson(anyList(), eq("Pricing"), any(), eq(true)))
                .thenReturn("{\"low\":2100,\"high\":2500,\"basis\":\"结构化约束返回\"}");

        Pricing vo = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals(new BigDecimal("2100"), vo.low());
        assertEquals("结构化约束返回", vo.basis());
        verify(chatModel, never()).generate(anyList());
    }

    /** 端点为 4xx（不支持该参数）时退回提示词路径，并记住失败不再逐次尝试 */
    @Test
    @DisplayName("UT-AIJSON-12 端点 4xx 拒绝 json_schema 后退回提示词路径且只尝试一次")
    void fallsBackWhenEndpointRejectsSchema() {
        when(llmGateway.jsonSchema()).thenReturn(Optional.of(jsonSchemaModel));
        when(jsonSchemaModel.generateJson(anyList(), anyString(), any(), anyBoolean()))
                .thenThrow(new LlmHttpException(400, "Chat Completions HTTP 400: unsupported response_format"));
        modelSays("{\"low\":2000,\"high\":2400,\"basis\":\"提示词路径\"}");

        Pricing first = client.call(AiPrompts.PRICING, "payload", Pricing.class);
        Pricing second = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals("提示词路径", first.basis());
        assertEquals("提示词路径", second.basis());
        verify(jsonSchemaModel, times(1)).generateJson(anyList(), anyString(), any(), anyBoolean());
    }

    /** 超时/5xx 属偶发故障：本次退回提示词路径，但下次仍会尝试结构化输出 */
    @Test
    @DisplayName("UT-AIJSON-13 结构化调用偶发失败不清除能力，下次仍走结构化路径")
    void keepsSchemaCapabilityAfterTransientFailure() {
        when(llmGateway.jsonSchema()).thenReturn(Optional.of(jsonSchemaModel));
        when(jsonSchemaModel.generateJson(anyList(), anyString(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("connect timed out"))
                .thenReturn("{\"low\":2000,\"high\":2400,\"basis\":\"第二次结构化成功\"}");
        modelSays("{\"low\":2000,\"high\":2400,\"basis\":\"提示词路径\"}");

        client.call(AiPrompts.PRICING, "payload", Pricing.class);
        Pricing second = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals("第二次结构化成功", second.basis());
        verify(jsonSchemaModel, times(2)).generateJson(anyList(), anyString(), any(), anyBoolean());
    }

    /**
     * 与结构化能力无关的 4xx（如上下文超长）不得触发长期降级：
     * 否则一次输入过长就会让本进程后续所有结构化调用都被永久降级。
     */
    @Test
    @DisplayName("UT-AIJSON-14 非参数类 4xx（上下文超长）不置降级位，下次仍走结构化路径")
    void unrelated4xxDoesNotDisableSchemaOutput() {
        when(llmGateway.jsonSchema()).thenReturn(Optional.of(jsonSchemaModel));
        when(jsonSchemaModel.generateJson(anyList(), anyString(), any(), anyBoolean()))
                .thenThrow(new LlmHttpException(400, "Chat Completions HTTP 400: context_length_exceeded"))
                .thenReturn("{\"low\":2000,\"high\":2400,\"basis\":\"第二次结构化成功\"}");
        modelSays("{\"low\":2000,\"high\":2400,\"basis\":\"提示词路径\"}");

        client.call(AiPrompts.PRICING, "payload", Pricing.class);
        Pricing second = client.call(AiPrompts.PRICING, "payload", Pricing.class);

        assertEquals("第二次结构化成功", second.basis());
        verify(jsonSchemaModel, times(2)).generateJson(anyList(), anyString(), any(), anyBoolean());
    }
}
