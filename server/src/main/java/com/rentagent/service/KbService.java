package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.agent.EmbeddingModelClient;
import com.rentagent.config.AiConfigChangedEvent;
import com.rentagent.config.AiProps;
import com.rentagent.entity.KbChunk;
import com.rentagent.entity.KbDocument;
import com.rentagent.mapper.KbChunkMapper;
import com.rentagent.mapper.KbDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 知识库服务（FR-13 / NFR-05）：
 * <ul>
 *   <li>文档切片入库与向量化；</li>
 *   <li>基于稠密向量嵌入（Dense Vector Embedding，如 bge-m3）与余弦相似度的语义检索；</li>
 *   <li>内存与 Redis 二级向量缓存，kb_chunk.vector_ref 持久化索引标记；</li>
 *   <li>嵌入服务不可用或异常时平滑降级至关键词检索（免 Key / 单测兼容）。</li>
 * </ul>
 */
@Slf4j
@Service
public class KbService implements ApplicationRunner {

    private static final int CHUNK_SIZE = 220;
    private static final String REDIS_PREFIX = "kb:vec:";
    private static final double DEFAULT_THRESHOLD = 0.58;

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final EmbeddingModelClient embeddingClient;
    private final StringRedisTemplate redisTemplate;
    private final AiProps aiProps;

    /** 内存向量索引：chunkId -> 嵌入向量 */
    private final Map<Long, float[]> vectorStore = new ConcurrentHashMap<>();

    public KbService(KbDocumentMapper documentMapper, KbChunkMapper chunkMapper) {
        this(documentMapper, chunkMapper, null, null, null);
    }

