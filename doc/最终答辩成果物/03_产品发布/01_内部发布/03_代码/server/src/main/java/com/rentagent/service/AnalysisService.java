package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.agent.AiJsonClient;
import com.rentagent.agent.AiPrompts;
import com.rentagent.agent.LlmGateway;
import com.rentagent.common.BizException;
import com.rentagent.common.ErrorCode;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.AiAnalysis;
import com.rentagent.entity.Contract;
import com.rentagent.entity.House;
import com.rentagent.mapper.AiAnalysisMapper;
import com.rentagent.mapper.ContractMapper;
import com.rentagent.mapper.HouseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 分析服务（FR-08/14/15/16）：定价建议、虚假房源检测、合同解读、房源信息识别填充。
 * <p>
 * 设计口径（2026-09-17 用户拍板）：四项能力**全部调用真实大模型**，本类不再包含任何本地规则兜底。
 * 代码只负责"数据事实"与"结果校验"：
 * <ul>
 *   <li>数据事实：同类样本统计（同小区 → 同区域同户型两级回退）、条款原文、房源字段——作为提示词输入喂给模型；</li>
 *   <li>结果校验：模型输出的字段完整性、取值域（如风险分 0~100）与逐条覆盖，不合规由 {@link AiJsonClient} 重试一次后报 4001。</li>
 * </ul>
 * 全部结果落 ai_analysis 表（输入快照 + 输出 + 实际引擎标识），支持 NFR-05 的可追溯与复现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    /** ai_analysis.model 列宽（超出截断） */
    private static final int MODEL_COLUMN_MAX = 100;
    /** 送模型统计的同类样本上限 */
    private static final int SAMPLE_LIMIT = 100;
    /** 样本量达到该值才认为"样本充足" */
    private static final int SAMPLE_ENOUGH = 3;

    private final AiAnalysisMapper analysisMapper;
    private final HouseMapper houseMapper;
    private final ContractMapper contractMapper;
    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;
    private final AiJsonClient aiJsonClient;

    // ────────────────────────── 模型输出结构 ──────────────────────────

    /** FR-15 模型返回的定价建议 */
    public record PricingAi(BigDecimal low, BigDecimal high, String basis, String note) {
    }

    /** FR-16 模型返回的风险评估 */
    public record DetectAi(Integer riskScore, List<String> suspicions, String suggestion) {
    }

    /** FR-14 模型返回的单条解读 */
    public record InterpAi(Integer index, Boolean risk, String explanation) {
    }

    /** FR-08 模型返回的填充建议 */
    public record FillAi(String description, String orientation, String floorDesc, List<String> facilities) {
    }

    /** 同类样本统计（代码侧事实，送模型作为定价/风控依据） */
    public record SampleStats(int count, BigDecimal avg, BigDecimal median, BigDecimal min, BigDecimal max) {

        static SampleStats of(List<House> houses) {
            List<BigDecimal> rents = houses.stream().map(House::getRent)
                    .filter(Objects::nonNull).sorted().toList();
            if (rents.isEmpty()) {
                return new SampleStats(0, null, null, null, null);
            }
            BigDecimal sum = rents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(rents.size()), 2, RoundingMode.HALF_UP);
            BigDecimal median = rents.size() % 2 == 1
                    ? rents.get(rents.size() / 2)
                    : rents.get(rents.size() / 2 - 1).add(rents.get(rents.size() / 2))
                            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            // 统一保留 2 位小数，避免奇数样本时中位数与均值的小数位不一致，影响展示与提示词口径
            return new SampleStats(rents.size(), avg, median.setScale(2, RoundingMode.HALF_UP),
                    rents.get(0), rents.get(rents.size() - 1));
        }

        String describe() {
            if (count == 0) {
                return "无同类样本（同小区与同区域同户型均无在租/在售房源）";
            }
            return "共 " + count + " 套；均价 " + avg + " 元，中位 " + median + " 元，最低 " + min
                    + " 元，最高 " + max + " 元" + (count < SAMPLE_ENOUGH ? "（样本量不足 " + SAMPLE_ENOUGH + " 套）" : "");
        }
    }

    // ────────────────────────── FR-15 智能定价建议 ──────────────────────────

    /**
     * FR-15 入口（按房源 id）：查库与"仅本人房源"的越权校验都放在服务层，
     * 控制器只负责编排——早先这段写在控制器里并直接返回 {@code R.err(2001/1007)}，
     * 与全仓"抛业务异常交给全局处理器"的口径不一致，且任何新调用方都会漏掉校验。
     */
    public AiDto.PricingVO pricing(long houseId, long uid) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND);
        }
        if (!Objects.equals(house.getLandlordId(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "仅可对自己发布的房源获取定价建议");
        }
        return pricing(house, uid);
    }

    public AiDto.PricingVO pricing(House draft, long uid) {
        List<House> sample = sameCommunitySamples(draft);
        String scope = "同小区「" + draft.getCommunity() + "」在租/在售样本";
        if (sample.size() < SAMPLE_ENOUGH) {
            sample = sameDistrictLayoutSamples(draft);
            scope = "同区域同户型（" + draft.getDistrict() + " · " + draft.getLayout() + "）在租/在售样本";
        }
        SampleStats stats = SampleStats.of(sample);

        PricingAi ai = aiJsonClient.call(AiPrompts.PRICING, pricingPayload(draft, scope, stats), PricingAi.class);
        if (ai == null || ai.low() == null || ai.high() == null
                || ai.low().signum() <= 0 || ai.high().signum() <= 0) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "模型未返回有效的租金区间，请重试");
        }
        BigDecimal low = ai.low();
        BigDecimal high = ai.high();
        if (low.compareTo(high) > 0) {
            log.warn("模型返回的区间上下限颠倒（low={} high={}），已按大小归一", low, high);
            BigDecimal swap = low;
            low = high;
            high = swap;
        }
        String basis = ai.basis() == null || ai.basis().isBlank() ? scope : ai.basis().trim();
        String note = ai.note() == null ? "" : ai.note().trim();

        AiDto.PricingVO vo = new AiDto.PricingVO(low, high, stats.avg(),
                basis + "（n=" + stats.count() + "）", stats.count(), note, engineLabel());
        save(uid, 1, draft.getId() == null ? "draft" : "house", draft.getId() == null ? 0L : draft.getId(),
                draft, vo);
        return vo;
    }

    private List<House> sameCommunitySamples(House draft) {
        return houseMapper.selectList(new LambdaQueryWrapper<House>()
                .in(House::getStatus, HouseService.ST_PASSED, HouseService.ST_ONLINE, HouseService.ST_RENTED)
                .eq(House::getCommunity, draft.getCommunity())
                .ne(draft.getId() != null, House::getId, draft.getId())
                .last("LIMIT " + SAMPLE_LIMIT));
    }

    private List<House> sameDistrictLayoutSamples(House draft) {
        return houseMapper.selectList(new LambdaQueryWrapper<House>()
                .in(House::getStatus, HouseService.ST_PASSED, HouseService.ST_ONLINE, HouseService.ST_RENTED)
                .eq(House::getDistrict, draft.getDistrict())
                .eq(House::getLayout, draft.getLayout())
                .ne(draft.getId() != null, House::getId, draft.getId())
                .last("LIMIT " + SAMPLE_LIMIT));
    }

    private String pricingPayload(House draft, String scope, SampleStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("【待定价房源】\n").append(houseLines(draft));
        sb.append("\n【同类样本统计】\n来源：").append(scope).append("\n统计：").append(stats.describe()).append('\n');
        if (stats.count() == 0) {
            sb.append("说明：平台当前无同类样本，请依据房源自身条件给出保守区间，并在 basis 中明确说明样本不足。\n");
        } else if (stats.count() < SAMPLE_ENOUGH) {
            sb.append("说明：样本量不足，请给出偏保守的区间并在 basis 中说明。\n");
        }
        return sb.toString();
    }

    // ────────────────────────── FR-16 虚假房源检测 ──────────────────────────

    public AiDto.DetectVO fakeDetect(long houseId, long adminId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND.getCode(), "房源不存在");
        }
        List<House> peers = houseMapper.selectList(new LambdaQueryWrapper<House>()
                .in(House::getStatus, HouseService.ST_ONLINE, HouseService.ST_PASSED)
                .eq(House::getDistrict, house.getDistrict())
                .last("LIMIT 50"));
        SampleStats stats = SampleStats.of(peers);

        DetectAi ai = aiJsonClient.call(AiPrompts.FAKE_DETECT, detectPayload(house, stats), DetectAi.class);
        if (ai == null || ai.riskScore() == null) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "模型未返回风险分，请重试");
        }
        int score = Math.max(0, Math.min(100, ai.riskScore()));
        if (score != ai.riskScore()) {
            log.warn("模型返回的风险分超出 0~100（{}），已按边界归一", ai.riskScore());
        }
        List<String> suspicions = ai.suspicions() == null ? List.of() : ai.suspicions();
        String suggestion = ai.suggestion() == null || ai.suggestion().isBlank()
                ? "（系统提示）请结合房源图片与同区域租金水平人工复核。" : ai.suggestion().trim();

        AiDto.DetectVO vo = new AiDto.DetectVO(score, suspicions, suggestion, engineLabel());
        save(adminId, 2, "house", houseId, Map.of("houseId", houseId, "title", house.getTitle()), vo);
        return vo;
    }

    private String detectPayload(House house, SampleStats stats) {
        return "【待核查房源】\n" + houseLines(house)
                + "\n【同区域（" + house.getDistrict() + "）在租/在售样本统计】\n统计：" + stats.describe() + '\n';
    }

    private String houseLines(House h) {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(h.getTitle()).append('\n');
        sb.append("区域：").append(h.getCity()).append(h.getDistrict()).append(h.getCommunity()).append('\n');
        sb.append("户型：").append(h.getLayout()).append("；面积：").append(h.getArea()).append(" ㎡\n");
        sb.append("朝向：").append(h.getOrientation()).append("；楼层：").append(h.getFloorDesc()).append('\n');
        sb.append("押付方式：").append(h.getDepositType()).append("；标签：")
                .append(h.getFacilities() == null ? "无" : String.join("、", h.getFacilities())).append('\n');
        sb.append("挂牌月租：").append(h.getRent()).append(" 元\n");
        sb.append("描述：").append(h.getDescription() == null ? "（房东未填写）" : h.getDescription()).append('\n');
        return sb.toString();
    }

    // ────────────────────────── FR-14 合同智能解读 ──────────────────────────

    public AiDto.InterpVO interpret(long contractId, long uid) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND.getCode(), "合同不存在");
        }
        if (contract.getTenantId() != uid && contract.getLandlordId() != uid) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "仅合同双方可解读");
        }
        List<Map<String, String>> clauses = parseClauses(contract.getClauses());
        List<InterpAi> aiItems = aiJsonClient.call(AiPrompts.CONTRACT_INTERPRET, interpretPayload(clauses),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InterpAi.class));
        if (aiItems == null || aiItems.isEmpty()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "模型未返回条款解读内容，请重试");
        }
        Map<Integer, InterpAi> byIndex = new LinkedHashMap<>();
        for (InterpAi item : aiItems) {
            if (item != null && item.index() != null && item.explanation() != null && !item.explanation().isBlank()) {
                byIndex.putIfAbsent(item.index(), item);
            }
        }
        List<AiDto.InterpItem> items = new ArrayList<>();
        for (int i = 0; i < clauses.size(); i++) {
            InterpAi ai = byIndex.get(i);
            if (ai == null) {
                throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(),
                        "模型未逐条覆盖合同条款（缺少第 " + (i + 1) + " 条），请重试");
            }
            items.add(new AiDto.InterpItem(i, clauses.get(i).getOrDefault("text", ""),
                    Boolean.TRUE.equals(ai.risk()), ai.explanation().trim()));
        }
        AiDto.InterpVO vo = new AiDto.InterpVO(items,
                "AI 生成，仅供参考，不构成法律意见；重要条款建议咨询专业人士", engineLabel());
        save(uid, 3, "contract", contractId, Map.of("contractId", contractId), vo);
        writeRiskFlags(contract.getId(), items);
        return vo;
    }

    private List<Map<String, String>> parseClauses(String clausesJson) {
        try {
            return objectMapper.readValue(clausesJson, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "合同条款解析失败");
        }
    }

    private String interpretPayload(List<Map<String, String>> clauses) {
        StringBuilder sb = new StringBuilder("【合同条款】\n");
        for (int i = 0; i < clauses.size(); i++) {
            Map<String, String> clause = clauses.get(i);
            sb.append("index=").append(i)
                    .append(" 标题：").append(clause.getOrDefault("title", ""))
                    .append("；正文：").append(clause.getOrDefault("text", "")).append('\n');
        }
        return sb.toString();
    }

    /**
     * 风险条款下标回写 contract.risk_flags（前端据此在合同上标红）。
     * <p>
     * 只更新这一列：模型调用耗时以秒计，若用 updateById 整行回写，期间并发发生的
     * 签约/退租状态会被这里携带的旧快照覆盖回去（合同状态被静默回退）。
     */
    private void writeRiskFlags(Long contractId, List<AiDto.InterpItem> items) {
        try {
            String riskFlags = objectMapper.writeValueAsString(
                    items.stream().filter(AiDto.InterpItem::risk).map(AiDto.InterpItem::index).toList());
            contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                    .eq(Contract::getId, contractId)
                    .set(Contract::getRiskFlags, riskFlags));
        } catch (Exception e) {
            log.warn("风险标注写回失败: {}", e.getMessage());
        }
    }

    // ────────────────────────── FR-08 房源信息智能识别 ──────────────────────────

    public HouseDto.AiFillVO assistFill(HouseDto.AiFillReq req, long uid) {
        FillAi ai = aiJsonClient.call(AiPrompts.ASSIST_FILL, fillPayload(req), FillAi.class);
        if (ai == null || ai.description() == null || ai.description().isBlank()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "模型未返回房源描述，请重试");
        }
        List<String> facilities = ai.facilities() == null ? List.of() : ai.facilities();
        if (facilities.isEmpty()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE.getCode(), "模型未返回设施标签，请重试");
        }
        HouseDto.AiFillVO vo = new HouseDto.AiFillVO(ai.description().trim(),
                blankTo(ai.orientation(), "南"), blankTo(ai.floorDesc(), "中层"), facilities);
        save(uid, 4, "draft", 0L, req, vo);
        return vo;
    }

    private String fillPayload(HouseDto.AiFillReq req) {
        return "【房东填写的信息】\n标题：" + nvl(req.title()) + "\n小区：" + nvl(req.community())
                + "\n户型：" + nvl(req.layout()) + "\n图片文件名：" + nvl(req.imageFileName()) + '\n';
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "（未填写）" : s;
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    // ────────────────────────── 落库与引擎标识 ──────────────────────────

    private void save(long uid, int type, String targetType, Long targetId, Object input, Object result) {
        try {
            AiAnalysis a = new AiAnalysis();
            a.setUserId(uid);
            a.setType(type);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setInputSnapshot(objectMapper.writeValueAsString(input));
            a.setResult(objectMapper.writeValueAsString(result));
            a.setModel(engineLabel());
            analysisMapper.insert(a);
        } catch (Exception e) {
            log.warn("AI 分析结果落库失败: {}", e.getMessage());
        }
    }

    /** 落库用的引擎标识（协议:模型）；列宽有限，超出即截断 */
    private String engineLabel() {
        String label = llmGateway.describe();
        return label.length() > MODEL_COLUMN_MAX ? label.substring(0, MODEL_COLUMN_MAX) : label;
    }
}
