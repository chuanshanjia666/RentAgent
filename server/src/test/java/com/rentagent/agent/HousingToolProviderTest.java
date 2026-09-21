package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.House;
import com.rentagent.security.UserContext;
import com.rentagent.service.HouseService;
import com.rentagent.service.KbService;
import com.rentagent.service.SearchService;
import com.rentagent.service.ToolTraceService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具提供器的本轮会话态与收敛护栏测试。
 * <p>
 * 关键口径：流式路径的"模型 → 工具 → 再叫模型"由 langchain4j 的流式处理器递归驱动，
 * **没有**内建轮次上限，且工具集在处理器构造时就已定死，返回文本拦不住它——
 * 因此超上限后必须靠异常硬停本轮（{@link ToolCallLimitExceededException}）；
 * 本轮状态则必须能整体移除，否则 {@code rounds} 会随用过的会话数一直涨。
 */
class HousingToolProviderTest {

    private static final long SESSION = 7L;
    private static final UserContext.User VIEWER = new UserContext.User(11L, 1);

    private KbService kbService;
    private HouseService houseService;
    private ToolTraceService toolTraceService;
    private HousingToolProvider provider;

    @BeforeEach
    void setUp() {
        SearchService searchService = mock(SearchService.class);
        houseService = mock(HouseService.class);
        kbService = mock(KbService.class);
        toolTraceService = mock(ToolTraceService.class);
        HousingTools tools = new HousingTools(searchService, houseService, kbService, new ObjectMapper());
        provider = new HousingToolProvider(tools, toolTraceService, new ObjectMapper());
        when(kbService.search(anyString(), anyInt())).thenReturn(List.of(Map.of("title", "押金规则")));
    }

    /** 取指定工具的执行器：{@code provideTools} 一次性注册全部 @Tool 方法，按名字挑一个即可 */
    private ToolExecutor executor(String name) {
        return provider.provideTools(new ToolProviderRequest(SESSION, UserMessage.from("帮我看看")))
                .tools().entrySet().stream()
                .filter(e -> name.equals(e.getKey().name()))
                .map(Map.Entry::getValue)
                .findFirst().orElseThrow(() -> new AssertionError("未注册的工具：" + name));
    }

    private static ToolExecutionRequest call(String name, String argumentsJson) {
        return ToolExecutionRequest.builder().id("call_1").name(name).arguments(argumentsJson).build();
    }

