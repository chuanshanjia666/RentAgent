package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.dto.HouseDto.SearchReq;
import com.rentagent.entity.House;
import com.rentagent.service.KbService;
import com.rentagent.service.HouseService;
import com.rentagent.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则引擎智能体（无 GLM Key 时的降级实现，风险 R1 应对）：
 * 找房场景解析预算/户型/区域/地铁后调用真实检索；客服场景走知识库命中，未命中礼貌转人工。
 * 模拟流式输出以保证前端体验一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockAgent implements AgentEngine {

    private static final Pattern BUDGET = Pattern.compile("(\\d{3,5})");
    private static final Pattern LAYOUT = Pattern.compile("([一二三1-3])[居室]");

    private final SearchService searchService;
    private final HouseService houseService;
    private final KbService kbService;
    private final ObjectMapper objectMapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void stream(long sessionId, long userId, int scene, String userMessage, Callback callback) {
        pool.submit(() -> {
            try {
                String full;
                String citationsJson = null;
                boolean transferred = false;
                if (scene == AiChatSessionScene.FIND_HOUSE) {
                    full = findHouseReply(userMessage);
                } else if (scene == AiChatSessionScene.CUSTOMER_SERVICE) {
                    List<Map<String, String>> refs = kbService.search(userMessage, 3);
                    if (refs.isEmpty()) {
                        transferred = true;
                        full = "抱歉，这个问题我还没学会，已为您转接人工客服，工作时间为每日 9:00-21:00。您也可以先看看常见问题清单。";
                    } else {
                        StringBuilder sb = new StringBuilder("根据平台知识库：\n");
                        for (Map<String, String> ref : refs) {
                            sb.append("· ").append(ref.get("snippet")).append("（来源：").append(ref.get("title")).append("）\n");
                        }
                        sb.append("\nAI 生成，仅供参考。还有其他问题欢迎继续问我～");
                        citationsJson = objectMapper.writeValueAsString(refs);
                        full = sb.toString();
                    }
                } else {
                    List<Map<String, String>> refs = kbService.search(userMessage, 2);
                    full = refs.isEmpty()
                            ? "关于合同条款，建议您在签约页使用「合同智能解读」功能，AI 会逐条通俗化解释并标红风险条款（如高额违约金、单方涨租）。AI 生成，仅供参考。"
                            : "根据平台知识库：" + refs.get(0).get("snippet") + "\n（来源：" + refs.get(0).get("title") + "）\n详细条款解读请在签约页使用「合同智能解读」。AI 生成，仅供参考。";
                }
                // 模拟流式输出
                for (int i = 0; i < full.length(); i += 6) {
                    callback.onToken(full.substring(i, Math.min(full.length(), i + 6)));
                    Thread.sleep(25);
                }
                callback.onComplete(full, citationsJson, transferred);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /** FR-12：自然语言 → 结构化条件 → 调用真实房源检索工具 → 推荐理由 */
    private String findHouseReply(String message) {
        Integer maxRent = null;
        Matcher m = BUDGET.matcher(message.replaceAll("[,，。]", ""));
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v >= 300 && v <= 100000) {
                maxRent = v;
                break;
            }
        }
        String layout = null;
        Matcher lm = LAYOUT.matcher(message);
        if (lm.find()) {
            String n = lm.group(1);
            layout = switch (n) {
                case "一", "1" -> "1室";
                case "二", "2" -> "2室";
                default -> "3室";
            };
        }
        Boolean subway = message.contains("地铁");
        SearchReq req = new SearchReq(null, null, layout, null, null,
                maxRent == null ? null : BigDecimal.valueOf(maxRent), null, null, null, null, null,
                maxRent == null ? "hot" : "rent_asc", 1L, 3L);
        List<House> houses = searchService.search(req).getRecords();
        if (!subway && maxRent == null && layout == null) {
            req = new SearchReq(stripNeed(message), null, null, null, null, null, null, null, null, null, null,
                    "hot", 1L, 3L);
            houses = searchService.search(req).getRecords();
        }
        if (houses.isEmpty()) {
            return "没有找到完全符合条件的房源。建议您：1）适当放宽预算或区域范围；2）在房源列表页使用多条件筛选；" +
                    "3）也可以告诉我更具体的需求（预算、户型、通勤方式），我再帮您找找。";
        }
        StringBuilder sb = new StringBuilder("好的，已按您的需求（");
        if (maxRent != null) {
            sb.append("预算 ").append(maxRent).append(" 元内");
        }
        if (layout != null) {
            sb.append(" ").append(layout);
        }
        if (subway) {
            sb.append(" 近地铁");
        }
        sb.append("）检索在租房源，为您推荐：\n");
        int i = 1;
        for (House h : houses) {
            sb.append(i++).append(". 【").append(h.getTitle()).append("】")
                    .append(h.getDistrict()).append(" · ").append(h.getCommunity())
                    .append(" · ").append(h.getLayout()).append(" · ¥").append(h.getRent()).append("/月\n");
        }
        sb.append("\n推荐理由：以上房源均为已通过平台审核的在租房源，价格与您的要求匹配度最高。")
                .append("点击房源卡片可查看详情、预约看房；需要调整条件（比如“再便宜一点”）直接告诉我。");
        return sb.toString();
    }

    private String stripNeed(String message) {
        return message.length() > 20 ? message.substring(0, 20) : message;
    }

    /** 会话场景常量（与 ai_chat_session.scene 对应） */
    static final class AiChatSessionScene {
        static final int FIND_HOUSE = 1;
        static final int CUSTOMER_SERVICE = 2;
        static final int CONTRACT = 3;
    }
}
