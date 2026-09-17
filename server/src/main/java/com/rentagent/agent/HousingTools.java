package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.House;
import com.rentagent.service.KbService;
import com.rentagent.service.HouseService;
import com.rentagent.service.SearchService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 智能体工具集（Function Calling）：新增工具只需新增 @Tool 方法（NFR-09） */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousingTools {

    private final SearchService searchService;
    private final HouseService houseService;
    private final KbService kbService;
    private final ObjectMapper objectMapper;

    @Tool("按条件检索在租房源。maxRent 为月租金上限（元），layoutKeyword 如 1室/2室/3室，" +
            "district 为行政区名，keyword 为标题/小区关键词，subway 为 true 时筛选近地铁房源。返回房源卡片 JSON。")
    public String searchHouses(Integer maxRent, String layoutKeyword, String district, String keyword, Boolean subway) {
        try {
            HouseDto.SearchReq req = new HouseDto.SearchReq(keyword, district, layoutKeyword, null,
                    null, toBigDecimal(maxRent),
                    Boolean.TRUE.equals(subway) ? List.of("近地铁") : null,
                    null, null, null, null, "rent_asc", 1L, 5L);
            List<House> houses = searchService.search(req).getRecords();
            return cardsJson(houses);
        } catch (Exception e) {
            log.warn("searchHouses 工具执行失败: {}", e.getMessage());
            return "[]";
        }
    }

    /** 房源卡片 JSON（工具返回体，同时作为工具留痕的 tool_result 落库） */
    public String cardsJson(List<House> houses) {
        List<Map<String, Object>> cards = houses.stream().map(h -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", h.getId());
            card.put("title", h.getTitle());
            card.put("district", h.getDistrict());
            card.put("community", h.getCommunity());
            card.put("layout", h.getLayout());
            card.put("rent", h.getRent());
            card.put("facilities", houseService.toList(h.getFacilities()));
            return card;
        }).toList();
        try {
            return objectMapper.writeValueAsString(cards);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Tool("检索平台知识库（平台规则、押金/退租/维修/违约等租赁政策 FAQ），返回带来源的片段。")
    public String searchKnowledge(String question) {
        try {
            return objectMapper.writeValueAsString(kbService.search(question, 3));
        } catch (Exception e) {
            return "[]";
        }
    }

    private static BigDecimal toBigDecimal(Integer v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
