package com.qh.ai_agent.dialog;

import com.qh.ai_agent.dialog.model.DialogState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨轮对话历史测试：验证条数上限与顺序。
 */
class DialogStateHistoryTest {

    @Test
    void appendHistoryShouldKeepOrderAndRoles() {
        DialogState state = DialogState.builder().sessionId("h1").build();
        state.appendHistory("问题1", "回答1", 6);
        assertEquals(2, state.getRecentHistory().size());
        assertEquals("user", state.getRecentHistory().get(0).getRole());
        assertEquals("问题1", state.getRecentHistory().get(0).getContent());
        assertEquals("assistant", state.getRecentHistory().get(1).getRole());
    }

    @Test
    void appendHistoryShouldKeepOnlyLatestTurns() {
        DialogState state = DialogState.builder().sessionId("h2").build();
        for (int i = 0; i < 20; i++) {
            state.appendHistory("Q" + i, "A" + i, 3);
        }
        assertEquals(6, state.getRecentHistory().size(), "maxTurns=3 时最多保留 6 条");
        assertEquals("Q17", state.getRecentHistory().get(0).getContent());
        assertEquals("A19", state.getRecentHistory().get(5).getContent());
    }

    @Test
    void blankContentShouldBeSkipped() {
        DialogState state = DialogState.builder().sessionId("h3").build();
        state.appendHistory("  ", "回答", 6);
        assertEquals(1, state.getRecentHistory().size());
        assertTrue(state.getRecentHistory().get(0).getContent().contains("回答"));
    }
}
