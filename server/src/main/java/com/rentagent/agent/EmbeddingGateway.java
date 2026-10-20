package com.rentagent.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.entity.AiProviderConfig;
import com.rentagent.mapper.AiProviderConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 词嵌入模型网关（RAG 知识库语义检索多后端与动态配置支撑）：
 * <p>
 * 遵循与 {@link LlmGateway} 完全一致的动态供应商读取机制：
 * <ol>
 *   <li>优先级 1：专用 Embedding 配置（环境变量 AI_EMBEDDING_API_KEY 或 application.yml）；</li>
 *   <li>优先级 2：数据库激活的 AI 供应商配置（ai_provider_config 表中 is_active=1 的项）；</li>
 *   <li>优先级 3：配置文件/环境变量中当前生效的命名后端（ai.active / backends / 快捷后端）；</li>
 *   <li>未配置有效凭据时 available()=false，上层 RAG 检索平滑降级至关键词检索。</li>
 * </ol>
 * 监听 {@link AiConfigChangedEvent} 事件，管理员在后台切换或修改 AI 供应商时自动热刷新生效模型。
 */
@Slf4j
@Component
public class EmbeddingGateway implements EmbeddingModelClient {

    private final AiProps props;
    private final AiProviderConfigMapper configMapper;

    private volatile EmbeddingModelClient delegate;
    private volatile ResolvedConfig resolved;

    public record ResolvedConfig(String source, String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public EmbeddingGateway(AiProps props) {
        this(props, null);
    }

    @Autowired
    public EmbeddingGateway(AiProps props, @Autowired(required = false) AiProviderConfigMapper configMapper) {
        this.props = props;
        this.configMapper = configMapper;
    }

    @EventListener
    public synchronized void onConfigChanged(AiConfigChangedEvent event) {
        log.info("Embedding 网关感知供应商配置变更 (action={})，刷新词嵌入模型客户端", event.action());
        refresh();
    }

    /** 刷新网关缓存的词嵌入模型，使数据库新配置即时生效 */
    public synchronized void refresh() {
        this.resolved = null;
        this.delegate = null;
    }

    /** 当前生效的嵌入配置描述，如 "DATABASE(大工 AI 网关):bge-m3" */
    public String describe() {
        ResolvedConfig r = resolve();
        return r == null ? "none" : r.source() + ":" + r.model();
    }

    @Override
    public boolean available() {
        resolve();
        return delegate != null && delegate.available();
    }

    @Override
    public float[] embed(String text) {
        if (!available() || text == null || text.isBlank()) {
            return new float[0];
        }
        return delegate.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!available() || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        return delegate.embedBatch(texts);
    }

    public synchronized ResolvedConfig resolvedConfig() {
        return resolve();
    }

    private synchronized ResolvedConfig resolve() {
        if (resolved != null) {
            return resolved;
        }

        // 1. 专用 Embedding 独立配置（环境变量 / application.yml 优先）
        AiProps.Embedding emb = props.getEmbedding();
        if (emb != null && emb.getApiKey() != null && !emb.getApiKey().isBlank()) {
            String baseUrl = (emb.getBaseUrl() != null && !emb.getBaseUrl().isBlank())
                    ? emb.getBaseUrl() : "http://aigw.dlut.edu.cn/v1";
            String model = (emb.getModel() != null && !emb.getModel().isBlank())
                    ? emb.getModel() : "bge-m3";
            int timeout = emb.getTimeoutSeconds() != null ? emb.getTimeoutSeconds() : 30;
            resolved = new ResolvedConfig("CONFIG(dedicated)", baseUrl, emb.getApiKey(), model, timeout);
            buildDelegate(resolved);
            return resolved;
        }

        // 2. 数据库中已激活的 AI 供应商配置
        if (configMapper != null) {
            try {
                // 2.1 优先查找类型为 EMBEDDING 且激活的专用词嵌入供应商
                AiProviderConfig activeEmb = configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                        .eq(AiProviderConfig::getCategory, "EMBEDDING")
                        .eq(AiProviderConfig::getIsActive, 1)
                        .last("LIMIT 1"));
                if (activeEmb != null && "EMBEDDING".equalsIgnoreCase(activeEmb.getCategory())
                        && activeEmb.getApiKey() != null && !activeEmb.getApiKey().isBlank()) {
                    String model = (activeEmb.getModel() != null && !activeEmb.getModel().isBlank())
                            ? activeEmb.getModel().trim() : "bge-m3";
                    int timeout = activeEmb.getTimeoutSeconds() != null ? activeEmb.getTimeoutSeconds() : 30;
                    resolved = new ResolvedConfig("DATABASE(" + activeEmb.getName() + ")", activeEmb.getBaseUrl(),
                            activeEmb.getApiKey(), model, timeout);
                    buildDelegate(resolved);
                    return resolved;
                }

                // 2.2 若无专用 EMBEDDING 供应商，尝试复用激活的 LLM 供应商凭据
                AiProviderConfig activeLlm = configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                        .and(w -> w.eq(AiProviderConfig::getCategory, "LLM").or().isNull(AiProviderConfig::getCategory))
                        .eq(AiProviderConfig::getIsActive, 1)
                        .last("LIMIT 1"));
                if (activeLlm != null && activeLlm.getApiKey() != null && !activeLlm.getApiKey().isBlank()) {
                    String model = (activeLlm.getEmbeddingModel() != null && !activeLlm.getEmbeddingModel().isBlank())
                            ? activeLlm.getEmbeddingModel().trim()
                            : ((emb != null && emb.getModel() != null && !emb.getModel().isBlank()) ? emb.getModel() : "bge-m3");
                    int timeout = (emb != null && emb.getTimeoutSeconds() != null)
                            ? emb.getTimeoutSeconds() : 30;
                    resolved = new ResolvedConfig("DATABASE(" + activeLlm.getName() + ")", activeLlm.getBaseUrl(),
                            activeLlm.getApiKey(), model, timeout);
                    buildDelegate(resolved);
                    return resolved;
                }
            } catch (Exception e) {
                log.warn("从数据库读取 AI 供应商配置异常（可能表未就绪），回落到配置文件: {}", e.getMessage());
            }
        }

        // 3. 配置文件/环境变量中当前生效的命名后端
        AiProps.Backend backend = props.activeBackend();
        if (backend != null && backend.getApiKey() != null && !backend.getApiKey().isBlank()) {
            String model = (emb != null && emb.getModel() != null && !emb.getModel().isBlank())
                    ? emb.getModel() : "bge-m3";
            int timeout = (emb != null && emb.getTimeoutSeconds() != null)
                    ? emb.getTimeoutSeconds() : 30;
            resolved = new ResolvedConfig("BACKEND(" + backend.getProtocol() + ")", backend.getBaseUrl(),
                    backend.getApiKey(), model, timeout);
            buildDelegate(resolved);
            return resolved;
        }

        return null;
    }

    private void buildDelegate(ResolvedConfig cfg) {
        log.info("Embedding 网关启用词嵌入后端: 来源={}, 端点={}, 模型={}",
                cfg.source(), cfg.baseUrl(), cfg.model());
        this.delegate = new OpenAiEmbeddingClient(
                cfg.baseUrl(), cfg.apiKey(), cfg.model(), Duration.ofSeconds(cfg.timeoutSeconds()));
    }
}
