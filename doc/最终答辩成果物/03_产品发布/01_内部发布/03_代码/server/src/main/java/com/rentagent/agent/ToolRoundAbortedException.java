package com.rentagent.agent;

/**
 * 本轮在工具阶段异常终止：会话记忆里可能已留下"带着 {@code tool_calls} 却没有对应工具结果"的助手消息。
 * <p>
 * langchain4j 的处理器（流式与非流式同）先把助手消息写入记忆、再执行工具，二者之间没有容错：
 * 执行一抛错，残缺就已铸成。这类消息会被 OpenAI / Anthropic 协议以 400 拒绝，留在记忆里会让该会话
 * 后续每一轮都失败，因此 {@link LlmAgent} 上报本异常前会先整体清空该会话记忆——丢一次上下文，
 * 好过会话从此不可用。
 * <p>
 * 两个来源：{@link ToolCallLimitExceededException}（超上限未收敛，主动硬停）与工具执行阶段的意外异常
 * （如模型发来的工具参数 JSON 非法——{@code DefaultToolExecutor} 的参数解析在其异常保护区之外，
 * 异常会直接冒出）。注意 {@code @Tool} 方法自身抛的业务异常**不在**此列：langchain4j 会把它吞成
 * 工具结果字符串回给模型，记忆不残缺，也走不到这里。
 */
public class ToolRoundAbortedException extends RuntimeException {

    public ToolRoundAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
