package com.qh.ai_agent.dialog.store;

import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.model.CustomerIntent;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存会话存储测试：TTL 过期、容量上限淘汰、原子 getOrCreate。
 */
class DialogStateStoreTest {

    private static DialogState state(String sid) {
        return DialogState.builder().sessionId(sid).dialogPhase(DialogPhase.GREETING).build();
    }

    @Test
    void putThenGetShouldReturnSameState() {
        InMemoryDialogStateStore store = new InMemoryDialogStateStore(30, 100);
        store.put("s1", state("s1"));
        assertNotNull(store.get("s1"));
        assertEquals(1, store.size());
    }

    @Test
    void expiredSessionShouldBeDroppedOnRead() throws Exception {
        InMemoryDialogStateStore store = new InMemoryDialogStateStore(Duration.ofMillis(50), 100);
        store.put("s1", state("s1"));
        assertNotNull(store.get("s1"));
        Thread.sleep(120);
        assertNull(store.get("s1"), "超过 TTL 的会话应被懒删除");
        assertEquals(0, store.size());
    }

    @Test
    void maxSessionsShouldEvictOldest() {
        InMemoryDialogStateStore store = new InMemoryDialogStateStore(30, 16);
        for (int i = 0; i < 40; i++) {
            store.put("s" + i, state("s" + i));
        }
        assertTrue(store.size() <= 16, "会话数不应超过上限，实际=" + store.size());
        assertNull(store.get("s0"), "最久未访问的会话应被淘汰");
        assertNotNull(store.get("s39"), "最新会话必须保留");
    }

    @Test
    void getOrCreateShouldCreateOnce() {
        InMemoryDialogStateStore store = new InMemoryDialogStateStore(30, 100);
        DialogState first = store.getOrCreate("s9", () -> state("s9"));
        first.setCurrentIntent(CustomerIntent.ORDER_QUERY);
        DialogState second = store.getOrCreate("s9", () -> state("s9"));
        assertEquals(CustomerIntent.ORDER_QUERY, second.getCurrentIntent(), "第二次应取到同一个状态对象");
        assertEquals(1, store.size());
    }

    @Test
    void removeShouldDeleteSession() {
        InMemoryDialogStateStore store = new InMemoryDialogStateStore(30, 100);
        store.put("s1", state("s1"));
        assertNotNull(store.remove("s1"));
        assertNull(store.get("s1"));
        assertTrue(store.describe().contains("memory"));
    }
}
