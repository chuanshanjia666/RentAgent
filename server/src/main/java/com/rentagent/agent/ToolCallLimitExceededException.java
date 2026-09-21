package com.rentagent.agent;

/**
 * 单轮工具调用次数超上限、且在给出终止提示后模型**仍在**发起工具调用（本轮未收敛）。
 * <p>
 * 这是唯一能终止流式工具循环的手段：langchain4j 0.35 的 {@code AiServiceStreamingResponseHandler}
 * 收到带工具调用的响应后一定会再叫一次模型，且模型与执行器在它构造时就已定死
 * （{@code provideTools} 只在本轮开始时调用一次，改不了后续轮次的工具集），
 * 因此"返回一段终止提示文本"拦不住这个循环，只有异常能穿透回 {@code onError} 结束本轮。
 * <p>
 * 抛出后的处理：{@link LlmAgent} 如实上报错误（不伪造任何回答），并按
 * {@link ToolRoundAbortedException} 的口径清掉本轮会话记忆——助手消息已带着 {@code tool_calls}
 * 进了记忆，而被中止的这一次工具调用不会再有对应的 {@code tool} 结果。
 */
public class ToolCallLimitExceededException extends ToolRoundAbortedException {

    public ToolCallLimitExceededException(String message) {
        super(message, null);
    }
}
