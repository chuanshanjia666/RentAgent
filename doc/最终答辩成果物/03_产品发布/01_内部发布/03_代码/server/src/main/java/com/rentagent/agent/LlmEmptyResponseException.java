package com.rentagent.agent;

/**
 * 模型响应既无正文也无工具调用（典型成因：推理内容占满 max_tokens、流被网关截断）。
 * <p>
 * 这是传输/预算层面的失败，**不是模型的结论**：既不能当作回答展示，也不允许伪造一句
 * "没有生成有效回答"糊弄过去——本系统不提供任何本地兜底回答（2026-09-17 口径）。
 * 抛出后由 {@link LlmAgent} 决定按可重试故障重跑一次，或如实通知用户重试。
 */
class LlmEmptyResponseException extends RuntimeException {

    LlmEmptyResponseException(String message) {
        super(message);
    }
}
