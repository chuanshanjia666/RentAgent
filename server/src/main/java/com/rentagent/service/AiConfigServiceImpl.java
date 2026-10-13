package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rentagent.agent.AnthropicMessagesChatModel;
import com.rentagent.agent.LlmGateway;
import com.rentagent.agent.OpenAiChatCompletionsModel;
import com.rentagent.agent.OpenAiResponsesChatModel;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.dto.AdminDto;
import com.rentagent.entity.AiProviderConfig;
import com.rentagent.mapper.AiProviderConfigMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AiProviderConfigMapper mapper;
    private final AiProps props;
    private final LlmGateway llmGateway;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_provider_config (
                      id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      name            VARCHAR(50)  NOT NULL COMMENT '供应商名称',
                      protocol        VARCHAR(50)  NOT NULL DEFAULT 'openai-chat-completions' COMMENT '协议: openai-chat-completions / anthropic-messages / openai-responses',
                      base_url        VARCHAR(255) NOT NULL COMMENT '接口基址',
                      api_key         VARCHAR(255) NOT NULL COMMENT 'API Key凭据',
                      model           VARCHAR(100) NOT NULL COMMENT '模型名称',
                      temperature     DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '采样温度',
                      max_tokens      INT UNSIGNED NOT NULL DEFAULT 4096 COMMENT '最大Token预算',
                      timeout_seconds INT UNSIGNED NOT NULL DEFAULT 60 COMMENT '超时时间(秒)',
                      user_agent      VARCHAR(255) NULL COMMENT '自定义User-Agent',
                      is_active       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否当前生效: 1是 0否',
                      created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      KEY idx_active (is_active)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI API供应商配置表';
                    """);
        } catch (Exception e) {
            log.warn("自检 ai_provider_config 表异常（若非 MySQL 环境可忽略）: {}", e.getMessage());
        }
    }

    @Override
    public AdminDto.AiConfigOverviewVO getOverview() {
        List<AiProviderConfig> list = mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .orderByDesc(AiProviderConfig::getIsActive)
                .orderByDesc(AiProviderConfig::getId));

        List<AdminDto.AiConfigVO> voList = new ArrayList<>(list.size());
        AiProviderConfig activeFromDb = null;
        for (AiProviderConfig c : list) {
            if (c.getIsActive() != null && c.getIsActive() == 1) {
                activeFromDb = c;
            }
            voList.add(toVO(c));
        }

        String activeSource;
        String activeDescribe;
        Long activeConfigId = null;

        if (activeFromDb != null && activeFromDb.getApiKey() != null && !activeFromDb.getApiKey().isBlank()) {
            activeSource = "DATABASE";
            activeDescribe = LlmGateway.normalizeProtocol(activeFromDb.getProtocol()) + ":" + activeFromDb.getModel();
            activeConfigId = activeFromDb.getId();
        } else if (llmGateway.available()) {
            activeSource = "CONFIG_FILE";
            activeDescribe = llmGateway.describe();
        } else {
            activeSource = "NONE";
            activeDescribe = "none";
        }

        return new AdminDto.AiConfigOverviewVO(activeSource, activeDescribe, activeConfigId, voList);
    }

    @Override
    @Transactional
    public AdminDto.AiConfigVO save(AdminDto.AiConfigSaveReq req, Long adminId) {
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "新建 AI 供应商配置必须填写 API Key 凭据");
        }

        boolean active = Boolean.TRUE.equals(req.isActive());
        if (active) {
            mapper.update(null, new LambdaUpdateWrapper<AiProviderConfig>()
                    .set(AiProviderConfig::getIsActive, 0));
        }

        AiProviderConfig entity = new AiProviderConfig();
        entity.setName(req.name().trim());
        entity.setProtocol(LlmGateway.normalizeProtocol(req.protocol()));
        entity.setBaseUrl(req.baseUrl().trim());
        entity.setApiKey(req.apiKey().trim());
        entity.setModel(req.model().trim());
        entity.setTemperature(req.temperature() != null ? BigDecimal.valueOf(req.temperature()) : BigDecimal.valueOf(0.70));
        entity.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 4096);
        entity.setTimeoutSeconds(req.timeoutSeconds() != null ? req.timeoutSeconds() : 60);
        entity.setUserAgent(req.userAgent() != null && !req.userAgent().isBlank() ? req.userAgent().trim() : null);
        entity.setIsActive(active ? 1 : 0);

        mapper.insert(entity);

        auditLogService.log(adminId, "AI_CONFIG_CREATE", "ai_provider_config", entity.getId(),
                safeDetail(entity.getName(), entity.getProtocol(), entity.getModel()), null);

        if (active) {
            eventPublisher.publishEvent(new AiConfigChangedEvent(entity.getId(), "save_and_activate"));
        }

        return toVO(entity);
    }

    @Override
    @Transactional
    public AdminDto.AiConfigVO update(Long id, AdminDto.AiConfigSaveReq req, Long adminId) {
        AiProviderConfig existing = mapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "未找到指定的 AI 供应商配置");
        }

        boolean shouldActivate = Boolean.TRUE.equals(req.isActive());
        if (shouldActivate) {
            mapper.update(null, new LambdaUpdateWrapper<AiProviderConfig>()
                    .ne(AiProviderConfig::getId, id)
                    .set(AiProviderConfig::getIsActive, 0));
        }

        existing.setName(req.name().trim());
        existing.setProtocol(LlmGateway.normalizeProtocol(req.protocol()));
        existing.setBaseUrl(req.baseUrl().trim());
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            existing.setApiKey(req.apiKey().trim());
        }
        existing.setModel(req.model().trim());
        if (req.temperature() != null) {
            existing.setTemperature(BigDecimal.valueOf(req.temperature()));
        }
        if (req.maxTokens() != null) {
            existing.setMaxTokens(req.maxTokens());
        }
        if (req.timeoutSeconds() != null) {
            existing.setTimeoutSeconds(req.timeoutSeconds());
        }
        existing.setUserAgent(req.userAgent() != null && !req.userAgent().isBlank() ? req.userAgent().trim() : null);
        if (req.isActive() != null) {
            existing.setIsActive(shouldActivate ? 1 : 0);
        }

        mapper.updateById(existing);

        auditLogService.log(adminId, "AI_CONFIG_UPDATE", "ai_provider_config", id,
                safeDetail(existing.getName(), existing.getProtocol(), existing.getModel()), null);

        if (existing.getIsActive() == 1 || shouldActivate) {
            eventPublisher.publishEvent(new AiConfigChangedEvent(id, "update_active"));
        }

        return toVO(existing);
    }

    @Override
    @Transactional
    public void activate(Long id, Long adminId) {
        AiProviderConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "指定的供应商配置不存在");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "该配置未配置 API Key，无法设为生效");
        }

        mapper.update(null, new LambdaUpdateWrapper<AiProviderConfig>()
                .set(AiProviderConfig::getIsActive, 0));

        config.setIsActive(1);
        mapper.updateById(config);

        auditLogService.log(adminId, "AI_CONFIG_ACTIVATE", "ai_provider_config", id,
                safeDetail(config.getName(), config.getProtocol(), config.getModel()), null);

        eventPublisher.publishEvent(new AiConfigChangedEvent(id, "activate"));
    }

    @Override
    @Transactional
    public void deactivate(Long adminId) {
        mapper.update(null, new LambdaUpdateWrapper<AiProviderConfig>()
                .set(AiProviderConfig::getIsActive, 0));

        auditLogService.log(adminId, "AI_CONFIG_DEACTIVATE", "ai_provider_config", null,
                Map.of("action", "deactivate_all"), null);

        eventPublisher.publishEvent(new AiConfigChangedEvent(null, "deactivate"));
    }

    @Override
    @Transactional
    public void delete(Long id, Long adminId) {
        AiProviderConfig config = mapper.selectById(id);
        if (config == null) {
            return;
        }
        if (config.getIsActive() != null && config.getIsActive() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "不可删除当前正在生效的供应商配置，请先切换生效配置或停用");
        }

        mapper.deleteById(id);

        auditLogService.log(adminId, "AI_CONFIG_DELETE", "ai_provider_config", id,
                Map.of("name", config.getName()), null);
    }

    @Override
    public AdminDto.AiConfigTestVO testConnection(AdminDto.AiConfigTestReq req) {
        long start = System.currentTimeMillis();
        try {
            String apiKey = req.apiKey();
            if ((apiKey == null || apiKey.isBlank()) && req.id() != null) {
                AiProviderConfig existing = mapper.selectById(req.id());
                if (existing != null) {
                    apiKey = existing.getApiKey();
                }
            }

            if (apiKey == null || apiKey.isBlank()) {
                return new AdminDto.AiConfigTestVO(false, 0, "缺少 API Key 凭据");
            }

            String protocol = req.protocol();
            String baseUrl = req.baseUrl();
            String model = req.model();

            if (baseUrl == null || baseUrl.isBlank()) {
                return new AdminDto.AiConfigTestVO(false, 0, "缺少接口基址");
            }
            if (model == null || model.isBlank()) {
                return new AdminDto.AiConfigTestVO(false, 0, "缺少模型名称");
            }

            Duration timeout = Duration.ofSeconds(req.timeoutSeconds() == null ? 15 : Math.min(req.timeoutSeconds(), 30));
            ChatLanguageModel testClient;
            String norm = LlmGateway.normalizeProtocol(protocol);

            switch (norm) {
                case "anthropic-messages" -> testClient = new AnthropicMessagesChatModel(
                        baseUrl, apiKey, model,
                        req.temperature() == null ? 0.7 : req.temperature(),
                        10, timeout);
                case "openai-responses" -> testClient = new OpenAiResponsesChatModel(
                        baseUrl, apiKey, model,
                        req.temperature() == null ? 0.7 : req.temperature(),
                        10, timeout);
                default -> {
                    Map<String, String> headers = new LinkedHashMap<>();
                    if (req.userAgent() != null && !req.userAgent().isBlank()) {
                        headers.put("User-Agent", req.userAgent());
                    }
                    testClient = new OpenAiChatCompletionsModel(
                            baseUrl, apiKey, model,
                            req.temperature() == null ? 0.7 : req.temperature(),
                            10, timeout, headers);
                }
            }

            var resp = testClient.generate(UserMessage.from("Hi"));
            long latency = System.currentTimeMillis() - start;
            String text = resp != null && resp.content() != null ? resp.content().text() : "";
            return new AdminDto.AiConfigTestVO(true, latency, "连接成功（响应耗时 " + latency + "ms）");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("AI 供应商连接测试失败: {}", e.getMessage());
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            return new AdminDto.AiConfigTestVO(false, latency, "连接测试失败: " + msg);
        }
    }

    @Override
    @Transactional
    public AdminDto.AiConfigVO importFromFile(Long adminId) {
        AiProps.Backend b = props.activeBackend();
        if (b == null || b.getApiKey() == null || b.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "配置文件或环境变量中未配置有效的 AI 供应商或缺少 API Key");
        }

        mapper.update(null, new LambdaUpdateWrapper<AiProviderConfig>()
                .set(AiProviderConfig::getIsActive, 0));

        AiProviderConfig entity = new AiProviderConfig();
        String name = props.getActive() != null && !props.getActive().isBlank()
                ? props.getActive() : "导入自配置文件 (" + b.getModel() + ")";
        entity.setName(name);
        entity.setProtocol(LlmGateway.normalizeProtocol(b.getProtocol()));
        entity.setBaseUrl(b.getBaseUrl() == null || b.getBaseUrl().isBlank() ? "https://api.openai.com/v1" : b.getBaseUrl());
        entity.setApiKey(b.getApiKey());
        entity.setModel(b.getModel());
        entity.setTemperature(b.getTemperature() != null ? BigDecimal.valueOf(b.getTemperature()) : BigDecimal.valueOf(0.70));
        entity.setMaxTokens(b.getMaxTokens() != null ? b.getMaxTokens() : 4096);
        entity.setTimeoutSeconds(b.getTimeoutSeconds() != null ? b.getTimeoutSeconds() : 60);
        entity.setUserAgent(b.getUserAgent());
        entity.setIsActive(1);

        mapper.insert(entity);

        auditLogService.log(adminId, "AI_CONFIG_IMPORT", "ai_provider_config", entity.getId(),
                safeDetail(entity.getName(), entity.getProtocol(), entity.getModel()), null);

        eventPublisher.publishEvent(new AiConfigChangedEvent(entity.getId(), "import_and_activate"));

        return toVO(entity);
    }

    private Map<String, Object> safeDetail(String name, String protocol, String model) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (name != null) map.put("name", name);
        if (protocol != null) map.put("protocol", protocol);
        if (model != null) map.put("model", model);
        return map;
    }

    private AdminDto.AiConfigVO toVO(AiProviderConfig c) {
        String key = c.getApiKey();
        boolean hasKey = key != null && !key.isBlank();
        String maskedKey = "";
        if (hasKey) {
            maskedKey = key.length() <= 8 ? "******" : key.substring(0, 3) + "****" + key.substring(key.length() - 4);
        }
        return new AdminDto.AiConfigVO(
                c.getId(),
                c.getName(),
                c.getProtocol(),
                c.getBaseUrl(),
                maskedKey,
                hasKey,
                c.getModel(),
                c.getTemperature() != null ? c.getTemperature().doubleValue() : 0.70,
                c.getMaxTokens(),
                c.getTimeoutSeconds(),
                c.getUserAgent(),
                c.getIsActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
