package com.rentagent.agent;

/**
 * 智能体引擎抽象（NFR-09 可插拔）：当前唯一实现为 {@link LlmAgent}（真实大模型 + Function Calling 工具）。
 * 上层 ChatService 面向本接口编程，新增智能体能力无需改动对话主流程。
 * <p>
 * 本系统不提供"无模型时"的本地规则引擎兜底：{@link #available()} 为 false 时上层直接报错（4001）。
 */
public interface AgentEngine {

    boolean available();

    void stream(long sessionId, long userId, int scene, String userMessage, Callback callback);

    interface Callback {

        void onToken(String token);

        /** @param citationsJson RAG 引用（JSON 数组字符串，可为 null） */
        void onComplete(String fullText, String citationsJson, boolean transferred);

        /** 模型上报的 token 用量（NFR-05 留痕用） */
        default void onUsage(Integer totalTokens) {
        }

        void onError(Throwable t);
    }
}
