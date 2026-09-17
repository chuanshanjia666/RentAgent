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
}
