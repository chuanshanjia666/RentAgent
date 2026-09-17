package com.rentagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.agent.LlmGateway;
import com.rentagent.common.BizException;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.AiAnalysis;
import com.rentagent.entity.Contract;
import com.rentagent.entity.House;
import com.rentagent.mapper.AiAnalysisMapper;
import com.rentagent.mapper.ContractMapper;
import com.rentagent.mapper.HouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 分析服务单元测试（FR-08/14/15/16）：智能定价区间计算与样本回退、虚假房源启发式打分、合同解读风险识别。
 * 全部为规则引擎路径（不发起任何网络调用），与「无 Key 也可演示」的降级设计一致。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-AI-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisServiceTest {

    @Mock
    private AiAnalysisMapper analysisMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private LlmGateway llmGateway;

    private AnalysisService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AnalysisService(analysisMapper, houseMapper, contractMapper, objectMapper, llmGateway);
    }

    private House house(long id, String community, String district, String layout, String rent) {
        House h = new House();
        h.setId(id);
        h.setCommunity(community);
        h.setDistrict(district);
        h.setLayout(layout);
        h.setTitle("精装一居室");
        h.setRent(new BigDecimal(rent));
        h.setStatus(HouseService.ST_ONLINE);
        return h;
    }

    private House draft() {
        House h = new House();
        h.setCommunity("软件园公寓");
        h.setDistrict("高新园区");
        h.setLayout("1室1厅");
        return h;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    private House buildHouse(String title, String description, String rent) {
        House h = house(101L, "软件园公寓", "高新园区", "1室1厅", rent);
        h.setTitle(title);
        h.setDescription(description);
        return h;
    }

    // ── FR-15 智能定价 ──

    @Test
    @DisplayName("UT-AI-01 同小区样本充足：均价与上下浮动区间按 HALF_UP 取整")
    void 定价区间计算() {
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "2000"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "2200"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "2400")));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(new BigDecimal("2200.00"), vo.avg());
        assertEquals(new BigDecimal("2024"), vo.low());   // 2200 × 0.92
        assertEquals(new BigDecimal("2376"), vo.high());  // 2200 × 1.08
        assertEquals(3, vo.sampleCount());
        assertTrue(vo.basis().contains("同小区「软件园公寓」"));
        assertTrue(vo.basis().contains("n=3"));
        assertEquals("rule-engine", vo.model());
        assertTrue(vo.note().contains("在区间内浮动"));
    }

    @Test
    @DisplayName("UT-AI-02 区间取整采用四舍五入（HALF_UP）")
    void 定价区间取整() {
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "2100"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "2100"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "2101")));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(new BigDecimal("2100.33"), vo.avg());
        assertEquals(new BigDecimal("1932"), vo.low());   // 1932.3036 → 1932
        assertEquals(new BigDecimal("2268"), vo.high());  // 2268.3564 → 2268
    }

    @Test
    @DisplayName("UT-AI-03 同小区样本不足 3 条时回退到同区域同户型样本")
    void 样本不足回退同区域同户型() {
        when(houseMapper.selectList(any()))
                .thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000")))
                .thenReturn(List.of(house(2L, "滨海公寓", "高新园区", "1室1厅", "3000"),
                        house(3L, "阳光家园", "高新园区", "1室1厅", "4000"),
                        house(4L, "东软家园", "高新园区", "1室1厅", "5000")));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertTrue(vo.basis().contains("同区域同户型"));
        assertTrue(vo.basis().contains("高新园区 · 1室1厅"));
        assertEquals(3, vo.sampleCount());
        assertEquals(new BigDecimal("4000.00"), vo.avg());
    }

    @Test
    @DisplayName("UT-AI-04 两级样本皆空：返回空区间并提示人工定价")
    void 无样本兜底() {
        when(houseMapper.selectList(any())).thenReturn(List.of());

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertNull(vo.low());
        assertNull(vo.high());
        assertNull(vo.avg());
        assertEquals(0, vo.sampleCount());
        assertEquals("rule-engine", vo.model());
        assertTrue(vo.note().contains("人工定价"));
    }

    @Test
    @DisplayName("UT-AI-05 样本不足 3 条时提示样本较少（含回退后样本数仍少的情形）")
    void 样本较少提示() {
        when(houseMapper.selectList(any()))
                .thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000"),
                        house(2L, "软件园公寓", "高新园区", "1室1厅", "2200")))
                .thenReturn(List.of(house(3L, "软件园公寓", "高新园区", "1室1厅", "2400")));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertTrue(vo.note().contains("样本较少"));
        assertEquals(1, vo.sampleCount());
    }

    @Test
    @DisplayName("UT-AI-06 定价结果落库为 type=1 且 model 记录当前引擎")
    void 定价结果落库留痕() {
        when(houseMapper.selectList(any())).thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000")));
        when(llmGateway.available()).thenReturn(true);
        when(llmGateway.describe()).thenReturn("openai-chat-completions:deepseek/deepseek-v4.1-flash");

        service.pricing(draft(), 7L);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        AiAnalysis saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals(1, saved.getType());
        assertEquals("draft", saved.getTargetType());
        assertEquals(0L, saved.getTargetId());
        assertEquals("openai-chat-completions:deepseek/deepseek-v4.1-flash", saved.getModel());
        assertTrue(saved.getResult().contains("sampleCount"));
    }

    @Test
    @DisplayName("UT-AI-07 落库异常不影响定价结果返回（NFR-03 降级）")
    void 落库失败不影响返回() {
        when(houseMapper.selectList(any())).thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000")));
        when(analysisMapper.insert(any(AiAnalysis.class))).thenThrow(new RuntimeException("db down"));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(1, vo.sampleCount());
    }

    @Test
    @DisplayName("UT-AI-08 引擎标识超长时按列宽截断到 100 字符")
    void 引擎标识截断() {
        when(houseMapper.selectList(any())).thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000")));
        when(llmGateway.available()).thenReturn(true);
        when(llmGateway.describe()).thenReturn("x".repeat(150));

        service.pricing(draft(), 7L);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        assertEquals(100, captor.getValue().getModel().length());
    }

    // ── FR-16 虚假房源检测 ──

    @Test
    @DisplayName("UT-AI-09 租金低于同区域均价 40% 以上判为高风险（40 分 + 阈值 70）")
    void 租金异常偏离判高风险() {
        // 均价 3000，自身 1000 < 1800 → +40；描述充足且无其他触发 → 40 分中风险
        House target = buildHouse("软件园公寓整租",
                "本房源位于软件园核心地段，步行至地铁站约五分钟，房屋精装修，家电家具齐全，采光通风良好，周边生活配套成熟，适合软件园上班族长期租住。", "1000");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "3000"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "3000"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "3000")));

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(40, vo.riskScore());
        assertTrue(vo.suspicions().get(0).contains("显著低于同区域均价"));
        assertTrue(vo.suggestion().contains("中风险"));
        assertEquals("rule-engine", vo.model());
    }

    @Test
    @DisplayName("UT-AI-10 引流话术关键词逐条叠加（每条 20 分）且封顶 100")
    void 引流话术叠加封顶() {
        House target = buildHouse("免押金 仅此一套 先到先得 无需看房 低价直租",
                "免押金仅此一套先到先得，无需看房低价直租，不查征信，本房源位于软件园核心地段，周边配套成熟，交通便利，联系 13800001111", "3000");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of());

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(100, vo.riskScore(), "六条话术 120 分 + 联系方式 25 分应封顶 100");
        assertEquals(7, vo.suspicions().size());
        assertTrue(vo.suggestion().contains("高风险"));
    }

    @Test
    @DisplayName("UT-AI-11 描述过短计 15 分，含外部手机号计 25 分")
    void 信息完整度与联系方式规则() {
        House target = buildHouse("软件园公寓", "近地铁，电话 13800001111", "3000");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of());

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(40, vo.riskScore()); // 15（描述过短）+ 25（外部联系方式）
        assertEquals(2, vo.suspicions().size());
        assertTrue(vo.suggestion().contains("中风险"));
    }

    @Test
    @DisplayName("UT-AI-12 无异常特征判定为低风险")
    void 低风险判定() {
        House target = buildHouse("软件园公寓 1 室 1 厅 近地铁精装",
                "本房源位于软件园核心地段，步行至地铁站约五分钟，房屋精装修，家电家具齐全，采光通风良好，周边生活配套成熟，适合软件园上班族长期租住。", "3000");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of());

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(0, vo.riskScore());
        assertTrue(vo.suspicions().isEmpty());
        assertTrue(vo.suggestion().contains("低风险"));
    }

    @Test
    @DisplayName("UT-AI-13 同区域样本不足 3 条时不做租金偏离判定")
    void 样本不足不做偏离判定() {
        House target = buildHouse("软件园公寓 1 室 1 厅 近地铁精装",
                "本房源位于软件园核心地段，步行至地铁站约五分钟，房屋精装修，家电家具齐全，采光通风良好，周边生活配套成熟。", "100");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of(house(1L, "x", "高新园区", "1室1厅", "3000")));

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(0, vo.riskScore());
    }

    @Test
    @DisplayName("UT-AI-14 检测不存在的房源返回 2001")
    void 检测房源不存在() {
        when(houseMapper.selectById(999L)).thenReturn(null);

        assertCode(2001, () -> service.fakeDetect(999L, 1L));
    }

    // ── FR-14 合同解读 ──

    private Contract contract(long id, long tenantId, long landlordId, List<Map<String, String>> clauses) {
        Contract c = new Contract();
        c.setId(id);
        c.setTenantId(tenantId);
        c.setLandlordId(landlordId);
        try {
            c.setClauses(objectMapper.writeValueAsString(clauses));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return c;
    }

    @Test
    @DisplayName("UT-AI-15 合同不存在返回 3004，非合同双方返回 1007")
    void 合同解读权限校验() {
        when(contractMapper.selectById(999L)).thenReturn(null);
        assertCode(3004, () -> service.interpret(999L, 2L));

        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, List.of()));
        assertCode(1007, () -> service.interpret(88L, 99L));
    }

    @Test
    @DisplayName("UT-AI-16 条款 JSON 解析失败返回 4001")
    void 条款解析失败() {
        Contract c = new Contract();
        c.setId(88L);
        c.setTenantId(2L);
        c.setLandlordId(7L);
        c.setClauses("{不是合法 JSON");
        when(contractMapper.selectById(88L)).thenReturn(c);

        assertCode(4001, () -> service.interpret(88L, 2L));
    }

    @Test
    @DisplayName("UT-AI-17 风险条款识别：押金不退/单方涨租/违约金等命中，含甲方违约的违约金条款不标红")
    void 风险条款识别() {
        List<Map<String, String>> clauses = new ArrayList<>();
        clauses.add(Map.of("title", "违约责任", "text", "乙方提前退租的押金不予退还。"));
        clauses.add(Map.of("title", "合同解除", "text", "单方涨租或单方收房视为违约。"));
        clauses.add(Map.of("title", "续约", "text", "租赁期满后自动续约一年。"));
        clauses.add(Map.of("title", "违约金例外", "text", "若因房东逾期交房产生的违约金，不构成甲方违约。"));
        clauses.add(Map.of("title", "维修责任", "text", "房屋主体结构损坏由甲方负责维修。"));
        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, clauses));

        AiDto.InterpVO vo = service.interpret(88L, 2L);

        assertEquals(5, vo.items().size());
        assertTrue(vo.items().get(0).risk());
        assertTrue(vo.items().get(1).risk());
        assertTrue(vo.items().get(2).risk());
        assertFalse(vo.items().get(3).risk(), "含「甲方违约」的违约金条款不应标红");
        assertFalse(vo.items().get(4).risk());
        assertEquals("rule-engine", vo.model());
        assertTrue(vo.disclaimer().contains("不构成法律意见"));
    }

    @Test
    @DisplayName("UT-AI-18 条款通俗化替换甲乙双方称谓并追加解释")
    void 条款通俗化() {
        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, List.of(
                Map.of("title", "租金与押金", "text", "甲方收取押金，乙方按月支付，押付方式为押一付三。"),
                Map.of("title", "维修责任", "text", "甲方应在 7 日内维修。"),
                Map.of("title", "续约", "text", "乙方享有优先续租权。"))));

        AiDto.InterpVO vo = service.interpret(88L, 2L);

        String first = vo.items().get(0).explanation();
        assertTrue(first.contains("房东收取押金"));
        assertTrue(first.contains("你（租客）按月支付"));
        assertTrue(first.contains("押金在退租验收无违约时会退还"));
        assertTrue(vo.items().get(1).explanation().contains("要求房东限期维修"));
        assertTrue(vo.items().get(2).explanation().contains("优先续租的权利"));
    }

    @Test
    @DisplayName("UT-AI-19 解读结果与风险下标回写：落库 type=3 并写回 risk_flags")
    void 解读结果留痕与风险回写() {
        Contract c = contract(88L, 2L, 7L, List.of(
                Map.of("title", "违约责任", "text", "乙方提前退租的押金不予退还。"),
                Map.of("title", "租期", "text", "租赁期一年。")));
        when(contractMapper.selectById(88L)).thenReturn(c);

        service.interpret(88L, 2L);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getType());
        assertEquals("contract", captor.getValue().getTargetType());
        assertEquals("[0]", c.getRiskFlags());
        verify(contractMapper).updateById(c);
    }

    // ── FR-08 智能识别填充 ──

    @Test
    @DisplayName("UT-AI-20 智能识别按标题关键词推断设施、朝向与楼层")
    void 智能识别填充() {
        HouseDto.AiFillVO vo = service.assistFill(
                new HouseDto.AiFillReq("近地铁精装高层朝南两居", "软件园公寓", "2室1厅", "IMG_南向.jpg"), 7L);

        assertTrue(vo.facilities().contains("近地铁"));
        assertTrue(vo.facilities().contains("精装修"));
        assertEquals("南", vo.orientation());
        assertEquals("高层", vo.floorDesc());
        assertTrue(vo.description().contains("软件园公寓"));
        assertTrue(vo.description().contains("2室1厅"));
    }

    @Test
    @DisplayName("UT-AI-21 无关键词命中时设施取默认推荐组合")
    void 智能识别默认设施() {
        HouseDto.AiFillVO vo = service.assistFill(new HouseDto.AiFillReq("一居室出租", null, null, null), 7L);

        assertEquals(List.of("家电齐全", "拎包入住"), vo.facilities());
        assertTrue(vo.description().contains("1室1厅"));
        assertTrue(vo.description().contains("目标小区"));
        assertEquals("中层", vo.floorDesc());
    }
}
