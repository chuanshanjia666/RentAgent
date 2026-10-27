package com.rentagent.config;

import com.rentagent.agent.EmbeddingGateway;
import com.rentagent.agent.EmbeddingModelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 词嵌入客户端配置（RAG 知识库语义检索基础设施）：
 * 优先注入支持动态配置与多级回落的 {@link EmbeddingGateway}。
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean(EmbeddingModelClient.class)
    public EmbeddingModelClient embeddingModelClient(EmbeddingGateway embeddingGateway) {
        return embeddingGateway;
    }
}
