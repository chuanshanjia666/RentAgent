package com.rentagent.agent;

/**
 * 智能体引擎抽象（NFR-09 可插拔）：GlmAgent（真模型）与 MockAgent（规则引擎）双实现，
 * 上层 ChatService 面向本接口编程，新增智能体能力无需改动对话主流程。
 */
public interface AgentEngine {

    boolean available();

    void stream(long sessionId, long userId, int scene, String userMessage, Callback callback);

    interface Callback {

        void onToken(String token);

        /** @param citationsJson RAG 引用（JSON 数组字符串，可为 null） */
        void onComplete(String fullText, String citationsJson, boolean transferred);

        void onError(Throwable t);
    }
}
