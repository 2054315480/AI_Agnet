package com.qh.ai_agent.dialog;

import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.dialog.store.DialogStateStore;
import com.qh.ai_agent.dialog.store.InMemoryDialogStateStore;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.intent.model.SlotDefinition;
import com.qh.ai_agent.intent.model.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 对话状态管理器。
 *
 * 会话状态不再直接放在本类的 ConcurrentHashMap 里，而是委托给 DialogStateStore：
 * - memory：单实例内存，带 TTL 与容量上限（默认）
 * - redis ：多实例共享 + TTL（cs.session.store=redis）
 * 这样切换存储实现不影响本类与调用方。
 */
@Slf4j
@Service
public class DialogStateManager {

    /** 转人工触发阈值：连续 OUT_OF_SCOPE 轮数 */
    private static final int HANDOFF_THRESHOLD = 2;

    private final IntentConfig intentConfig;
    private final DialogStateStore store;

    /** 单测或手工 new 时使用：默认内存存储 */
    public DialogStateManager(IntentConfig intentConfig) {
        this(intentConfig, new InMemoryDialogStateStore(30, 5000));
    }

    /** Spring 装配：注入由 SessionStoreConfig 决定的存储实现 */
    @Autowired
    public DialogStateManager(IntentConfig intentConfig, DialogStateStore store) {
        this.intentConfig = intentConfig;
        this.store = store;
    }

    /**
     * 创建新会话
     */
    public DialogState createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DialogState state = DialogState.builder()
                .sessionId(sessionId)
                .dialogPhase(DialogPhase.GREETING)
                .build();
        store.put(sessionId, state);
        log.info("[DialogStateManager] 创建会话: {}（存储：{}）", sessionId, store.describe());
        return state;
    }

    /**
     * 获取会话状态，不存在则创建
     */
    public DialogState getOrCreateState(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        return store.getOrCreate(sessionId, () -> DialogState.builder()
                .sessionId(sessionId)
                .dialogPhase(DialogPhase.GREETING)
                .build());
    }

    /**
     * 获取会话状态
     */
    public DialogState getState(String sessionId) {
        return store.get(sessionId);
    }

    /**
     * 更新会话状态
     */
    public void updateState(String sessionId, DialogState state) {
        store.put(sessionId, state);
    }

    /**
     * 从意图识别结果更新状态
     */
    public DialogState updateFromIntentResult(String sessionId, IntentResult intentResult) {
        DialogState state = getOrCreateState(sessionId);

        // 获取当前意图对应的槽位定义
        List<SlotDefinition> slotDefs = List.of();
        if (intentResult.getIntent() != null) {
            slotDefs = intentConfig.getSlotDefinitions(intentResult.getIntent().name());
        }

        state.updateFromIntentResult(intentResult, slotDefs);

        // 检查转人工条件
        if (state.getConsecutiveOutOfScope() >= HANDOFF_THRESHOLD && !state.isHandoffTriggered()) {
            state.setDialogPhase(DialogPhase.HANDOFF);
            state.setHandoffTriggered(true);
            log.info("[DialogStateManager] 会话 {} 连续 {} 轮超范围，触发转人工", sessionId, HANDOFF_THRESHOLD);
        }

        store.put(sessionId, state);
        return state;
    }

    /**
     * 清除会话状态
     */
    public void clearState(String sessionId) {
        store.remove(sessionId);
        log.info("[DialogStateManager] 清除会话: {}", sessionId);
    }

    /**
     * 关闭会话：阶段置为 CLOSED，历史数据保留用于分析。
     */
    public void closeSession(String sessionId) {
        DialogState state = store.get(sessionId);
        if (state != null) {
            state.setDialogPhase(DialogPhase.CLOSED);
            store.put(sessionId, state);
            log.info("[DialogStateManager] 会话 {} 已关闭", sessionId);
        }
    }

    /**
     * 关闭后重新开一条干净状态，复用同一个 sessionId。
     */
    /** 当前会话存储实现描述，便于排障与演示 */
    public String storeInfo() {
        return store.describe();
    }

    public DialogState resetSession(String sessionId) {
        DialogState fresh = DialogState.builder()
                .sessionId(sessionId)
                .dialogPhase(DialogPhase.GREETING)
                .build();
        store.put(sessionId, fresh);
        log.info("[DialogStateManager] 会话 {} 状态已重置", sessionId);
        return fresh;
    }

}
