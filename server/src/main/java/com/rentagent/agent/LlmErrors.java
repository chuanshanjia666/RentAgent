package com.rentagent.agent;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 适配器异步回调的异常规整。
 * <p>
 * 三个适配器都在 {@code sendAsync(...).thenAccept(...)} 的回调里解析分片，回调中抛出的异常会被
 * CompletableFuture 包成 {@link CompletionException}；不剥掉这层，下游（重试判定、日志、SSE 提示）
 * 看到的都只是一句 "java.util.concurrent.CompletionException"，真实原因（限流/空返回/断连）被藏住。
 */
final class LlmErrors {

    private LlmErrors() {
    }

    /** 剥掉 CompletionException / ExecutionException 外壳，返回最内层的真实异常 */
    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
