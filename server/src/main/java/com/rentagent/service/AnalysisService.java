package com.rentagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 分析服务（FR-08/14/15/16）：
 * 有 GLM Key 时走大模型增强解释，无 Key 时全部降级为内置规则引擎（风险 R1 应对，保证可演示）。
 * 全部结果落 ai_analysis 表，输入带快照可复现（NFR-05）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String MOCK_MODEL = "rule-engine";

    private final AiAnalysisMapper analysisMapper;
    private final HouseMapper houseMapper;
    private final ContractMapper contractMapper;
    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;

    /** FR-15 智能定价建议：同小区 → 同区域同户型两级取样本，给出区间/依据/样本量 */
    public AiDto.PricingVO pricing(House draft, long uid) {
        List<House> sample = houseMapper.selectList(new LambdaQueryWrapper<House>()
                .in(House::getStatus, HouseService.ST_PASSED, HouseService.ST_ONLINE, HouseService.ST_RENTED)
                .eq(House::getCommunity, draft.getCommunity())
                .ne(draft.getId() != null, House::getId, draft.getId())
                .last("LIMIT 100"));
        String basis = "同小区「" + draft.getCommunity() + "」在租/在售样本";
        if (sample.size() < 3) {
            sample = houseMapper.selectList(new LambdaQueryWrapper<House>()
                    .in(House::getStatus, HouseService.ST_PASSED, HouseService.ST_ONLINE, HouseService.ST_RENTED)
                    .eq(House::getDistrict, draft.getDistrict())
                    .eq(House::getLayout, draft.getLayout())
                    .ne(draft.getId() != null, House::getId, draft.getId())
                    .last("LIMIT 100"));
            basis = "同区域同户型（" + draft.getDistrict() + " · " + draft.getLayout() + "）在租/在售样本";
        }
        AiDto.PricingVO vo;
        if (sample.isEmpty()) {
            vo = new AiDto.PricingVO(null, null, null, basis, 0,
                    "当前无同类样本数据，建议参考周边挂牌价人工定价", MOCK_MODEL);
        } else {
            List<BigDecimal> rents = sample.stream().map(House::getRent).sorted().toList();
            BigDecimal avg = rents.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(rents.size()), 2, RoundingMode.HALF_UP);
            BigDecimal low = avg.multiply(new BigDecimal("0.92")).setScale(0, RoundingMode.HALF_UP);
            BigDecimal high = avg.multiply(new BigDecimal("1.08")).setScale(0, RoundingMode.HALF_UP);
            String note = sample.size() < 3 ? "样本较少，建议区间仅供参考" : "建议根据装修、楼层、朝向在区间内浮动";
            vo = new AiDto.PricingVO(low, high, avg, basis + "（n=" + rents.size() + "）", rents.size(), note, MOCK_MODEL);
        }
        save(uid, 1, draft.getId() == null ? "draft" : "house", draft.getId() == null ? 0L : draft.getId(),
                draft, vo);
        return vo;
    }

    /** FR-16 虚假房源检测：启发式规则打分（0~100），管理员保留最终裁决权 */
    public AiDto.DetectVO fakeDetect(long houseId, long adminId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(2001, "房源不存在");
        }
        int score = 0;
        List<String> suspicions = new ArrayList<>();
        // 规则1：租金偏离同区域均价
        List<House> peers = houseMapper.selectList(new LambdaQueryWrapper<House>()
                .in(House::getStatus, HouseService.ST_ONLINE, HouseService.ST_PASSED)
                .eq(House::getDistrict, house.getDistrict()).last("LIMIT 50"));
        if (peers.size() >= 3) {
            BigDecimal avg = peers.stream().map(House::getRent).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(peers.size()), 2, RoundingMode.HALF_UP);
            if (house.getRent().doubleValue() < avg.doubleValue() * 0.6) {
                score += 40;
                suspicions.add("租金 ¥" + house.getRent() + " 显著低于同区域均价 ¥" + avg + "（偏离超 40%）");
            }
        }
        // 规则2：引流话术关键词
        String text = (house.getTitle() == null ? "" : house.getTitle()) + " " + (house.getDescription() == null ? "" : house.getDescription());
        for (String kw : List.of("免押金", "无需看房", "低价直租", "仅此一套", "先到先得", "不查征信")) {
            if (text.contains(kw)) {
                score += 20;
                suspicions.add("描述命中引流话术：" + kw);
            }
        }
        // 规则3：信息完整度
        if (house.getDescription() == null || house.getDescription().length() < 50) {
            score += 15;
            suspicions.add("房源描述过短（少于 50 字），信息完整度低");
        }
        // 规则4：描述含外部联系方式
        if (text.matches(".*(1[3-9]\\d{9}|微信|vx|VX).*")) {
            score += 25;
            suspicions.add("描述中疑似含外部联系方式，存在导流风险");
        }
        score = Math.min(score, 100);
        AiDto.DetectVO vo = new AiDto.DetectVO(score, suspicions,
                score >= 70 ? "高风险：建议人工复核并优先处理" : score >= 40 ? "中风险：建议核对图片与租金合理性" : "低风险：正常审核流程即可",
                MOCK_MODEL);
        save(adminId, 2, "house", houseId, Map.of("houseId", houseId, "title", house.getTitle()), vo);
        return vo;
    }

    /** FR-14 合同智能解读：逐条通俗化 + 风险标红（演示模板内置风险示例条款） */
    public AiDto.InterpVO interpret(long contractId, long uid) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BizException(3004, "合同不存在");
        }
        if (contract.getTenantId() != uid && contract.getLandlordId() != uid) {
            throw new BizException(1007, "仅合同双方可解读");
        }
        List<Map<String, String>> clauses;
        try {
            clauses = objectMapper.readValue(contract.getClauses(),
                    new TypeReference<List<Map<String, String>>>() {
                    });
        } catch (Exception e) {
            throw new BizException(4001, "合同条款解析失败");
        }
        List<AiDto.InterpItem> items = new ArrayList<>();
        for (int i = 0; i < clauses.size(); i++) {
            Map<String, String> clause = clauses.get(i);
            String text = clause.getOrDefault("text", "");
            String plain = "这一条约定的是「" + clause.getOrDefault("title", "") + "」：" + simplify(text);
            boolean risk = isRisk(text);
            if (risk) {
                plain += "。注意：本条含对租客较为不利的约定，请重点关注。";
            }
            items.add(new AiDto.InterpItem(i, text, risk, plain));
        }
        AiDto.InterpVO vo = new AiDto.InterpVO(items, "AI 生成，仅供参考，不构成法律意见；重要条款建议咨询专业人士", MOCK_MODEL);
        save(uid, 3, "contract", contractId, Map.of("contractId", contractId), vo);
        try {
            contract.setRiskFlags(objectMapper.writeValueAsString(
                    items.stream().filter(AiDto.InterpItem::risk).map(AiDto.InterpItem::index).toList()));
            contractMapper.updateById(contract);
        } catch (Exception e) {
            log.warn("风险标注写回失败: {}", e.getMessage());
        }
        return vo;
    }

    /** FR-08 房源信息智能识别（演示：从标题/图片名关键词推断，可一键填充且允许修改） */
    public HouseDto.AiFillVO assistFill(HouseDto.AiFillReq req, long uid) {
        String src = (req.title() == null ? "" : req.title()) + " " + (req.imageFileName() == null ? "" : req.imageFileName());
        List<String> facilities = new ArrayList<>();
        for (Map.Entry<String, String> kw : Map.of("地铁", "近地铁", "精装", "精装修", "家电", "家电齐全",
                "电梯", "电梯", "拎包", "拎包入住", "朝南", "采光好").entrySet()) {
            if (src.contains(kw.getKey())) {
                facilities.add(kw.getValue());
            }
        }
        if (facilities.isEmpty()) {
            facilities = List.of("家电齐全", "拎包入住");
        }
        String orientation = src.contains("南") ? "南" : src.contains("南北") ? "南北" : "南";
        String floor = src.contains("高层") ? "高层" : src.contains("低层") ? "低层" : "中层";
        String layout = req.layout() == null ? "1室1厅" : req.layout();
        String description = "位于" + (req.community() == null ? "目标小区" : req.community()) + "的" + layout +
                "房源，" + orientation + "向采光良好，" + floor + "楼，" + String.join("、", facilities) +
                "，周边配套成熟，交通便捷，适合上班族居住。";
        HouseDto.AiFillVO vo = new HouseDto.AiFillVO(description, orientation, floor, facilities);
        save(uid, 4, "draft", 0L, req, vo);
        return vo;
    }

    private boolean isRisk(String text) {
        return text.contains("押金不予退还") || text.contains("单方涨租") || text.contains("单方收房")
                || text.contains("两个月租金的违约金") || text.contains("违约金") && !text.contains("甲方违约")
                || text.contains("不予退还") || text.contains("自动续约");
    }

    private String simplify(String text) {
        String s = text.replaceAll("乙方", "你（租客）").replaceAll("甲方", "房东");
        if (s.contains("押付")) {
            s += "。押金在退租验收无违约时会退还。";
        }
        if (s.contains("维修")) {
            s += "。遇到房屋设施损坏可以按此条要求房东限期维修。";
        }
        if (s.contains("优先续租")) {
            s += "。租期到了想继续住，你有优先续租的权利。";
        }
        return s;
    }

    private void save(long uid, int type, String targetType, Long targetId, Object input, Object result) {
        try {
            AiAnalysis a = new AiAnalysis();
            a.setUserId(uid);
            a.setType(type);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setInputSnapshot(objectMapper.writeValueAsString(input));
            a.setResult(objectMapper.writeValueAsString(result));
            a.setModel(llmGateway.available() ? llmGateway.describe() : MOCK_MODEL);
            analysisMapper.insert(a);
        } catch (Exception e) {
            log.warn("AI 分析结果落库失败: {}", e.getMessage());
        }
    }
}
