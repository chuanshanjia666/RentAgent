package com.rentagent.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.entity.AiProviderConfig;
import com.rentagent.mapper.AiProviderConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingGatewayTest {

    @Mock
    private AiProviderConfigMapper configMapper;

    private AiProps props;
    private EmbeddingGateway gateway;

    @BeforeEach
    void setUp() {
        props = new AiProps();
        gateway = new EmbeddingGateway(props, configMapper);
    }

    @Test
    @DisplayName("UT-EMBGW-01 优先读取专用配置中的 Embedding 凭据")
    void prefersDedicatedEmbeddingConfig() {
        props.getEmbedding().setBaseUrl("http://dedicated-embed.com/v1");
        props.getEmbedding().setApiKey("sk-dedicated-key");
        props.getEmbedding().setModel("bge-m3");

        assertTrue(gateway.available());
        EmbeddingGateway.ResolvedConfig cfg = gateway.resolvedConfig();
        assertNotNull(cfg);
        assertEquals("http://dedicated-embed.com/v1", cfg.baseUrl());
        assertEquals("sk-dedicated-key", cfg.apiKey());
        assertEquals("bge-m3", cfg.model());
        assertTrue(gateway.describe().contains("CONFIG(dedicated)"));
    }

    @Test
    @DisplayName("UT-EMBGW-02 无专用配置时，与供应商逻辑一致从数据库读取激活配置")
    void fallbackToDatabaseActiveProvider() {
        props.getEmbedding().setApiKey(""); // 无专用凭据

        AiProviderConfig active = new AiProviderConfig();
        active.setName("大工网关");
        active.setBaseUrl("http://aigw.dlut.edu.cn/v1");
        active.setApiKey("sk-db-key");
        active.setModel("DeepSeek-V4");
        active.setIsActive(1);

        when(configMapper.selectOne(any())).thenReturn(active);

        assertTrue(gateway.available());
        EmbeddingGateway.ResolvedConfig cfg = gateway.resolvedConfig();
        assertNotNull(cfg);
        assertEquals("http://aigw.dlut.edu.cn/v1", cfg.baseUrl());
        assertEquals("sk-db-key", cfg.apiKey());
        assertEquals("bge-m3", cfg.model());
        assertTrue(gateway.describe().contains("DATABASE(大工网关)"));
    }

    @Test
    @DisplayName("UT-EMBGW-03 数据库无激活项时，回落到配置文件/环境变量中生效的后端凭据")
    void fallbackToActiveBackend() {
        props.getEmbedding().setApiKey("");
        when(configMapper.selectOne(any())).thenReturn(null);

        AiProps.Backend backend = new AiProps.Backend();
        backend.setProtocol("openai-chat-completions");
        backend.setBaseUrl("http://env-backend.com/v1");
        backend.setApiKey("sk-env-key");
        backend.setModel("deepseek-v4");
        props.setBackends(Map.of("default-backend", backend));
        props.setActive("default-backend");

        assertTrue(gateway.available());
        EmbeddingGateway.ResolvedConfig cfg = gateway.resolvedConfig();
        assertNotNull(cfg);
        assertEquals("http://env-backend.com/v1", cfg.baseUrl());
        assertEquals("sk-env-key", cfg.apiKey());
        assertTrue(gateway.describe().contains("BACKEND"));
    }

    @Test
    @DisplayName("UT-EMBGW-04 各层均无有效凭据时 available 为 false，平滑降级")
    void unavailableWhenNoKey() {
        props.getEmbedding().setApiKey("");
        when(configMapper.selectOne(any())).thenReturn(null);

        assertFalse(gateway.available());
        assertEquals("none", gateway.describe());
        assertEquals(0, gateway.embed("测试").length);
        assertTrue(gateway.embedBatch(java.util.List.of("测试")).isEmpty());
    }

    @Test
    @DisplayName("UT-EMBGW-05 感知 AiConfigChangedEvent 事件时热重载刷新配置")
    void refreshesOnConfigChangedEvent() {
        props.getEmbedding().setApiKey("");

        AiProviderConfig active1 = new AiProviderConfig();
        active1.setName("网关1");
        active1.setBaseUrl("http://gw1.com/v1");
        active1.setApiKey("sk-key1");
        when(configMapper.selectOne(any())).thenReturn(active1);

        assertEquals("http://gw1.com/v1", gateway.resolvedConfig().baseUrl());

        // 模拟切换供应商
        AiProviderConfig active2 = new AiProviderConfig();
        active2.setName("网关2");
        active2.setBaseUrl("http://gw2.com/v1");
        active2.setApiKey("sk-key2");
        when(configMapper.selectOne(any())).thenReturn(active2);

        // 触发事件
        gateway.onConfigChanged(new AiConfigChangedEvent(2L, "activate"));

        assertEquals("http://gw2.com/v1", gateway.resolvedConfig().baseUrl());
        assertEquals("sk-key2", gateway.resolvedConfig().apiKey());
    }
}
