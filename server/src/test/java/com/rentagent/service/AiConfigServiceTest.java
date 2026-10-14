package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rentagent.agent.LlmGateway;
import com.rentagent.common.BizException;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.dto.AdminDto;
import com.rentagent.entity.AiProviderConfig;
import com.rentagent.mapper.AiProviderConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiConfigServiceTest {

    @Mock
    private AiProviderConfigMapper mapper;
    @Mock
    private LlmGateway llmGateway;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AiProps props;
    private AiConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        com.rentagent.support.EntityMetadataHelper.init(AiProviderConfig.class);
        props = new AiProps();
        props.setProtocol("openai-chat-completions");
        props.setBaseUrl("https://api.openai.com/v1");
        props.setApiKey("file-key");
        props.setModel("gpt-4.1-mini");
        service = new AiConfigServiceImpl(mapper, props, llmGateway, auditLogService, eventPublisher, jdbcTemplate);
    }

    @Test
    @DisplayName("UT-AICONFIG-01 概览优先展示数据库中激活的供应商")
    void overviewPrefersDatabaseActiveConfig() {
        AiProviderConfig active = new AiProviderConfig();
        active.setId(10L);
        active.setName("DeepSeek 官方");
        active.setProtocol("openai-chat-completions");
        active.setBaseUrl("https://api.deepseek.com/v1");
        active.setApiKey("sk-123456789abcdef");
        active.setModel("deepseek-chat");
        active.setIsActive(1);

        when(mapper.selectList(any())).thenReturn(List.of(active));

        AdminDto.AiConfigOverviewVO overview = service.getOverview();
        assertEquals("DATABASE", overview.activeSource());
        assertEquals("openai-chat-completions:deepseek-chat", overview.activeDescribe());
        assertEquals(10L, overview.activeConfigId());
        assertEquals(1, overview.configs().size());
        assertTrue(overview.configs().get(0).maskedApiKey().contains("****"));
    }

    @Test
    @DisplayName("UT-AICONFIG-02 数据库无激活配置时回退展示配置文件来源")
    void overviewFallsBackToConfigFileWhenDbEmpty() {
        when(mapper.selectList(any())).thenReturn(List.of());
        when(llmGateway.available()).thenReturn(true);
        when(llmGateway.describe()).thenReturn("openai-chat-completions:gpt-4.1-mini");

        AdminDto.AiConfigOverviewVO overview = service.getOverview();
        assertEquals("CONFIG_FILE", overview.activeSource());
        assertEquals("openai-chat-completions:gpt-4.1-mini", overview.activeDescribe());
        assertNull(overview.activeConfigId());
    }

    @Test
    @DisplayName("UT-AICONFIG-03 新增配置若设为激活，需清空其余激活状态并派发热更事件")
    void saveActiveConfigResetsOthersAndPublishesEvent() {
        AdminDto.AiConfigSaveReq req = new AdminDto.AiConfigSaveReq(
                "通义千问", "openai-chat-completions", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-qwen12345678", "qwen-plus", 0.7, 4096, 60, null, "{\"x-custom\":\"test\"}", true
        );

        AdminDto.AiConfigVO vo = service.save(req, 1L);
        assertNotNull(vo);
        assertEquals("通义千问", vo.name());
        assertEquals(1, vo.isActive());
        assertEquals("{\"x-custom\":\"test\"}", vo.customHeaders());

        verify(mapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(mapper).insert(any(AiProviderConfig.class));
        verify(eventPublisher).publishEvent(any(AiConfigChangedEvent.class));
    }

    @Test
    @DisplayName("UT-AICONFIG-04 新增配置缺少 API Key 时拒绝保存")
    void saveRejectsMissingApiKey() {
        AdminDto.AiConfigSaveReq req = new AdminDto.AiConfigSaveReq(
                "测试", "openai-chat-completions", "https://api.test.com",
                "", "test-model", 0.7, 4096, 60, null, null, false
        );

        assertThrows(BizException.class, () -> service.save(req, 1L));
    }

    @Test
    @DisplayName("UT-AICONFIG-05 编辑配置未提供新 Key 时保留原数据库中 Key")
    void updateRetainsExistingApiKeyWhenBlank() {
        AiProviderConfig existing = new AiProviderConfig();
        existing.setId(20L);
        existing.setName("旧名称");
        existing.setProtocol("openai-chat-completions");
        existing.setBaseUrl("https://api.old.com");
        existing.setApiKey("sk-secret-keep-this");
        existing.setModel("old-model");
        existing.setIsActive(0);

        when(mapper.selectById(20L)).thenReturn(existing);

        AdminDto.AiConfigSaveReq req = new AdminDto.AiConfigSaveReq(
                "新名称", "openai-chat-completions", "https://api.new.com",
                "", "new-model", 0.5, 2048, 45, null, "{\"x-session\":\"123\"}", false
        );

        service.update(20L, req, 1L);

        assertEquals("sk-secret-keep-this", existing.getApiKey());
        assertEquals("新名称", existing.getName());
        assertEquals("https://api.new.com", existing.getBaseUrl());
        assertEquals("{\"x-session\":\"123\"}", existing.getCustomHeaders());
        verify(mapper).updateById(existing);
    }

    @Test
    @DisplayName("UT-AICONFIG-06 激活配置将自身置为1并重置其他项")
    void activateSetsCurrentAndClearsOthers() {
        AiProviderConfig target = new AiProviderConfig();
        target.setId(30L);
        target.setName("备用供应商");
        target.setApiKey("sk-key-30");
        target.setIsActive(0);

        when(mapper.selectById(30L)).thenReturn(target);

        service.activate(30L, 1L);

        verify(mapper).update(any(), any(LambdaUpdateWrapper.class));
        assertEquals(1, target.getIsActive());
        verify(mapper).updateById(target);
        verify(eventPublisher).publishEvent(any(AiConfigChangedEvent.class));
    }

    @Test
    @DisplayName("UT-AICONFIG-07 禁止删除当前生效中的供应商配置")
    void deleteRejectsActiveConfig() {
        AiProviderConfig active = new AiProviderConfig();
        active.setId(40L);
        active.setName("活跃配置");
        active.setIsActive(1);

        when(mapper.selectById(40L)).thenReturn(active);

        assertThrows(BizException.class, () -> service.delete(40L, 1L));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("UT-AICONFIG-08 从配置文件导入当前模型配置至数据库")
    void importFromFileImportsActiveBackend() {
        AdminDto.AiConfigVO vo = service.importFromFile(1L);

        assertNotNull(vo);
        assertEquals(1, vo.isActive());
        assertEquals("gpt-4.1-mini", vo.model());
        verify(mapper).insert(any(AiProviderConfig.class));
        verify(eventPublisher).publishEvent(any(AiConfigChangedEvent.class));
    }
}
