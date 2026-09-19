package com.rentagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.dto.HouseDto;
import com.rentagent.entity.House;
import com.rentagent.service.HouseService;
import com.rentagent.service.KbService;
import com.rentagent.service.SearchService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体工具集（Function Calling）：新增工具只需新增 @Tool 方法（NFR-09）。
 * <p>
 * 房源卡片的 {@code detailUrl} 由本类统一拼装（HashRouter 的详情页地址），
 * 系统提示词要求模型原样引用该地址输出 Markdown 链接——链接形状由代码定义，
 * 模型只负责转述，从根上避免"模型编 URL"导致的死链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousingTools {

    /** 房源详情页地址前缀（前端 HashRouter 路由，详情见 App.tsx 的 /app/houses/:id） */
    public static final String DETAIL_URL_PREFIX = "#/app/houses/";

    /** 单次检索返回的房源数上限：足够模型做对比推荐，又不至于把上下文撑爆 */
    private static final int SEARCH_LIMIT = 8;

    private final SearchService searchService;
    private final HouseService houseService;
    private final KbService kbService;
    private final ObjectMapper objectMapper;

    @Tool("按条件检索平台真实在租房源。minRent/maxRent 为月租金区间（元）；layoutKeyword 为户型前缀，如 1室/2室/3室；" +
            "district 为行政区名（甘井子区/沙河口区/高新园区/中山区/西岗区）；keyword 为标题/小区/地址关键词；" +
            "subway 为 true 时筛选近地铁房源；facilities 为逗号分隔的设施标签，可选值：近地铁/精装修/家电齐全/拎包入住/电梯/采光好；" +
            "sort 取 rent_asc（租金升序，默认）或 rent_desc（租金降序）。" +
            "返回 JSON：total 为符合条件的房源总数，list 为房源卡片数组，卡片内 detailUrl 为房源详情页链接（输出链接时必须原样使用）。")
    public String searchHouses(Integer minRent, Integer maxRent, String layoutKeyword, String district,
                               String keyword, Boolean subway, String facilities, String sort) {
        try {
            List<String> tags = parseFacilities(facilities);
            if (Boolean.TRUE.equals(subway) && !tags.contains("近地铁")) {
                tags.add("近地铁");
            }
            HouseDto.SearchReq req = new HouseDto.SearchReq(keyword, district, layoutKeyword, null,
                    toBigDecimal(minRent), toBigDecimal(maxRent), tags.isEmpty() ? null : tags,
                    null, null, null, null,
                    "rent_desc".equals(sort) ? "rent_desc" : "rent_asc", 1L, (long) SEARCH_LIMIT);
            var page = searchService.search(req);
            Map<String, Object> body = new HashMap<>();
            body.put("total", page.getTotal());
            body.put("list", cards(page.getRecords()));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("searchHouses 工具执行失败: {}", e.getMessage());
            return "{\"total\":0,\"list\":[]}";
        }
    }

    /** 查看单套房源的完整信息（含朝向/楼层/押付/描述/封面），houseId 取自 searchHouses 返回的卡片 id */
    @Tool("查看指定房源的完整信息（朝向、楼层、面积、押付方式、设施、描述、封面图与详情页链接）。" +
            "houseId 必须来自 searchHouses 返回的房源卡片。房源已下架或不存在时会返回提示文本。")
    public String getHouseDetail(Long houseId) {
        if (houseId == null) {
            return "缺少 houseId 参数";
        }
        try {
            HouseDto.Item item = houseService.detail(houseId);
            Map<String, Object> d = new HashMap<>();
            House h = item.house();
            d.put("id", h.getId());
            d.put("title", h.getTitle());
            d.put("district", h.getDistrict());
            d.put("community", h.getCommunity());
            d.put("address", h.getAddress());
            d.put("layout", h.getLayout());
            d.put("area", h.getArea());
            d.put("orientation", h.getOrientation());
            d.put("floorDesc", h.getFloorDesc());
            d.put("rent", h.getRent());
            d.put("depositType", h.getDepositType());
            d.put("facilities", h.getFacilities());
            d.put("description", h.getDescription());
            d.put("coverUrl", h.getCoverUrl());
            d.put("detailUrl", DETAIL_URL_PREFIX + h.getId());
            d.put("landlordName", item.landlordName());
            d.put("reviewCount", item.reviewCount());
            return objectMapper.writeValueAsString(d);
        } catch (Exception e) {
            log.warn("getHouseDetail 工具执行失败: {}", e.getMessage());
            return "该房源不存在或已下架，无法查看详情。";
        }
    }

    /** 房源卡片列表（工具返回体，同时作为工具留痕的 tool_result 落库） */
    public List<Map<String, Object>> cards(List<House> houses) {
        return houses.stream().map(h -> {
            Map<String, Object> card = new HashMap<>();
            card.put("id", h.getId());
            card.put("title", h.getTitle());
            card.put("district", h.getDistrict());
            card.put("community", h.getCommunity());
            card.put("layout", h.getLayout());
            card.put("rent", h.getRent());
            card.put("facilities", h.getFacilities());
            card.put("coverUrl", h.getCoverUrl());
            card.put("detailUrl", DETAIL_URL_PREFIX + h.getId());
            return card;
        }).toList();
    }

    @Tool("检索平台知识库（平台规则、押金/退租/维修/违约等租赁政策 FAQ），返回带来源的片段。")
    public String searchKnowledge(String question) {
        try {
            return objectMapper.writeValueAsString(kbService.search(question, 3));
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 逗号分隔的设施标签解析：容错空串/空格/中文逗号，异常输入退化为空列表 */
    private static List<String> parseFacilities(String facilities) {
        List<String> tags = new ArrayList<>();
        if (facilities == null || facilities.isBlank()) {
            return tags;
        }
        for (String t : facilities.split("[,，]")) {
            String v = t.trim();
            if (!v.isEmpty()) {
                tags.add(v);
            }
        }
        return tags;
    }

    private static BigDecimal toBigDecimal(Integer v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