    @Test
    @DisplayName("UT-TOOL-01 工具调用超上限：先给一次终止提示，仍不收敛即抛错硬停本轮")
    void abortsRoundWhenToolLoopDoesNotConverge() {
        provider.openRound(SESSION, VIEWER);
        ToolExecutor knowledge = executor("searchKnowledge");
        ToolExecutionRequest request = call("searchKnowledge", "{\"question\":\"押金怎么退\"}");

        for (int i = 0; i < HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND; i++) {
            knowledge.execute(request, SESSION);
        }
        // 超出上限的第一次：返回终止提示（合规模型据此立即作答），本次工具不再执行
        String hint = knowledge.execute(request, SESSION);
        assertTrue(hint.contains("已达上限"), hint);
        // 提示之后还要调工具＝本轮没收敛：只能抛错结束循环
        assertThrows(ToolCallLimitExceededException.class, () -> knowledge.execute(request, SESSION));

        // 三次终态都留痕：正常执行、终止提示、硬停（后台审计据此回放工具链）
        verify(toolTraceService, times(HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND + 2))
                .record(eq(SESSION), eq("searchKnowledge"), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("UT-TOOL-02 closeRound 整体移除会话态：收尾后不再残留（计数不带到下一轮，也不随会话数增长）")
    void closeRoundDropsRoundState() {
        provider.openRound(SESSION, VIEWER);
        ToolExecutor knowledge = executor("searchKnowledge");
        ToolExecutionRequest request = call("searchKnowledge", "{\"question\":\"押金\"}");
        for (int i = 0; i <= HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND; i++) {
            knowledge.execute(request, SESSION);
        }

        provider.closeRound(SESSION);
        assertFalse(provider.isCancelled(SESSION), "收尾后不得还留着本轮的取消标记");
        // 若会话态没被移除，这一次会是超上限的第 N+1 次调用而直接抛错；移除后是全新的一轮
        assertDoesNotThrow(() -> knowledge.execute(request, SESSION));
    }

    @Test
    @DisplayName("UT-TOOL-03 取消本轮后工具不再执行（返回取消体），且一直调用仍会被上限硬停")
    void cancelledRoundStopsExecutingTools() {
        provider.openRound(SESSION, VIEWER);
        provider.cancelRound(SESSION);
        ToolExecutor knowledge = executor("searchKnowledge");
        ToolExecutionRequest request = call("searchKnowledge", "{\"question\":\"押金\"}");

        String cancelled = knowledge.execute(request, SESSION);
        assertTrue(cancelled.contains("已取消"), cancelled);
        verify(kbService, never()).search(anyString(), anyInt());

        // 取消不是"随便转"：计数照走，超上限仍未收敛同样硬停
        assertThrows(ToolCallLimitExceededException.class, () -> {
            for (int i = 0; i <= HousingToolProvider.MAX_TOOL_CALLS_PER_ROUND; i++) {
                knowledge.execute(request, SESSION);
            }
        });
    }

    @Test
    @DisplayName("UT-TOOL-04 知识库引用与本轮工具集：取走即清空，同一轮不会重复上报")
    void takesAndClearsRoundOutputs() {
        provider.openRound(SESSION, VIEWER);
        executor("searchKnowledge").execute(call("searchKnowledge", "{\"question\":\"押金\"}"), SESSION);

        String citations = provider.takeKnowledgeCitations(SESSION);
        assertNotNull(citations);
        assertTrue(citations.contains("押金规则"), citations);
        assertNull(provider.takeKnowledgeCitations(SESSION), "取走后不得再取到同一份引用");
        assertEquals(Set.of("searchKnowledge"), provider.takeRoundTools(SESSION));
        assertTrue(provider.takeRoundTools(SESSION).isEmpty(), "工具集取走后必须清空");
    }

    @Test
    @DisplayName("UT-TOOL-05 工具以本轮绑定的浏览身份执行；收尾后回到匿名（身份不跨轮残留）")
    void executesToolsWithRoundViewer() {
        when(houseService.detail(eq(1L), any())).thenReturn(
                new HouseDto.Item(new House(), List.of(), "房东", false, 0L));
        ToolExecutor detail = executor("getHouseDetail");
        ToolExecutionRequest request = call("getHouseDetail", "{\"houseId\":1}");

        provider.openRound(SESSION, VIEWER);
        detail.execute(request, SESSION);
        verify(houseService).detail(1L, VIEWER);

        provider.closeRound(SESSION);
        detail.execute(request, SESSION);
        verify(houseService).detail(1L, null);
    }

    @Test
    @DisplayName("UT-TOOL-06 工具参数非法等意外异常：包成中止标记上抛并留痕（带 tool_calls 的助手消息已入记忆，须触发清空）")
    void wrapsUnexpectedToolPhaseFailureAsAbort() {
        provider.openRound(SESSION, VIEWER);
        ToolExecutor knowledge = executor("searchKnowledge");
        // 参数 JSON 非法：DefaultToolExecutor 的参数解析在其异常保护区之外，异常直接冒出
        ToolExecutionRequest malformed = call("searchKnowledge", "{\"question\":");

        ToolRoundAbortedException thrown = assertThrows(ToolRoundAbortedException.class,
                () -> knowledge.execute(malformed, SESSION));
        assertFalse(thrown instanceof ToolCallLimitExceededException, "意外失败不是超上限，不得混用中止原因");
        assertNotNull(thrown.getCause(), "必须保留原始异常便于排障");
        // 工具方法根本没机会执行；失败已留痕（后台审计据此回放故障点）
        verify(kbService, never()).search(anyString(), anyInt());
        verify(toolTraceService).record(eq(SESSION), eq("searchKnowledge"), eq(malformed.arguments()),
                anyString(), anyInt());
    }
}
