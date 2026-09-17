package com.rentagent.controller;

import com.rentagent.common.R;
import com.rentagent.dto.AiDto;
import com.rentagent.dto.HouseDto;
import com.rentagent.dto.PageVO;
import com.rentagent.entity.AiChatSession;
import com.rentagent.entity.House;
import com.rentagent.mapper.HouseMapper;
import com.rentagent.security.UserContext;
import com.rentagent.service.AnalysisService;
import com.rentagent.service.ChatService;
import com.rentagent.service.HouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** AI 智能体（FR-08/12~15） */
@Tag(name = "ai", description = "AI 智能体服务")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final ChatService chatService;
    private final AnalysisService analysisService;
    private final HouseService houseService;
    private final HouseMapper houseMapper;

    @Operation(summary = "创建会话（scene 1找房助手 2智能客服 3合同解读）")
    @PostMapping("/sessions")
    public R<AiChatSession> createSession(@Valid @RequestBody AiDto.SessionCreateReq req) {
        return R.ok(chatService.createSession(UserContext.userId(), req.scene(), req.title()));
    }

    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    public R<PageVO<AiChatSession>> sessions(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size) {
        return R.ok(PageVO.of(chatService.sessions(UserContext.userId(), page, size)));
    }

    @Operation(summary = "会话历史")
    @GetMapping("/sessions/{id}/history")
    public R<List<AiDto.MessageVO>> history(@PathVariable long id) {
        return R.ok(chatService.history(id, UserContext.userId()));
    }

    @Operation(summary = "发送消息（SSE 流式返回：delta* + done）")
    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@PathVariable long id, @Valid @RequestBody AiDto.MessageSendReq req) {
        return chatService.send(id, req.content(), UserContext.userId());
    }

    @Operation(summary = "当前智能体引擎（<协议>:<模型> / rule-engine）")
    @GetMapping("/engine")
    public R<Map<String, String>> engine() {
        return R.ok(Map.of("engine", chatService.engineName()));
    }

    @Operation(summary = "智能定价建议（房东，FR-15）")
    @PostMapping("/houses/{id}/pricing-suggestion")
    public R<AiDto.PricingVO> pricing(@PathVariable long id) {
        House house = houseMapper.selectById(id);
        if (house == null) {
            return R.err(2001, "房源不存在");
        }
        if (house.getLandlordId() != UserContext.userId()) {
            return R.err(1007, "仅可对自己发布的房源获取定价建议");
        }
        return R.ok(analysisService.pricing(house, UserContext.userId()));
    }

    @Operation(summary = "房源信息智能识别填充（房东，FR-08）")
    @PostMapping("/assist-fill")
    public R<Map<String, Object>> assistFill(@Valid @RequestBody Map<String, String> req) {
        HouseDto.AiFillReq fillReq = new HouseDto.AiFillReq(
                req.get("title"), req.get("community"), req.get("layout"), req.get("imageFileName"));
        var vo = analysisService.assistFill(fillReq, UserContext.userId());
        return R.ok(Map.of("description", vo.description(), "orientation", vo.orientation(),
                "floorDesc", vo.floorDesc(), "facilities", vo.facilities()));
    }
}
