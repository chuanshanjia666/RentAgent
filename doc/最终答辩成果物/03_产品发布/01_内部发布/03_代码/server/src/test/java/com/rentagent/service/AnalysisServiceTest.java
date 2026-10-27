package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.agent.AiJsonClient;
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
import com.rentagent.support.EntityMetadataHelper;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 分析服务单元测试（FR-08/14/15/16）。
 * <p>
 * 2026-09-17 口径变更后（四项能力全部真调模型、无规则兜底），本层的职责边界是：
 * <b>送模型的"数据事实"是否正确</b>（样本统计、条款原文、房源字段）与
 * <b>模型返回的"结构与取值域"是否被正确校验并落库</b>；
 * 模型本身的行为由 {@code AiJsonClientTest}（解析/重试/报错）与集成冒烟（真实模型）覆盖。
 * 用例编号见 doc/04.编码/单元测试用例设计.md（UT-AI-xx）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisServiceTest {

    private static final String ENGINE = "openai-chat-completions:deepseek/deepseek-v4.1-flash";

    @Mock
    private AiAnalysisMapper analysisMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private LlmGateway llmGateway;
    @Mock
    private AiJsonClient aiJsonClient;

    private AnalysisService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // risk_flags 回写走 LambdaUpdateWrapper.set(...)，需要渲染 lambda 列名
        EntityMetadataHelper.init(Contract.class);
        service = new AnalysisService(analysisMapper, houseMapper, contractMapper, objectMapper,
                llmGateway, aiJsonClient);
        when(llmGateway.describe()).thenReturn(ENGINE);
    }

    // ── 测试数据 ──

    private House house(long id, String community, String district, String layout, String rent) {
        House h = new House();
        h.setId(id);
        h.setCommunity(community);
        h.setDistrict(district);
        h.setLayout(layout);
        h.setCity("大连市");
        h.setTitle("精装一居室");
        h.setOrientation("南");
        h.setFloorDesc("中层");
        h.setArea(new BigDecimal("45"));
        h.setRent(new BigDecimal(rent));
        h.setDepositType("押一付三");
        h.setFacilities(List.of("近地铁"));
        h.setDescription("近地铁精装，家电齐全，周边配套成熟，适合上班族长期居住，看房方便。");
        h.setStatus(HouseService.ST_ONLINE);
        return h;
    }

    private House draft() {
        House h = new House();
        h.setCommunity("软件园公寓");
        h.setDistrict("高新园区");
        h.setLayout("1室1厅");
        h.setRent(new BigDecimal("2100"));
        return h;
    }

    private void assertCode(int expected, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(expected, e.getCode(), "错误码不符，实际 message=" + e.getMessage());
    }

    // ── FR-15 智能定价建议 ──

    @Test
    @DisplayName("UT-AI-01 同小区样本充足：统计口径与提示词都带上真实样本，区间取自模型")
    void pricingUsesSamplesAndModelRange() {
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "2000"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "2200"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "2400")));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("2024"), new BigDecimal("2376"),
                        "同小区三套样本均价 2200 元，结合房源条件给出区间", "可按楼层与朝向微调"));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(new BigDecimal("2024"), vo.low());
        assertEquals(new BigDecimal("2376"), vo.high());
        assertEquals(new BigDecimal("2200.00"), vo.avg());
        assertEquals(3, vo.sampleCount());
        assertEquals(ENGINE, vo.model());
        assertEquals("可按楼层与朝向微调", vo.note());
        String sent = payload.getValue();
        assertTrue(sent.contains("【同类样本统计】"), sent);
        assertTrue(sent.contains("均价 2200.00 元"), sent);
        assertTrue(sent.contains("中位 2200.00 元"), sent);
        assertTrue(sent.contains("最低 2000 元") && sent.contains("最高 2400 元"), sent);
        assertTrue(sent.contains("软件园公寓"), sent);
    }

    @Test
    @DisplayName("UT-AI-02 同小区不足 3 套时回退到同区域同户型样本，且依据带样本量")
    void pricingFallsBackWhenSamplesScarce() {
        when(houseMapper.selectList(any()))
                .thenReturn(List.of(house(1L, "软件园公寓", "高新园区", "1室1厅", "2000")))
                .thenReturn(List.of(house(2L, "滨海公寓", "高新园区", "1室1厅", "3000"),
                        house(3L, "阳光家园", "高新园区", "1室1厅", "4000"),
                        house(4L, "东软家园", "高新园区", "1室1厅", "5000")));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("3500"), new BigDecimal("4500"), "", ""));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertTrue(payload.getValue().contains("同区域同户型（高新园区 · 1室1厅）"), payload.getValue());
        assertEquals(3, vo.sampleCount());
        // 模型未给依据时用样本口径兜底，保证"依据 + 样本量"始终可展示
        assertTrue(vo.basis().contains("同区域同户型"), vo.basis());
        assertTrue(vo.basis().endsWith("（n=3）"), vo.basis());
    }

    @Test
    @DisplayName("UT-AI-03 两级样本皆空：提示模型样本不足，样本量为 0 且均价为空")
    void pricingWithoutSamplesStillUsesModel() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("1900"), new BigDecimal("2100"),
                        "平台暂无同类样本，按房源自身条件给保守区间", ""));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertTrue(payload.getValue().contains("无同类样本"), payload.getValue());
        assertTrue(payload.getValue().contains("样本不足"), payload.getValue());
        assertEquals(0, vo.sampleCount());
        assertNull(vo.avg());
        assertTrue(vo.basis().endsWith("（n=0）"), vo.basis());
    }

    @Test
    @DisplayName("UT-AI-04 模型区间上下限颠倒时按大小归一")
    void pricingNormalizesReversedRange() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("2600"), new BigDecimal("2200"), "依据", "建议"));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(new BigDecimal("2200"), vo.low());
        assertEquals(new BigDecimal("2600"), vo.high());
    }

    @Test
    @DisplayName("UT-AI-05 模型未返回有效区间时报 4001 且不落库")
    void pricingFailsWithoutRange() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(null, new BigDecimal("2200"), "依据", ""));

        assertCode(4001, () -> service.pricing(draft(), 7L));

        verify(analysisMapper, never()).insert(any(AiAnalysis.class));
    }

    @Test
    @DisplayName("UT-AI-06 未配置模型（调用层抛 4001）时如实透传，不算成功")
    void pricingFailsWithoutModel() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenThrow(new BizException(4001, "未配置大模型服务：请注入模型凭据"));

        assertCode(4001, () -> service.pricing(draft(), 7L));

        verify(analysisMapper, never()).insert(any(AiAnalysis.class));
    }

    @Test
    @DisplayName("UT-AI-07 定价结果落库：type=1、引擎标识为真实模型、输入快照与输出均可复现")
    void pricingPersistsResult() {
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "2000"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "2200"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "2400")));
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("2024"), new BigDecimal("2376"), "依据", "建议"));

        service.pricing(draft(), 7L);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        AiAnalysis saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals(1, saved.getType());
        assertEquals("draft", saved.getTargetType());
        assertEquals(0L, saved.getTargetId());
        assertEquals(ENGINE, saved.getModel());
        assertTrue(saved.getInputSnapshot().contains("软件园公寓"));
        assertTrue(saved.getResult().contains("sampleCount"));
    }

    @Test
    @DisplayName("UT-AI-08 引擎标识超长按列宽截断到 100 字符")
    void truncatesEngineLabel() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(llmGateway.describe()).thenReturn("p".repeat(150));
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("2024"), new BigDecimal("2376"), "依据", ""));

        service.pricing(draft(), 7L);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        assertEquals(100, captor.getValue().getModel().length());
    }

    @Test
    @DisplayName("UT-AI-09 落库异常不影响结果返回（NFR-07 降级）")
    void pricingSurvivesPersistenceFailure() {
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.PricingAi(new BigDecimal("2024"), new BigDecimal("2376"), "依据", ""));
        when(analysisMapper.insert(any(AiAnalysis.class))).thenThrow(new RuntimeException("db down"));

        AiDto.PricingVO vo = service.pricing(draft(), 7L);

        assertEquals(new BigDecimal("2024"), vo.low());
    }

    // ── FR-16 虚假房源检测 ──

    @Test
    @DisplayName("UT-AI-10 检测：房源信息与同区域样本统计送模型，风险分与疑点取模型结果")
    void detectUsesModelResult() {
        House target = house(101L, "软件园公寓", "高新园区", "1室1厅", "1000");
        when(houseMapper.selectById(101L)).thenReturn(target);
        when(houseMapper.selectList(any())).thenReturn(List.of(
                house(1L, "软件园公寓", "高新园区", "1室1厅", "3000"),
                house(2L, "软件园公寓", "高新园区", "1室1厅", "3000"),
                house(3L, "软件园公寓", "高新园区", "1室1厅", "3000")));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.DetectAi(80, List.of("租金显著低于同区域均价"), "建议优先人工复核"));

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(80, vo.riskScore());
        assertEquals(1, vo.suspicions().size());
        assertEquals("建议优先人工复核", vo.suggestion());
        assertEquals(ENGINE, vo.model());
        assertTrue(payload.getValue().contains("【同区域（高新园区）在租/在售样本统计】"), payload.getValue());
        assertTrue(payload.getValue().contains("均价 3000.00 元"), payload.getValue());
        assertTrue(payload.getValue().contains("挂牌月租：1000"), payload.getValue());
    }

    @Test
    @DisplayName("UT-AI-11 模型风险分越界时按 0~100 归一")
    void detectNormalizesRiskScore() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, "软件园公寓", "高新园区", "1室1厅", "3000"));
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.DetectAi(150, null, "建议核实"));

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(100, vo.riskScore());
        assertTrue(vo.suspicions().isEmpty());
    }

    @Test
    @DisplayName("UT-AI-12 模型未给处置建议时用显式标注的系统提示兜底")
    void detectFallsBackToDefaultSuggestion() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, "软件园公寓", "高新园区", "1室1厅", "3000"));
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.DetectAi(10, List.of(), "  "));

        AiDto.DetectVO vo = service.fakeDetect(101L, 1L);

        assertEquals(10, vo.riskScore());
        assertTrue(vo.suggestion().startsWith("（系统提示）"), vo.suggestion());
    }

    @Test
    @DisplayName("UT-AI-13 模型未返回风险分时报 4001")
    void detectFailsWithoutRiskScore() {
        when(houseMapper.selectById(101L)).thenReturn(house(101L, "软件园公寓", "高新园区", "1室1厅", "3000"));
        when(houseMapper.selectList(any())).thenReturn(List.of());
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.DetectAi(null, List.of(), "建议"));

        assertCode(4001, () -> service.fakeDetect(101L, 1L));
    }

    @Test
    @DisplayName("UT-AI-14 房源不存在返回 2001 且不调用模型")
    void detectFailsWhenHouseMissing() {
        when(houseMapper.selectById(999L)).thenReturn(null);

        assertCode(2001, () -> service.fakeDetect(999L, 1L));

        verify(aiJsonClient, never()).call(anyString(), anyString(), any(Class.class));
    }

    // ── FR-14 合同智能解读 ──

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

    private List<Map<String, String>> demoClauses() {
        return List.of(
                Map.of("title", "租赁标的", "text", "甲方将位于高新园区的房屋出租给乙方居住使用。"),
                Map.of("title", "违约责任", "text", "乙方提前退租的押金不予退还。"));
    }

    @Test
    @DisplayName("UT-AI-15 解读权限：合同不存在 3004、非合同双方 1007，均不调用模型")
    void interpretChecksOwnership() {
        when(contractMapper.selectById(999L)).thenReturn(null);
        assertCode(3004, () -> service.interpret(999L, 2L));

        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, demoClauses()));
        assertCode(1007, () -> service.interpret(88L, 99L));

        verify(aiJsonClient, never()).call(anyString(), anyString(), any(JavaType.class));
    }

    @Test
    @DisplayName("UT-AI-16 合同条款 JSON 损坏时报 4001，不调用模型")
    void interpretFailsOnBrokenClauses() {
        Contract c = new Contract();
        c.setId(88L);
        c.setTenantId(2L);
        c.setLandlordId(7L);
        c.setClauses("{不是合法 JSON");
        when(contractMapper.selectById(88L)).thenReturn(c);

        assertCode(4001, () -> service.interpret(88L, 2L));

        verify(aiJsonClient, never()).call(anyString(), anyString(), any(JavaType.class));
    }

    @Test
    @DisplayName("UT-AI-17 解读：条款原文与 index 送模型，逐条解释与风险标记按 index 回填")
    void interpretMapsClausesByIndex() {
        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, demoClauses()));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(JavaType.class)))
                .thenReturn(List.of(
                        new AnalysisService.InterpAi(0, false, "这一条说明房子在哪里、由谁出租。"),
                        new AnalysisService.InterpAi(1, true, "提前退租会损失押金，退租前务必提前协商。")));

        AiDto.InterpVO vo = service.interpret(88L, 2L);

        assertEquals(2, vo.items().size());
        assertEquals(0, vo.items().get(0).index());
        assertTrue(vo.items().get(0).clause().contains("甲方将位于高新园区"));
        assertEquals(false, vo.items().get(0).risk());
        assertTrue(vo.items().get(1).clause().contains("押金不予退还"));
        assertEquals(true, vo.items().get(1).risk());
        assertTrue(vo.items().get(1).explanation().contains("押金"));
        assertTrue(vo.disclaimer().contains("不构成法律意见"));
        assertEquals(ENGINE, vo.model());
        assertTrue(payload.getValue().contains("index=0"), payload.getValue());
        assertTrue(payload.getValue().contains("index=1"), payload.getValue());
        assertTrue(payload.getValue().contains("标题：违约责任"), payload.getValue());
    }

    @Test
    @DisplayName("UT-AI-18 模型漏解某条条款时报 4001（不允许缺条，避免漏掉风险条款）")
    void interpretFailsOnMissingClause() {
        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, demoClauses()));
        when(aiJsonClient.call(anyString(), anyString(), any(JavaType.class)))
                .thenReturn(List.of(new AnalysisService.InterpAi(0, false, "只解释了第一条。")));

        assertCode(4001, () -> service.interpret(88L, 2L));
    }

    @Test
    @DisplayName("UT-AI-19 模型返回空数组时报 4001")
    void interpretFailsOnEmptyResult() {
        when(contractMapper.selectById(88L)).thenReturn(contract(88L, 2L, 7L, demoClauses()));
        when(aiJsonClient.call(anyString(), anyString(), any(JavaType.class))).thenReturn(List.of());

        assertCode(4001, () -> service.interpret(88L, 2L));
    }

    @Test
    @DisplayName("UT-AI-20 风险条款下标回写 risk_flags，解读结果落库 type=3")
    void interpretWritesRiskFlagsAndPersists() {
        Contract c = contract(88L, 2L, 7L, demoClauses());
        when(contractMapper.selectById(88L)).thenReturn(c);
        when(aiJsonClient.call(anyString(), anyString(), any(JavaType.class)))
                .thenReturn(List.of(
                        new AnalysisService.InterpAi(0, false, "第一条说明。"),
                        new AnalysisService.InterpAi(1, true, "第二条有风险。")));

        service.interpret(88L, 2L);

        // 只回写 risk_flags 一列：模型调用耗时以秒计，整行 updateById 会把并发期间的签约/退租状态覆盖回旧快照
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Contract>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(contractMapper).update(eq(null), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getParamNameValuePairs().containsValue("[1]"),
                "risk_flags 应写为 [1]，实际参数：" + updateCaptor.getValue().getParamNameValuePairs());
        verify(contractMapper, never()).updateById(any(Contract.class));
        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(analysisMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getType());
        assertEquals("contract", captor.getValue().getTargetType());
    }

    // ── FR-08 房源信息智能识别填充 ──

    @Test
    @DisplayName("UT-AI-21 智能识别：房东输入送模型，描述/朝向/楼层/设施取模型结果")
    void assistFillUsesModelResult() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.FillAi("位于软件园公寓的两居室，近地铁，采光良好，适合通勤。",
                        "南北", "高层", List.of("近地铁", "精装修")));

        HouseDto.AiFillVO vo = service.assistFill(
                new HouseDto.AiFillReq("近地铁精装两居", "软件园公寓", "2室1厅", "IMG_0021.jpg"), 7L);

        assertEquals("南北", vo.orientation());
        assertEquals("高层", vo.floorDesc());
        assertEquals(List.of("近地铁", "精装修"), vo.facilities());
        assertTrue(vo.description().contains("软件园公寓"));
        assertTrue(payload.getValue().contains("标题：近地铁精装两居"), payload.getValue());
        assertTrue(payload.getValue().contains("户型：2室1厅"), payload.getValue());
        assertTrue(payload.getValue().contains("图片文件名：IMG_0021.jpg"), payload.getValue());
    }

    @Test
    @DisplayName("UT-AI-22 模型未返回描述或设施时报 4001")
    void assistFillFailsWithoutRequiredFields() {
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.FillAi("  ", "南", "中层", List.of("近地铁")));
        assertCode(4001, () -> service.assistFill(new HouseDto.AiFillReq("标题", null, null, null), 7L));

        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.FillAi("描述内容", "南", "中层", List.of()));
        assertCode(4001, () -> service.assistFill(new HouseDto.AiFillReq("标题", null, null, null), 7L));
    }

    @Test
    @DisplayName("UT-AI-23 模型未给朝向/楼层时取表单默认值（仍允许人工修改）")
    void assistFillAppliesDefaults() {
        when(aiJsonClient.call(anyString(), anyString(), any(Class.class)))
                .thenReturn(new AnalysisService.FillAi("描述内容", null, "", List.of("家电齐全")));

        HouseDto.AiFillVO vo = service.assistFill(new HouseDto.AiFillReq("标题", null, null, null), 7L);

        assertEquals("南", vo.orientation());
        assertEquals("中层", vo.floorDesc());
    }

    @Test
    @DisplayName("UT-AI-24 提示词装配：未填写的输入项显式标注为未填写")
    void assistFillMarksMissingInput() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(aiJsonClient.call(anyString(), payload.capture(), any(Class.class)))
                .thenReturn(new AnalysisService.FillAi("描述内容", "南", "中层", List.of("近地铁")));

        service.assistFill(new HouseDto.AiFillReq(null, null, null, null), 7L);

        assertTrue(payload.getValue().contains("（未填写）"), payload.getValue());
    }
}
