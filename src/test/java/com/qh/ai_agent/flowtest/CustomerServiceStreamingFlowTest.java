package com.qh.ai_agent.flowtest;

import com.qh.ai_agent.Agent.Model.AgentState;
import com.qh.ai_agent.Agent.customer_service.CustomerServiceAgent;
import com.qh.ai_agent.dialog.DialogStateManager;
import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.intent.IntentClassifier;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.model.PromptTemplate;
import com.qh.ai_agent.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客服链路离线全链路测试：用假的流式 ChatModel 驱动真实的
 * think -> act -> 流式作答流程，验证 SSE 事件序列与状态机阶段。
 * 不需要网络和 API Key。
 */
class CustomerServiceStreamingFlowTest {

    private static final String KB_HIT = "{\"matchType\":\"hybrid_search\",\"results\":["
            + "{\"kbId\":\"kb1\",\"relevance\":0.09,\"content\":\"Q: 退换货政策\\nA: 7天无理由退货\"}],"
            + "\"suggestedQuestions\":[]}";

    private static final String KB_EMPTY = "{\"matchType\":\"none\",\"results\":[],\"suggestedQuestions\":[]}";

    // ==================== 测试替身 ====================

    /** 假的客服知识库工具，返回预设 JSON */
    public static class FakeKbTools {
        private final String result;

        public FakeKbTools(String result) {
            this.result = result;
        }

        @Tool(name = "fakeSearch", description = "假的客服知识库检索")
        public String fakeSearch(String query) {
            return result;
        }
    }

    /** 捕获 SSE 事件，不真正写网络 */
    static class CapturingAgent extends CustomerServiceAgent {
        final List<String> types = new ArrayList<>();
        final List<String> contents = new ArrayList<>();

        CapturingAgent(ToolCallback[] tools, ChatModel chatModel, IntentClassifier classifier,
                       DialogStateManager manager, IntentConfig config, PromptTemplateService pts) {
            super(tools, chatModel, classifier, manager, config, pts);
        }

        @Override
        protected void sendSseEvent(String type, String content) {
            types.add(type);
            contents.add(content);
        }

