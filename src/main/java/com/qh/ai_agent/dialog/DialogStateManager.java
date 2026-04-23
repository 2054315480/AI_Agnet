package com.qh.ai_agent.dialog;

import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话状态管理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogStateManager {

    private final IntentConfig intentConfig;

    /** 会话状态缓存 */
    private final ConcurrentHashMap<String, DialogState> stateCache = new ConcurrentHashMap<>();

    /** 转人工触发阈值：连续 OUT_OF_SCOPE 轮数 */
    private static final int HANDOFF_THRESHOLD = 2;

    /**
     * 创建新会话
     */
    public DialogState createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DialogState state = DialogState.builder()
                .sessionId(sessionId)
                .dialogPhase(DialogPhase.GREETING)
                .build();
        stateCache.put(sessionId, state);
        log.info("[DialogStateManager] 创建会话: {}", sessionId);
        return state;
    }

    /**
     * 获取会话状态，不存在则创建
     */
    public DialogState getOrCreateState(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        return stateCache.computeIfAbsent(sessionId, id -> {
            DialogState s = DialogState.builder()
                    .sessionId(id)
                    .dialogPhase(DialogPhase.GREETING)
                    .build();
            return s;
        });
    }

    /**
     * 获取会话状态
     */
    public DialogState getState(String sessionId) {
        return stateCache.get(sessionId);
    }

    /**
     * 更新会话状态
     */
    public void updateState(String sessionId, DialogState state) {
        stateCache.put(sessionId, state);
    }

    /**
     * 从意图识别结果更新状态
     */
    public DialogState updateFromIntentResult(String sessionId, IntentResult intentResult) {
        DialogState state = getOrCreateState(sessionId);

        // 获取当前意图对应的槽位定义
        List<com.qh.ai_agent.intent.model.SlotDefinition> slotDefs = List.of();
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

        stateCache.put(sessionId, state);
        return state;
    }

    /**
     * 清除会话状态
     */
    public void clearState(String sessionId) {
        stateCache.remove(sessionId);
        log.info("[DialogStateManager] 清除会话: {}", sessionId);
    }
}
