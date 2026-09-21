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
import java.util.concurrent.atomic.AtomicBoolean;
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
     * 模型反复发工具调用就是无限循环，每轮一次上游调用。
     * <p>
     * 收敛分两步（见 {@link #executeTraced}）：超上限先返回一次终止提示让模型依据已有结果作答，
     * 合规模型到此为止；提示之后**还**要调工具就是真没收敛，此时只能抛
     * {@link ToolCallLimitExceededException} 硬停——返回文本拦不住上述循环，只有异常能终止它。
     * <p>
     * public 是给 {@code ChatService} 推算 SSE 超时预算用（一轮最坏耗时按本上限推导）。
     */
    public static final int MAX_TOOL_CALLS_PER_ROUND = 8;

    /** 达到调用上限后的工具返回体：要求模型立即收敛，别继续调工具（只给一次，再调即硬停） */
    private static final String TOOL_LIMIT_REACHED =
            "{\"error\":\"本轮工具调用次数已达上限\",\"hint\":\"请立即依据已取得的工具结果作答；"
            + "结果不足时如实说明，不要编造房源或平台政策\"}";

    /** 本轮已取消（客户端断开或 SSE 超时）后的工具返回体 */
    private static final String ROUND_CANCELLED =
            "{\"error\":\"本轮请求已取消\",\"hint\":\"请直接结束本轮，不要再调用任何工具\"}";

    /**
     * 一轮对话的会话态：浏览身份 + 工具调用计数 + 本轮工具集 + 知识库引用 + 取消/收敛标记。
     * <p>
     * 这些状态在同一轮里同时生效，故合并成一个对象按 sessionId 存放，
     * 由 {@link #openRound} 在受理消息时整体重置、{@link #closeRound} 在本轮收尾时整体移除，
     * 避免各状态之间出现"清了一个漏了另一个"。
     */
    private static final class Round {
        private final UserContext.User viewer;
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final Set<String> tools = ConcurrentHashMap.newKeySet();
        /** 超上限的终止提示是否已给过：给过之后仍然要调工具即判未收敛，直接硬停 */
        private final AtomicBoolean limitHintGiven = new AtomicBoolean();
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

    /**
     * 本轮收尾：整体移除会话态（浏览身份 / 工具调用计数 / 本轮工具集 / 知识库引用）。
     * <p>
     * 由 {@code ChatService} 在本轮的两个终止回调（{@code onComplete} / {@code onError}）里调用，
     * 那时工具轮次与兜底重跑都已结束、不会再有人读这些状态。不移除的话 map 会随"用过的会话数"一直涨，
     * 每个条目还攥着一份 {@link UserContext.User} 与引用文本直到进程结束。
     * <p>
     * 前提与 {@link #cancelRound} 相同：同一会话同一时刻只有一轮在跑（前端在流式期间禁用发送按钮）。
     * 若同一会话真并发跑起第二轮，第一轮的收尾会连第二轮的会话态一并清掉——两处都按会话定位，
     * 不做轮次代号校验。
     */
    public void closeRound(long sessionId) {
        rounds.remove(sessionId);
    }

    /** 本轮是否已取消：{@link LlmAgent} 据此跳过非流式兜底重跑 */
    public boolean isCancelled(long sessionId) {
        Round round = rounds.get(sessionId);
        return round != null && round.cancelled;
    }

    /**
     * 执行工具并留痕。
     * <p>
     * 执行前先做三件事：计数与收敛检查（超上限先给一次终止提示，再超即抛错硬停）、
     * 取消检查（客户端已走就不再干活）、绑定浏览身份（工具线程没有 UserContext）。
     * <p>
     * 异常分两类，出路完全不同：{@code @Tool} 方法自身抛的业务异常不会向上抛——langchain4j 的
     * {@code DefaultToolExecutor} 把它吞成工具结果字符串回给模型（本轮继续，记忆不残缺）；
     * 而参数解析、反射准备这类在 {@code DefaultToolExecutor} 异常保护区**之外**的意外异常会冒出本方法，
     * 此时带 {@code tool_calls} 的助手消息已入记忆而结果不会再有，故包成
     * {@link ToolRoundAbortedException} 上抛，让 {@code LlmAgent} 清掉这段残缺记忆。
     */
    private String executeTraced(ToolExecutor delegate, ToolExecutionRequest req, Object memoryId) {
        long start = System.currentTimeMillis();
        long sessionId = sessionOf(memoryId);
        Round round = sessionId > 0 ? rounds.computeIfAbsent(sessionId, k -> new Round(null)) : null;
        if (round != null && round.toolCalls.incrementAndGet() > MAX_TOOL_CALLS_PER_ROUND) {
            // 计数在判上限之前先加：取消也要受上限约束，否则模型持续发工具调用时循环仍然不收敛
            if (round.limitHintGiven.compareAndSet(false, true)) {
                // 超上限先给一次终止提示（本次工具不再执行）：合规模型据此立即依据已有结果作答
                toolTraceService.record(sessionId, req.name(), req.arguments(), TOOL_LIMIT_REACHED, elapsed(start));
                return TOOL_LIMIT_REACHED;
            }
            // 提示已给过还要调工具＝本轮没收敛：只能抛错结束循环（返回文本拦不住 langchain4j 的递归调用）
            ToolCallLimitExceededException exceeded = new ToolCallLimitExceededException(
                    "本轮工具调用已超过上限 " + MAX_TOOL_CALLS_PER_ROUND + " 次且仍在调用 " + req.name() + "，已中止本轮");
            toolTraceService.record(sessionId, req.name(), req.arguments(), errorJson(exceeded), elapsed(start));
            throw exceeded;
        }
        if (round != null) {
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
            // 意外异常（参数 JSON 非法等）：结果不会再有，记忆已残缺，标记中止让 LlmAgent 清掉它
            throw new ToolRoundAbortedException(
                    "工具 " + req.name() + " 执行失败，本轮中止：" + e.getMessage(), e);
        }
    }

    /** 取走本会话最近一次知识库引用（取走即置空，引用文本不再滞留在会话态里） */
    public String takeKnowledgeCitations(long sessionId) {
        Round round = rounds.get(sessionId);
        if (round == null) {
            return null;
        }
        String citations = round.citations;
        round.citations = null;
        return citations;
    }

    /**
     * 取走本轮已执行的工具名（取走即清空，与知识库引用同生命周期）；未执行任何工具返回空集合。
     * <p>
     * 清空的是本轮的**工具集**，不是本轮会话态——会话态由 {@link #closeRound} 在本轮收尾时整体移除。
     */
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
