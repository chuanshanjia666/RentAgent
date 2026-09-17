package com.rentagent.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 智能体（唯一实现）：LangChain4j AiServices 编排（角色设定 + 会话记忆 + Function Calling 工具）。
 * 模型客户端由 LLM 网关按配置提供（三种协议可切换）；网关不可用时上层直接报错（4001），无本地兜底。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LlmAgent implements AgentEngine {

    /** 智能体角色设定（后台"AI 对话审计"会展示实际生效的这一份，便于排查回答偏差） */
    public static final String SYSTEM_PROMPT = """
            你是 RentAgent 房屋租赁平台的 AI 助手，服务租客、房东与管理员。请遵守：
            1. 找房场景：先用 searchHouses 工具查询真实在租房源，再用自然语言给出推荐理由，不做编造；
            2. 客服场景：押金、退租、维修、违约等问题必须先调用 searchKnowledge 查询知识库，回答末尾以"（来源：xxx）"注明出处；
            3. 知识库未命中的客服问题，回答"抱歉，这个问题我还没学会，已为您转接人工客服。"；
            4. 涉及合同与资金问题时，必须声明"AI 生成，仅供参考"；
            5. 回答使用简体中文，简洁友好。
            """;

    interface Assistant {
        @SystemMessage(SYSTEM_PROMPT)
        TokenStream chat(@MemoryId long sessionId, @UserMessage String message);

        /** 非流式单轮（工具调用可靠）：流式通道丢失工具调用时用它兜底，仍由同一个 Agent 与同一份会话记忆执行 */
        @SystemMessage(SYSTEM_PROMPT)
        String chatSync(@MemoryId long sessionId, @UserMessage String message);
    }

    private final LlmGateway llmGateway;
    private final HousingToolProvider housingToolProvider;
    private volatile Assistant assistant;

    /** 兜底重试的强化指令：按场景指向该场景必需的工具（找房 → searchHouses，客服/合同 → searchKnowledge） */
    private static final String SEARCH_RETRY_HINT =
            "\n（重要：请立即调用 searchHouses 工具查询真实在租房源，并在本次回复中直接列出符合条件的房源与价格，"
            + "不要只回复“正在查询”这类过程说明，必须给出结果。）";

    private static final String KNOWLEDGE_RETRY_HINT =
            "\n（重要：请立即调用 searchKnowledge 工具查询平台规则与政策，并在回答末尾以“（来源：xxx）”注明出处，"
            + "不要只回复“正在查询”这类过程说明。）";

    @Override
    public boolean available() {
        return llmGateway.available();
    }

    public String describe() {
        return llmGateway.describe();
    }

    /** 兜底重试用：非流式结果按固定长度切片回放，前端渲染体验与流式一致 */
    private static final int SYNC_CHUNK = 24;

    @Override
    public void stream(long sessionId, long userId, int scene, String userMessage, Callback callback) {
        run(sessionId, scene, userMessage, callback);
    }

    /**
     * 单轮对话（流式）。工具调用与正文由适配器一并还原（见 {@link OpenAiChatCompletionsModel}），
     * 正常情况下本轮即可完成"调工具 → 出结论"。仅当本轮**一个工具都没执行**、回答又不像结论时，
     * 才追加强化指令用非流式重跑一次——这是对传输异常/模型输出质量的最后一道网，
     * 重跑拿到的仍是模型真实回答与真实工具结果，不生成任何本地虚构答案。
     */
    private void run(long sessionId, int scene, String userMessage, Callback callback) {
        Assistant a = assistant();
        StringBuilder buf = new StringBuilder();
        a.chat(sessionId, userMessage)
                .onNext(token -> {
                    buf.append(token);
                    callback.onToken(token);
                })
                .onComplete((Response<AiMessage> resp) -> {
                    String streamed = buf.toString();
                    String text = resp.content() == null ? null : resp.content().text();
                    String effective = streamed.isBlank() && text != null ? text : streamed;
                    // NFR-05 留痕：token 用量随回复落库（重试时上层累加）
                    if (resp.tokenUsage() != null) {
                        callback.onUsage(resp.tokenUsage().totalTokenCount());
                    }
                    // NFR-05：知识库来源取自 searchKnowledge 工具的返回体（非 null 即说明本轮真的调用了工具）
                    String citations = housingToolProvider.takeKnowledgeCitations(sessionId);
                    Set<String> tools = housingToolProvider.takeRoundTools(sessionId);
                    if (shouldFallbackToSync(scene, effective, citations, tools)) {
                        log.warn("本轮未执行任何工具且回答疑似过程语（{} 字），改用非流式兜底重跑一次",
                                effective.trim().length());
                        callback.onToken(effective.isBlank() ? "" : "\n\n");
                        syncRun(sessionId, scene, userMessage + retryHint(scene), callback);
                        return;
                    }
                    callback.onComplete(effective, citations, effective.contains("转接人工客服"));
                })
                .onError(error -> {
                    // 本轮中断时清掉会话标记，避免残留污染下一轮的工具链判定
                    housingToolProvider.takeKnowledgeCitations(sessionId);
                    housingToolProvider.takeRoundTools(sessionId);
                    callback.onError(error);
                })
                .start();
    }

    /**
     * 是否需要非流式兜底重跑：**本轮执行过工具就绝不重跑**——工具已给出真实结果，
     * 回答偏短（如"暂无符合条件的房源"）是模型的真实结论而非过程语。
     * 只有在"一个工具都没执行"时，才按场景判断回答是否像半截的过程语：
     * 找房场景（scene=1）没调 searchHouses 且回答过短；客服/合同场景（scene=2/3）没调 searchKnowledge 因而没有来源。
     */
    private boolean shouldFallbackToSync(int scene, String text, String citations, Set<String> toolsThisRound) {
        if (!toolsThisRound.isEmpty()) {
            return false;
        }
        return scene == 1 ? tooShortForSearch(text) : citations == null;
    }

    /** 找房场景的正常答复会列出房源与价格，过短即视为只回复了过程语 */
    private boolean tooShortForSearch(String text) {
        return text == null || text.replaceAll("\\s", "").length() < 40;
    }

    private String retryHint(int scene) {
        return scene == 1 ? SEARCH_RETRY_HINT : KNOWLEDGE_RETRY_HINT;
    }

    /**
     * 非流式兜底：AiServices 以同步方式执行（工具调用可靠），拿到完整回答后按固定长度切片回放为 delta。
     * 输出的仍然是模型的真实回答与真实工具结果，只是改变了传输方式。
     */
    private void syncRun(long sessionId, int scene, String userMessage, Callback callback) {
        try {
            String text = assistant().chatSync(sessionId, userMessage);
            if (text == null) {
                text = "";
            }
            for (int i = 0; i < text.length(); i += SYNC_CHUNK) {
                callback.onToken(text.substring(i, Math.min(text.length(), i + SYNC_CHUNK)));
            }
            String citations = housingToolProvider.takeKnowledgeCitations(sessionId);
            callback.onComplete(text, citations, text.contains("转接人工客服"));
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private Assistant assistant() {
        if (assistant == null) {
            synchronized (this) {
                if (assistant == null) {
                    assistant = AiServices.builder(Assistant.class)
                            .streamingChatLanguageModel(llmGateway.streaming())
                            .chatLanguageModel(llmGateway.chat())
                            .toolProvider(housingToolProvider)
                            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(12))
                            .build();
                }
            }
        }
        return assistant;
    }
}
