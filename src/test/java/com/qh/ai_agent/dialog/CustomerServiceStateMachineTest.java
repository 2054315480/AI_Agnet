package com.qh.ai_agent.dialog;

import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.intent.model.SlotDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话状态机单测：验证各阶段可达、澄清与转人工条件真正生效。
 * 不依赖大模型，可直接跑。
 */
class CustomerServiceStateMachineTest {

    private static IntentConfig config() {
        IntentConfig config = new IntentConfig();
        config.init();
        return config;
    }

    private static IntentResult result(CustomerIntent intent, double confidence, Map<String, String> slots) {
        return IntentResult.builder()
                .intent(intent)
                .confidence(confidence)
                .slots(slots)
                .build();
    }

    @Test
    void missingRequiredSlotShouldEnterSlotFilling() {
        DialogState state = DialogState.builder().sessionId("s1").build();
        List<SlotDefinition> defs = config().getSlotDefinitions("ORDER_QUERY");

        state.updateFromIntentResult(result(CustomerIntent.ORDER_QUERY, 0.9, new HashMap<>()), defs);

        assertEquals(DialogPhase.SLOT_FILLING, state.getDialogPhase());
        assertFalse(state.getMissingSlots(defs).isEmpty());
    }

    @Test
    void filledSlotShouldEnterRetrieving() {
        DialogState state = DialogState.builder().sessionId("s2").build();
        List<SlotDefinition> defs = config().getSlotDefinitions("ORDER_QUERY");
        Map<String, String> slots = new HashMap<>();
        slots.put("order_id", "123456");

        state.updateFromIntentResult(result(CustomerIntent.ORDER_QUERY, 0.9, slots), defs);

        assertEquals(DialogPhase.RETRIEVING, state.getDialogPhase());
        assertTrue(state.getMissingSlots(defs).isEmpty());
    }

    @Test
    void lowConfidenceShouldEnterClarifying() {
        DialogState state = DialogState.builder().sessionId("s3").build();
        List<SlotDefinition> defs = config().getSlotDefinitions("PRODUCT_INFO");
        IntentResult lowConfidence = IntentResult.builder()
                .intent(CustomerIntent.PRODUCT_INFO)
                .confidence(0.2)
                .slots(new HashMap<>())
                .needsClarification(true)
                .build();

        state.updateFromIntentResult(lowConfidence, defs);

        assertEquals(DialogPhase.CLARIFYING, state.getDialogPhase());
    }

    @Test
    void requestHumanShouldEnterHandoff() {
        DialogState state = DialogState.builder().sessionId("s4").build();

        state.updateFromIntentResult(result(CustomerIntent.REQUEST_HUMAN, 0.95, new HashMap<>()), List.of());

        assertEquals(DialogPhase.HANDOFF, state.getDialogPhase());
        assertTrue(state.isHandoffTriggered());
    }

    @Test
    void twoConsecutiveOutOfScopeShouldTriggerHandoff() {
        DialogStateManager manager = new DialogStateManager(config());
        IntentResult outOfScope = result(CustomerIntent.OUT_OF_SCOPE, 0.6, new HashMap<>());

        manager.updateFromIntentResult("s5", outOfScope);
        assertEquals(1, manager.getState("s5").getConsecutiveOutOfScope());
        assertFalse(manager.getState("s5").isHandoffTriggered());

        manager.updateFromIntentResult("s5", outOfScope);
        assertEquals(DialogPhase.HANDOFF, manager.getState("s5").getDialogPhase());
        assertTrue(manager.getState("s5").isHandoffTriggered());
    }

    @Test
    void closedSessionShouldResetToGreeting() {
        DialogStateManager manager = new DialogStateManager(config());
        manager.getOrCreateState("s6");

        manager.closeSession("s6");
        assertEquals(DialogPhase.CLOSED, manager.getState("s6").getDialogPhase());

        DialogState fresh = manager.resetSession("s6");
        assertEquals(DialogPhase.GREETING, fresh.getDialogPhase());
        assertEquals(DialogPhase.GREETING, manager.getState("s6").getDialogPhase());
    }
}