        List<String> contentsOf(String type) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                if (type.equals(types.get(i))) {
                    out.add(contents.get(i));
                }
            }
            return out;
        }

        String joinedDeltas() {
            return String.join("", contentsOf("answer_delta"));
        }
    }

    private static ChatResponse textChunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse toolCallChunk(String name, String args) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", name, args)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static PromptTemplateService promptService() {
        PromptTemplateService service = mock(PromptTemplateService.class);
        when(service.loadTemplate(anyString()))
                .thenReturn(new PromptTemplate("customer-service", "你是智能客服。"));
        return service;
    }

    private static IntentConfig config() {
        IntentConfig config = new IntentConfig();
        config.init();
        return config;
    }

    private static IntentClassifier classifierReturning(IntentResult result) {
        IntentClassifier classifier = mock(IntentClassifier.class);
        when(classifier.classify(anyString(), any(), any())).thenReturn(result);
        return classifier;
    }

    private static IntentResult intent(CustomerIntent intent, double confidence) {
        return IntentResult.builder().intent(intent).confidence(confidence).slots(new HashMap<>()).build();
    }

    /** 模拟 BaseAgent.runStream 的前置动作：建会话 + 塞入用户消息 + 挂上 SSE（非 null 才走流式分支） */
    private void prepare(CapturingAgent agent, String sessionId, String userText) {
        agent.initSession(sessionId);
        agent.getMessagesList().add(new UserMessage(userText));
        agent.setCurrentSseEmitter(new SseEmitter());
    }

    private CapturingAgent newAgent(ChatModel model, IntentClassifier classifier,
                                    DialogStateManager manager, IntentConfig config, String kbResult) {
        ToolCallback[] tools = ToolCallbacks.from(new FakeKbTools(kbResult));
        return new CapturingAgent(tools, model, classifier, manager, config, promptService());
    }

    // ==================== 用例 ====================

    /**
     * 回归用例：知识库已经检索到内容、但意图缺必填槽位时，必须照常作答，不能追问。
     * 这是 RRF 融合分量级约 0.09、被 0.3 阈值误判成未命中导致的真实线上回归。
     */
    @Test
    void knowledgeHitWithMissingSlotShouldAnswerInsteadOfClarify() {
        IntentConfig config = config();
        DialogStateManager manager = new DialogStateManager(config);
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(
                Flux.just(toolCallChunk("fakeSearch", "{\"query\":\"退货政策\"}")),
                Flux.just(textChunk("我们的"), textChunk("退货政策是"), textChunk("7天无理由")));

        CapturingAgent agent = newAgent(model, classifierReturning(intent(CustomerIntent.PRODUCT_INFO, 0.88)),
                manager, config, KB_HIT);
        prepare(agent, "flow-hit", "退货政策是什么");

        agent.step();
        agent.setCurrentStep(2);
        agent.step();

        assertTrue(agent.contentsOf("clarification").isEmpty(), "命中知识库时不应追问");
        assertEquals(3, agent.contentsOf("answer_delta").size(), "应该逐片推送增量文本");
        assertEquals("我们的退货政策是7天无理由", agent.joinedDeltas());
        assertEquals("我们的退货政策是7天无理由", agent.contentsOf("answer").get(0));
        assertEquals(DialogPhase.ANSWERING, manager.getState("flow-hit").getDialogPhase());
    }

    /**
     * 工具调用轮：模型先输出中间说明再决定调工具，这段文本必须被回撤并归入思考过程。
     */
    @Test
    void toolPreambleShouldBeResetAndMovedToThinking() {
        IntentConfig config = config();
        DialogStateManager manager = new DialogStateManager(config);
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(
                Flux.just(textChunk("我先帮您查一下"), toolCallChunk("fakeSearch", "{\"query\":\"x\"}")),
                Flux.just(textChunk("查到了")));

        CapturingAgent agent = newAgent(model, classifierReturning(intent(CustomerIntent.POLICY_QUERY, 0.9)),
                manager, config, KB_HIT);
        prepare(agent, "flow-preamble", "退换货怎么弄");

        agent.step();

        List<String> t = agent.types;
        assertTrue(t.indexOf("answer_delta") < t.indexOf("answer_reset"), "先有增量再有回撤");
        assertTrue(t.indexOf("answer_reset") < t.indexOf("tool_call"), "回撤后再发工具调用");
        assertTrue(t.contains("thinking"), "中间说明应归入思考过程");
        assertTrue(t.contains("tool_result"), "工具应真实执行");
    }

    /**
     * 知识库真的没有结果、且意图缺必填槽位时，才追问槽位。
     */
    @Test
    void emptyKnowledgeWithMissingSlotShouldAskForSlot() {
        IntentConfig config = config();
        DialogStateManager manager = new DialogStateManager(config);
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(
                Flux.just(toolCallChunk("fakeSearch", "{\"query\":\"订单\"}")));

        CapturingAgent agent = newAgent(model, classifierReturning(intent(CustomerIntent.ORDER_QUERY, 0.9)),
                manager, config, KB_EMPTY);
        prepare(agent, "flow-clarify", "订单到哪了");

        agent.step();

        assertEquals(1, agent.contentsOf("clarification").size(), "应当追问缺失槽位");
        assertTrue(agent.contentsOf("clarification").get(0).contains("订单号"));
        assertEquals(DialogPhase.SLOT_FILLING, manager.getState("flow-clarify").getDialogPhase());
        assertTrue(agent.contentsOf("answer_delta").isEmpty(), "追问时不应有作答增量");
    }

    /**
     * 连续两轮超出范围后必须真的转人工（修复前 shouldHandoff 是死代码）。
     */
    @Test
    void secondConsecutiveOutOfScopeShouldHandoff() {
        IntentConfig config = config();
        DialogStateManager manager = new DialogStateManager(config);
        ChatModel model = mock(ChatModel.class);
        IntentResult outOfScope = intent(CustomerIntent.OUT_OF_SCOPE, 0.7);

        CapturingAgent agent = newAgent(model, classifierReturning(outOfScope), manager, config, KB_EMPTY);
        agent.initSession("flow-handoff");
        agent.getMessagesList().add(new UserMessage("帮我写一首关于春天的诗"));

        // 模拟已经有一轮超范围
        manager.updateFromIntentResult("flow-handoff", outOfScope);
        manager.updateFromIntentResult("flow-handoff", outOfScope);
        assertEquals(DialogPhase.HANDOFF, manager.getState("flow-handoff").getDialogPhase());

        agent.setCurrentSseEmitter(new SseEmitter());
        agent.step();

        assertEquals(1, agent.contentsOf("handoff").size(), "应推送转人工事件");
        assertTrue(agent.contentsOf("answer").get(0).contains("转接人工"));
        assertEquals(AgentState.FINISHED, agent.getState());
        assertFalse(agent.contentsOf("handoff").get(0).isBlank());
    }
}
