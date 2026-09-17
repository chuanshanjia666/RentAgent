package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具提供器（NFR-05 可追溯）：注册方式等价于 {@code AiServices.tools(housingTools)}，
 * 差别在于每次工具执行都被包装——记录工具名/入参/返回/耗时后落库，
 * 后台"AI 对话审计"据此回放工具链。
 * <p>
 * 放在提供器而非工具方法内部，是为了让 {@link HousingTools} 新增 @Tool 方法时
 * 自动获得留痕能力（NFR-09 可扩展），无需逐个改造。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousingToolProvider implements ToolProvider {

    private final HousingTools housingTools;
    private final ToolTraceService toolTraceService;
    private final ObjectMapper objectMapper;

    /** @Tool 方法清单在启动后不变，只反射一次（按方法名排序，保证注册顺序稳定） */
    private volatile List<Method> toolMethods;

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

    /** 执行工具并留痕；工具抛异常时同样留痕（记 error），再向上抛给 LangChain4j 的容错处理 */
    private String executeTraced(ToolExecutor delegate, ToolExecutionRequest req, Object memoryId) {
        long start = System.currentTimeMillis();
        long sessionId = sessionOf(memoryId);
        try {
            String result = delegate.execute(req, memoryId);
            toolTraceService.record(sessionId, req.name(), req.arguments(), result, elapsed(start));
            return result;
        } catch (RuntimeException e) {
            toolTraceService.record(sessionId, req.name(), req.arguments(), errorJson(e), elapsed(start));
            throw e;
        }
    }

    private List<Method> methods() {
        if (toolMethods == null) {
            synchronized (this) {
                if (toolMethods == null) {
                    toolMethods = java.util.Arrays.stream(HousingTools.class.getMethods())
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