    @Autowired
    public KbService(KbDocumentMapper documentMapper,
                     KbChunkMapper chunkMapper,
                     @Autowired(required = false) EmbeddingModelClient embeddingClient,
                     @Autowired(required = false) StringRedisTemplate redisTemplate,
                     @Autowired(required = false) AiProps aiProps) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingClient = embeddingClient;
        this.redisTemplate = redisTemplate;
        this.aiProps = aiProps;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 服务启动后异步预热知识库向量索引
        try {
            ensureVectorsLoaded();
        } catch (Exception e) {
            log.warn("知识库向量预热后台任务异常: {}", e.getMessage());
        }
    }

    @EventListener
    public synchronized void onAiConfigChanged(AiConfigChangedEvent event) {
        log.info("RAG 知识库感知 AI 供应商配置变更 (action={})，重置向量索引缓存", event.action());
        vectorStore.clear();
        ensureVectorsLoaded();
    }

    /** 导入文档并切片入库，同时触发向量化 */
    @Transactional
    public KbDocument importDoc(String title, int category, String sourceUrl, String content) {
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setSourceUrl(sourceUrl);
        doc.setContent(content);
        doc.setStatus(1);
        documentMapper.insert(doc);

        List<String> pieces = split(content);
        int seq = 0;
        List<KbChunk> createdChunks = new ArrayList<>();
        for (String piece : pieces) {
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setSeq(seq++);
            chunk.setContent(piece);
            chunk.setTokenCount(piece.length());
            chunkMapper.insert(chunk);
            createdChunks.add(chunk);
        }
        doc.setChunkCount(seq);
        documentMapper.updateById(doc);

        // 稠密向量嵌入与缓存落库
        if (embeddingClient != null && embeddingClient.available() && !createdChunks.isEmpty()) {
            try {
                List<String> contents = createdChunks.stream().map(KbChunk::getContent).toList();
                List<float[]> embs = embeddingClient.embedBatch(contents);
                for (int i = 0; i < createdChunks.size() && i < embs.size(); i++) {
                    KbChunk c = createdChunks.get(i);
                    float[] vec = embs.get(i);
                    vectorStore.put(c.getId(), vec);
                    c.setVectorRef("vec:" + c.getId());
                    chunkMapper.updateById(c);
                    if (redisTemplate != null) {
                        try {
                            redisTemplate.opsForValue().set(REDIS_PREFIX + c.getId(), encodeVector(vec));
                        } catch (Exception ex) {
                            log.warn("写入 Redis 向量缓存失败: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("导入文档切片向量化失败: {}", e.getMessage());
            }
        }
        return doc;
    }

    /**
     * RAG 知识检索：
     * 1. 优先使用语义向量检索（基于稠密向量嵌入与余弦相似度）；
     * 2. 若向量模型未就绪或调用异常，平滑降级至关键词检索；
     * 3. 返回 topK 条 {docId, title, snippet} 引用片段。
     */
    public List<Map<String, String>> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (embeddingClient != null && embeddingClient.available()) {
            try {
                return vectorSearch(query, topK);
            } catch (Exception e) {
                log.warn("向量语义检索异常，平滑降级至关键词检索: {}", e.getMessage());
            }
        }
        return keywordSearch(query, topK);
    }

    /** 知识库是否命中（客服未命中转人工的判定依据，FR-13） */
    public boolean hit(String query) {
        return !search(query, 1).isEmpty();
    }

    /** 向量语义检索实现 */
    private List<Map<String, String>> vectorSearch(String query, int topK) {
        ensureVectorsLoaded();
        if (vectorStore.isEmpty()) {
            return keywordSearch(query, topK);
        }

        float[] queryVec = embeddingClient.embed(query);
        if (queryVec == null || queryVec.length == 0) {
            return keywordSearch(query, topK);
        }

        double threshold = resolveSimilarityThreshold();
        record ScoredChunk(Long chunkId, double similarity) {}
        List<ScoredChunk> candidates = new ArrayList<>();

        for (Map.Entry<Long, float[]> entry : vectorStore.entrySet()) {
            double sim = cosineSimilarity(queryVec, entry.getValue());
            if (sim >= threshold) {
                candidates.add(new ScoredChunk(entry.getKey(), sim));
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        List<ScoredChunk> topHits = candidates.stream().limit(topK).toList();

        Map<Long, KbDocument> docCache = new LinkedHashMap<>();
        List<Map<String, String>> result = new ArrayList<>();
        for (ScoredChunk hit : topHits) {
            KbChunk chunk = chunkMapper.selectById(hit.chunkId());
            if (chunk == null) {
                continue;
            }
            KbDocument doc = docCache.computeIfAbsent(chunk.getDocumentId(),
                    id -> documentMapper.selectById(id));
            Map<String, String> ref = new LinkedHashMap<>();
            ref.put("docId", String.valueOf(chunk.getDocumentId()));
            ref.put("title", doc == null ? "知识库" : doc.getTitle());
            ref.put("snippet", abbreviate(chunk.getContent(), 120));
            result.add(ref);
        }
        return result;
    }

    /** 关键词命中检索（平滑降级与免 Key 场景） */
    private List<Map<String, String>> keywordSearch(String query, int topK) {
        List<String> keywords = keywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<KbChunk> candidates = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .and(w -> {
                    for (String kw : keywords) {
                        w.or().like(KbChunk::getContent, kw);
                    }
                })
                .last("LIMIT 200"));
        Map<Long, KbDocument> docCache = new LinkedHashMap<>();
        record Scored(KbChunk chunk, long score) {}
        List<Scored> scored = candidates.stream()
                .map(c -> new Scored(c, keywords.stream().filter(kw -> c.getContent().toLowerCase()
                        .contains(kw.toLowerCase())).count()))
                .filter(s -> s.score() > 0)
                .sorted((a, b) -> Long.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
        List<Map<String, String>> result = new ArrayList<>();
        for (Scored s : scored) {
            KbDocument doc = docCache.computeIfAbsent(s.chunk().getDocumentId(),
                    id -> documentMapper.selectById(id));
            Map<String, String> ref = new LinkedHashMap<>();
            ref.put("docId", String.valueOf(s.chunk().getDocumentId()));
            ref.put("title", doc == null ? "知识库" : doc.getTitle());
            ref.put("snippet", abbreviate(s.chunk().getContent(), 120));
            result.add(ref);
        }
        return result;
    }

    /** 确保全部切片已加载向量索引 */
    public synchronized void ensureVectorsLoaded() {
        if (embeddingClient == null || !embeddingClient.available()) {
            return;
        }
        List<KbChunk> allChunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .orderByAsc(KbChunk::getId));
        if (allChunks.isEmpty()) {
            return;
        }

        List<KbChunk> needEmbed = new ArrayList<>();
        for (KbChunk chunk : allChunks) {
            if (vectorStore.containsKey(chunk.getId())) {
                continue;
            }
            float[] vec = null;
            if (redisTemplate != null) {
                try {
                    String cached = redisTemplate.opsForValue().get(REDIS_PREFIX + chunk.getId());
                    if (cached != null) {
                        vec = decodeVector(cached);
                    }
                } catch (Exception e) {
                    log.warn("从 Redis 读取向量缓存失败: {}", e.getMessage());
                }
            }
            if (vec != null) {
                vectorStore.put(chunk.getId(), vec);
            } else {
                needEmbed.add(chunk);
            }
        }

        if (!needEmbed.isEmpty()) {
            log.info("RAG 知识库发现 {} 条切片尚未建立向量索引，批量调用嵌入模型计算...", needEmbed.size());
            int batchSize = 10;
            for (int i = 0; i < needEmbed.size(); i += batchSize) {
                List<KbChunk> batch = needEmbed.subList(i, Math.min(i + batchSize, needEmbed.size()));
                List<String> contents = batch.stream().map(KbChunk::getContent).toList();
                try {
                    List<float[]> embs = embeddingClient.embedBatch(contents);
                    for (int j = 0; j < batch.size() && j < embs.size(); j++) {
                        KbChunk c = batch.get(j);
                        float[] v = embs.get(j);
                        vectorStore.put(c.getId(), v);
                        c.setVectorRef("vec:" + c.getId());
                        chunkMapper.updateById(c);
                        if (redisTemplate != null) {
                            try {
                                redisTemplate.opsForValue().set(REDIS_PREFIX + c.getId(), encodeVector(v));
                            } catch (Exception ex) {
                                log.warn("写入 Redis 向量缓存失败: {}", ex.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("切片批量生成向量失败: {}", e.getMessage());
                }
            }
            log.info("RAG 知识库向量索引加载完毕，当前索引切片数: {}", vectorStore.size());
        }
    }

    private double resolveSimilarityThreshold() {
        if (aiProps != null && aiProps.getEmbedding() != null
                && aiProps.getEmbedding().getSimilarityThreshold() != null) {
            return aiProps.getEmbedding().getSimilarityThreshold();
        }
        return DEFAULT_THRESHOLD;
    }

    /** 计算两向量的余弦相似度：Cosine(A, B) = A·B / (|A| * |B|) */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 向量序列化为 Base64 */
    public static String encodeVector(float[] vec) {
        if (vec == null) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4);
        for (float v : vec) {
            buf.putFloat(v);
        }
        return Base64.getEncoder().encodeToString(buf.array());
    }

    /** Base64 反序列化为向量 */
    public static float[] decodeVector(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) {
            vec[i] = buf.getFloat();
        }
        return vec;
    }

    /** 简易中文关键词提取：去停用词后对长片段做 2 字滑窗切词（平滑降级支撑） */
    public static List<String> keywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> stop = List.of("请问", "我想", "我要", "什么", "怎么", "如何", "一下", "可以", "你们",
                "你们好", "哪些", "多少", "是不是", "有没有", "rentagent");
        String cleaned = query.replaceAll("[\\s\\p{Punct}\uff0c\u3002\uff1f\uff01\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\uff08\uff09\u300a\u300b]", " ").trim();
        for (String sw : stop) {
            cleaned = cleaned.replace(sw, " ");
        }
        Set<String> kws = new LinkedHashSet<>();
        for (String seg : cleaned.split("\\s+")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (seg.length() <= 4) {
                kws.add(seg);
                continue;
            }
            for (int i = 0; i + 2 <= seg.length(); i++) {
                kws.add(seg.substring(i, i + 2));
            }
        }
        if (kws.isEmpty() && !cleaned.isBlank()) {
            kws.add(cleaned);
        }
        return kws.stream().limit(8).toList();
    }

    private List<String> split(String content) {
        List<String> pieces = new ArrayList<>();
        String remaining = content.trim();
        while (!remaining.isEmpty()) {
            int end = Math.min(CHUNK_SIZE, remaining.length());
            if (end < remaining.length()) {
                int dot = remaining.lastIndexOf('。', end);
                if (dot > CHUNK_SIZE / 2) {
                    end = dot + 1;
                }
            }
            pieces.add(remaining.substring(0, end));
            remaining = remaining.substring(end).trim();
        }
        return pieces;
    }

    private String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
