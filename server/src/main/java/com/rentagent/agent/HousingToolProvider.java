package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.security.UserContext;
import com.rentagent.service.ToolTraceService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具提供器（NFR-05 可追溯）：注册方式等价于 {@code AiServices.tools(housingTools)}，
 * 差别在于每次工具执行都被包装——记录工具名/入参/返回/耗时后落库，
 * 后台"AI 对话审计"据此回放工具链。
 * <p>
 * 放在提供器而非工具方法内部，是为了让 {@link HousingTools} 新增 @Tool 方法时
 * 自动获得留痕能力（NFR-09 可扩展），无需逐个改造。
 */
@Component
@RequiredArgsConstructor
public class HousingToolProvider implements ToolProvider {

    private final HousingTools housingTools;
    private final ToolTraceService toolTraceService;
    private final ObjectMapper objectMapper;

    /** @Tool 方法清单在启动后不变，只反射一次（按方法名排序，保证注册顺序稳定） */
    private volatile List<Method> toolMethods;

    /**
     * 单轮对话允许的工具调用次数上限。
     * <p>
     * 必须自己设：流式路径的"模型 → 工具 → 再叫模型"循环由 langchain4j 的
     * {@code AiServiceStreamingResponseHandler} 递归驱动，**没有**任何轮次上限
     * （{@code MAX_SEQUENTIAL_TOOL_EXECUTIONS=100} 只存在于非流式路径），
     * 模型反复发工具调用就是无限循环，每轮一次上游调用。超过上限后返回终止提示，
     * 让模型依据已有结果作答，保证本轮一定收敛。
     */
    private static final int MAX_TOOL_CALLS_PER_ROUND = 8;

    /** 达到调用上限后的工具返回体：要求模型立即收敛，别继续调工具 */
    private static final String TOOL_LIMIT_REACHED =
            "{\"error\":\"本轮工具调用次数已达上限\",\"hint\":\"请立即依据已取得的工具结果作答；"
            + "结果不足时如实说明，不要编造房源或平台政策\"}";

    /** 本轮已取消（客户端断开或 SSE 超时）后的工具返回体 */
    private static final String ROUND_CANCELLED =
            "{\"error\":\"本轮请求已取消\",\"hint\":\"请直接结束本轮，不要再调用任何工具\"}";

    /**
     * 一轮对话的会话态：浏览身份 + 工具调用计数 + 本轮工具集 + 知识库引用 + 取消标记。
     * <p>
     * 这四者在同一轮里同时生效，故合并成一个对象按 sessionId 存放，
     * 由 {@link #openRound} 在受理消息时整体重置，避免各状态之间出现"清了一个漏了另一个"。
     */
    private static final class Round {
        private final UserContext.User viewer;
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final Set<String> tools = ConcurrentHashMap.newKeySet();
        private volatile String citations;
        private volatile boolean cancelled;

        private Round(UserContext.User viewer) {
            this.viewer = viewer;
        }
    }

    private final Map<Long, Round> rounds = new ConcurrentHashMap<>();

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        for (Method method : methods()) {
            ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
            ToolExecutor delegate = new DefaultToolExecutor(housingTools, method);
            tools.put(spec, (req, memoryId) -> executeTraced(delegate, req, memoryId));
        }
        return new ToolProviderResult(tools);
    }

    /**
     * 开启新一轮对话：绑定浏览身份并整体重置本轮状态（由 {@code ChatService} 在受理消息时调用）。
     * 身份来自受理请求的 Servlet 线程（那里才有 {@link UserContext}），工具执行线程上取不到。
     */
    public void openRound(long sessionId, UserContext.User viewer) {
        rounds.put(sessionId, new Round(viewer));
    }

    /**
     * 标记本轮已取消（客户端断开 / SSE 超时）。取消**不会中断**正在途中的那次上游请求
     * （HTTP 客户端层没有取消通道），但能让后续工具轮次与兜底重跑立刻停止，不再继续烧 token。
     */
    public void cancelRound(long sessionId) {
        Round round = rounds.get(sessionId);
        if (round != null) {
            round.cancelled = true;
        }
    }

    /** 本轮是否已取消：{@link LlmAgent} 据此跳过非流式兜底重跑 */
    public boolean isCancelled(long sessionId) {
        Round round = rounds.get(sessionId);
        return round != null && round.cancelled;
    }

    /**
     * 执行工具并留痕；工具抛异常时同样留痕（记 error），再向上抛给 LangChain4j 的容错处理。
     * <p>
     * 执行前先做三件事：计数（超上限即返回终止提示，保证循环收敛）、
     * 取消检查（客户端已走就不再干活）、绑定浏览身份（工具线程没有 UserContext）。
     */
    private String executeTraced(ToolExecutor delegate, ToolExecutionRequest req, Object memoryId) {
        long start = System.currentTimeMillis();
        long sessionId = sessionOf(memoryId);
        Round round = sessionId > 0 ? rounds.computeIfAbsent(sessionId, k -> new Round(null)) : null;
        if (round != null) {
            // 计数在返回前先加：取消也要受上限约束，否则模型持续发工具调用时循环仍然不收敛
            if (round.toolCalls.incrementAndGet() > MAX_TOOL_CALLS_PER_ROUND) {
                toolTraceService.record(sessionId, req.name(), req.arguments(), TOOL_LIMIT_REACHED, elapsed(start));
                return TOOL_LIMIT_REACHED;
            }
            if (round.cancelled) {
                return ROUND_CANCELLED;
            }
            round.tools.add(req.name());
        }
        try {
            String result = HousingTools.withViewer(round == null ? null : round.viewer,
                    () -> delegate.execute(req, memoryId));
            if (round != null && "searchKnowledge".equals(req.name()) && result != null) {
                round.citations = result;
            }
            toolTraceService.record(sessionId, req.name(), req.arguments(), result, elapsed(start));
            return result;
        } catch (RuntimeException e) {
            toolTraceService.record(sessionId, req.name(), req.arguments(), errorJson(e), elapsed(start));
            throw e;
        }
    }

    /** 取走本会话最近一次知识库引用（读取即清除，不在内存里长期驻留） */
    public String takeKnowledgeCitations(long sessionId) {
        Round round = rounds.get(sessionId);
        if (round == null) {
            return null;
        }
        String citations = round.citations;
        round.citations = null;
        return citations;
    }

    /** 取走本轮已执行的工具名（读取即清除，与知识库引用同生命周期）；未执行任何工具返回空集合 */
    public Set<String> takeRoundTools(long sessionId) {
        Round round = rounds.get(sessionId);
        if (round == null) {
            return Set.of();
        }
        Set<String> tools = Set.copyOf(round.tools);
        round.tools.clear();
        return tools;
    }

    private List<Method> methods() {
        if (toolMethods == null) {
            synchronized (this) {
                if (toolMethods == null) {
                    toolMethods = Arrays.stream(HousingTools.class.getMethods())
                            .filter(m -> m.isAnnotationPresent(Tool.class))
                            .sorted(Comparator.comparing(Method::getName))
                            .toList();
                }
            }
        }
        return toolMethods;
    }

    /** @MemoryId 传下来的是会话 id（装箱 Long） */
    private long sessionOf(Object memoryId) {
        return memoryId instanceof Number n ? n.longValue() : 0L;
    }

    private int elapsed(long start) {
        return (int) (System.currentTimeMillis() - start);
    }

    private String errorJson(RuntimeException e) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", String.valueOf(e.getMessage())));
        } catch (Exception ignore) {
            return null;
        }
    }
}
