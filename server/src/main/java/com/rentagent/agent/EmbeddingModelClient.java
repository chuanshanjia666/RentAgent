package com.rentagent.agent;

import java.util.List;

/**
 * 文本向量化嵌入（Embedding）模型客户端接口（RAG 知识库语义检索能力支撑）
 */
public interface EmbeddingModelClient {

    /**
     * 为单个文本计算稠密嵌入向量
     *
     * @param text 输入文本
     * @return 浮点数向量数组；若输入为空或失败返回空数组
     */
    float[] embed(String text);

    /**
     * 批量计算稠密嵌入向量
     *
     * @param texts 输入文本列表
     * @return 向量列表，顺序与输入列表保持一致
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 客户端是否就绪（已配置有效基址与 API Key）
     */
    boolean available();
}

