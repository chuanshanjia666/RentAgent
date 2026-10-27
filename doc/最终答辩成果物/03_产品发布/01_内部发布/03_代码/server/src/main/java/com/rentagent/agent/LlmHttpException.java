package com.rentagent.agent;

/** LLM 端点返回非 2xx 时的异常：带状态码，调用方据此区分"参数不被支持"与"网络/限流类故障" */
class LlmHttpException extends RuntimeException {

    private final int status;

    LlmHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }

    /** 限流、超时与 5xx 属于可能自愈的传输层故障；4xx（鉴权失败、参数不被支持）重跑没有意义 */
    boolean retryable() {
        return status == 408 || status == 429 || status >= 500;
    }
}
