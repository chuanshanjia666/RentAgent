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
import org.springframework.stereotype.Component;

/**
 * 真模型智能体：LangChain4j AiServices 编排（角色设定 + 会话记忆 + Function Calling 工具）。
 * 模型客户端由 LLM 网关按配置提供（OpenAI 兼容 / Anthropic Messages 双协议可切换）；
 * 网关不可用时上层自动降级为 MockAgent。
 */
@Component
@RequiredArgsConstructor
public class LlmAgent implements AgentEngine {

    interface Assistant {
        @SystemMessage("""
                你是 RentAgent 房屋租赁平台的 AI 助手，服务租客、房东与管理员。请遵守：
                1. 找房场景：先用 searchHouses 工具查询真实在租房源，再用自然语言给出推荐理由，不做编造；
                2. 客服场景：押金、退租、维修、违约等问题必须先调用 searchKnowledge 查询知识库，回答末尾以"（来源：xxx）"注明出处；
                3. 知识库未命中的客服问题，回答"抱歉，这个问题我还没学会，已为您转接人工客服。"；
                4. 涉及合同与资金问题时，必须声明"AI 生成，仅供参考"；
                5. 回答使用简体中文，简洁友好。
                """)
        TokenStream chat(@MemoryId long sessionId, @UserMessage String message);
    }

    private final LlmGateway llmGateway;
    private final HousingTools housingTools;
    private volatile Assistant assistant;

    @Override
    public boolean available() {
        return llmGateway.available();
    }

    public String describe() {
        return llmGateway.describe();
    }

    @Override
    public void stream(long sessionId, long userId, int scene, String userMessage, Callback callback) {
        Assistant a = assistant();
        a.chat(sessionId, userMessage)
                .onNext(callback::onToken)
                .onComplete((Response<AiMessage> resp) -> {
                    String text = resp.content() == null ? null : resp.content().text();
                    boolean transferred = text != null && text.contains("转接人工客服");
                    callback.onComplete(text == null ? "" : text, null, transferred);
                })
                .onError(callback::onError)
                .start();
    }

    private Assistant assistant() {
        if (assistant == null) {
            synchronized (this) {
                if (assistant == null) {
                    assistant = AiServices.builder(Assistant.class)
                            .streamingChatLanguageModel(llmGateway.streaming())
                            .tools(housingTools)
                            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(12))
                            .build();
                }
            }
        }
        return assistant;
    }
}
