package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rentagent.entity.KbChunk;
import com.rentagent.entity.KbDocument;
import com.rentagent.mapper.KbChunkMapper;
import com.rentagent.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库（FR-13 / NFR-05）：
 * 文档切片入库；检索当前为关键词命中 + 计分（免 Key 可用），
 * kb_chunk.vector_ref 已预留向量升级位（接入 embedding 模型后填充并切换 Redis Stack 语义检索）。
 */
@Service
@RequiredArgsConstructor
public class KbService {

    private static final int CHUNK_SIZE = 220;

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    /** 导入文档并切片入库 */
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
        for (String piece : pieces) {
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setSeq(seq++);
            chunk.setContent(piece);
            chunk.setTokenCount(piece.length());
            chunkMapper.insert(chunk);
        }
        doc.setChunkCount(seq);
        documentMapper.updateById(doc);
        return doc;
    }

    /** 检索：返回 topK 条 {docId, docTitle, content} 引用（按关键词命中数排序） */
    public List<Map<String, String>> search(String query, int topK) {
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
        record Scored(KbChunk chunk, long score) {
        }
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

    /** 知识库是否命中（客服未命中转人工的判定依据，FR-13） */
    public boolean hit(String query) {
        return !search(query, 1).isEmpty();
    }

    /** 简易中文关键词提取：去停用词后对长片段做 2 字滑窗切词（演示级实现，支撑关键词检索命中） */
    static List<String> keywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> stop = List.of("请问", "我想", "我要", "什么", "怎么", "如何", "一下", "可以", "你们",
                "你们好", "哪些", "多少", "是不是", "有没有", "rentagent");
        String cleaned = query.replaceAll("[\\s\\p{Punct}\uff0c\u3002\uff1f\uff01\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\uff08\uff09\u300a\u300b]", " ").trim();
        for (String sw : stop) {
            cleaned = cleaned.replace(sw, " ");
        }
        java.util.Set<String> kws = new java.util.LinkedHashSet<>();
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
