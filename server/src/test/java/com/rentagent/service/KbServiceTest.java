package com.rentagent.service;

import com.rentagent.entity.KbChunk;
import com.rentagent.entity.KbDocument;
import com.rentagent.mapper.KbChunkMapper;
import com.rentagent.mapper.KbDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库与检索单元测试（FR-13）：中文关键词提取、切片、命中排序与引用组装。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-KB-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KbServiceTest {

    @Mock
    private KbDocumentMapper documentMapper;
    @Mock
    private KbChunkMapper chunkMapper;

    private KbService service;

    @BeforeEach
    void setUp() {
        service = new KbService(documentMapper, chunkMapper);
    }

    private KbChunk chunk(long docId, String content) {
        KbChunk c = new KbChunk();
        c.setDocumentId(docId);
        c.setContent(content);
        return c;
    }

    private KbDocument doc(long id, String title) {
        KbDocument d = new KbDocument();
        d.setId(id);
        d.setTitle(title);
        return d;
    }

    // ── 关键词提取 ──

    @Test
    @DisplayName("UT-KB-01 空查询不产生关键词")
    void 空查询无关键词() {
        assertTrue(KbService.keywords(null).isEmpty());
        assertTrue(KbService.keywords("").isEmpty());
        assertTrue(KbService.keywords("   ").isEmpty());
    }

    @Test
    @DisplayName("UT-KB-02 停用词被剔除，短词整段保留")
    void 停用词剔除() {
        List<String> kws = KbService.keywords("请问怎么退押金");

        assertFalse(kws.contains("请问"));
        assertFalse(kws.contains("怎么"));
        assertTrue(kws.contains("退押金"), "剩余短段应整体保留，实际=" + kws);
    }

    @Test
    @DisplayName("UT-KB-03 长片段按 2 字滑窗切词并限制最多 8 个")
    void 长片段滑窗切词() {
        List<String> kws = KbService.keywords("押金退还流程");

        assertTrue(kws.contains("押金"));
        assertTrue(kws.contains("退还"));
        assertTrue(kws.contains("流程"));
        assertEquals(5, kws.size());

        assertTrue(KbService.keywords("房屋租赁合同押金退还违约责任维修费用承担与交付标准说明").size() <= 8);
    }

    @Test
    @DisplayName("UT-KB-04 标点与符号被视作分隔符")
    void 标点作为分隔符() {
        List<String> kws = KbService.keywords("押金，退还；违约！");

        assertTrue(kws.contains("押金"));
        assertTrue(kws.contains("退还"));
        assertTrue(kws.contains("违约"));
    }

    // ── 检索 ──

    @Test
    @DisplayName("UT-KB-05 无有效关键词时不查库直接返回空")
    void 无关键词不查库() {
        assertTrue(service.search("   ", 3).isEmpty());

        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("UT-KB-06 命中片段按「命中关键词个数」排序并截断 topK")
    void 检索按命中数排序() {
        // 检索评分是"命中关键词个数"（非出现次数）：两个关键词各命中一次的片段排在只命中一个的之前
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(1L, "押金交纳说明：签约时交纳押金。"),
                chunk(2L, "押金退还流程：退租验收后押金退还。"),
                chunk(3L, "水电费结算方式。")));
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, "押金规则"));
        when(documentMapper.selectById(2L)).thenReturn(doc(2L, "退租指引"));

        List<Map<String, String>> result = service.search("押金 退还", 2);

        assertEquals(2, result.size());
        assertEquals("2", result.get(0).get("docId"), "命中关键词更多的片段应排前");
        assertEquals("退租指引", result.get(0).get("title"));
        assertEquals("押金规则", result.get(1).get("title"));
    }

    @Test
    @DisplayName("UT-KB-07 无命中片段时返回空列表")
    void 无命中返回空() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(1L, "完全不相关的内容")));

        assertTrue(service.search("押金退还", 3).isEmpty());
    }

    @Test
    @DisplayName("UT-KB-08 文档缺失时标题兜底，片段超长截断加省略号")
    void 引用组装兜底与截断() {
        String longChunk = "押金退还" + "详细说明".repeat(60);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(9L, longChunk)));
        when(documentMapper.selectById(9L)).thenReturn(null);

        List<Map<String, String>> result = service.search("押金退还", 1);

        assertEquals("知识库", result.get(0).get("title"));
        assertEquals(121, result.get(0).get("snippet").length());
        assertTrue(result.get(0).get("snippet").endsWith("…"));
    }

    @Test
    @DisplayName("UT-KB-09 知识库命中判定（客服未命中转人工的依据）")
    void 命中判定() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(1L, "押金退还说明")));
        assertTrue(service.hit("押金退还"));

        when(chunkMapper.selectList(any())).thenReturn(List.of());
        assertFalse(service.hit("押金退还"));
    }

    // ── 文档切片入库 ──

    @Test
    @DisplayName("UT-KB-10 文档导入按 220 字切片，优先在句号处切分并回写切片数")
    void 文档切片入库() {
        String content = "甲".repeat(200) + "。" + "乙".repeat(99);
        when(documentMapper.insert(any(KbDocument.class))).thenAnswer(inv -> {
            ((KbDocument) inv.getArgument(0)).setId(7L);
            return 1;
        });

        KbDocument saved = service.importDoc("押金规则", 1, null, content);

        assertEquals(1, saved.getStatus());
        assertEquals(2, saved.getChunkCount());
        ArgumentCaptor<KbChunk> captor = ArgumentCaptor.forClass(KbChunk.class);
        verify(chunkMapper, times(2)).insert(captor.capture());
        List<KbChunk> chunks = captor.getAllValues();
        assertEquals(0, chunks.get(0).getSeq());
        assertEquals(201, chunks.get(0).getContent().length(), "应在 220 字内的句号处切分");
        assertTrue(chunks.get(0).getContent().endsWith("。"));
        assertEquals(7L, chunks.get(0).getDocumentId());
        assertEquals(99, chunks.get(1).getContent().length());
        assertEquals(chunks.get(1).getContent().length(), chunks.get(1).getTokenCount());
        verify(documentMapper).updateById(saved);
    }

    @Test
    @DisplayName("UT-KB-11 短文档不切片，仅生成 1 个片段")
    void 短文档单片段() {
        when(documentMapper.insert(any(KbDocument.class))).thenAnswer(inv -> {
            ((KbDocument) inv.getArgument(0)).setId(8L);
            return 1;
        });

        KbDocument saved = service.importDoc("短文档", 2, null, "押金退还规则说明。");

        assertEquals(1, saved.getChunkCount());
        verify(chunkMapper, times(1)).insert(any(KbChunk.class));
    }

    @Test
    @DisplayName("UT-KB-12 长文档（无句号）按固定长度顺序切片，无内容丢失")
    void 无句号按固定长度切片() {
        String content = "内容".repeat(300); // 600 字，无句号
        when(documentMapper.insert(any(KbDocument.class))).thenAnswer(inv -> {
            ((KbDocument) inv.getArgument(0)).setId(9L);
            return 1;
        });

        KbDocument saved = service.importDoc("无标点文档", 1, null, content);

        ArgumentCaptor<KbChunk> captor = ArgumentCaptor.forClass(KbChunk.class);
        verify(chunkMapper, times(3)).insert(captor.capture());
        List<KbChunk> chunks = new ArrayList<>(captor.getAllValues());
        assertEquals(220, chunks.get(0).getContent().length());
        assertEquals(220, chunks.get(1).getContent().length());
        assertEquals(160, chunks.get(2).getContent().length());
        assertEquals(content, chunks.get(0).getContent() + chunks.get(1).getContent() + chunks.get(2).getContent());
        assertEquals(3, saved.getChunkCount());
    }
}
