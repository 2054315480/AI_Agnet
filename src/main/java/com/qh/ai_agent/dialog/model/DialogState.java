package com.qh.ai_agent.dialog.model;

import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.intent.model.SlotDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 对话状态：跟踪当前会话的意图、槽位、阶段等信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogState {

    /** 会话 ID */
    private String sessionId;

    /** 当前识别的意图 */
    private CustomerIntent currentIntent;

    /** 已填充的槽位 */
    @Builder.Default
    private Map<String, String> filledSlots = new HashMap<>();

    /** 当前对话阶段 */
    @Builder.Default
    private DialogPhase dialogPhase = DialogPhase.GREETING;

    /** 当前轮次 */
    @Builder.Default
    private int turnCount = 0;

    /** 历史意图列表（用于话题切换追踪） */
    @Builder.Default
    private List<CustomerIntent> historyIntents = new ArrayList<>();

    /** 当前意图的置信度 */
    @Builder.Default
    private double confidenceScore = 0.0;

    /** 上一次澄清的槽位名称 */
    private String lastClarificationSlot;

    /** 连续无法识别的轮数 */
    @Builder.Default
    private int consecutiveOutOfScope = 0;

    /** 是否已触发转人工 */
    @Builder.Default
    private boolean handoffTriggered = false;

    /**
     * 从意图识别结果更新状态
     */
    public void updateFromIntentResult(IntentResult result, List<SlotDefinition> requiredSlots) {
        if (result.getIntent() != null) {
            if (this.currentIntent != null && this.currentIntent != result.getIntent()) {
                historyIntents.add(this.currentIntent);
            }
            this.currentIntent = result.getIntent();
        }
        if (result.getSlots() != null) {
            this.filledSlots.putAll(result.getSlots());
        }
        this.confidenceScore = result.getConfidence();

        // 更新对话阶段
        if (result.getIntent() == CustomerIntent.REQUEST_HUMAN) {
            this.dialogPhase = DialogPhase.HANDOFF;
            this.handoffTriggered = true;
        } else if (result.getIntent() == CustomerIntent.OUT_OF_SCOPE) {
            this.consecutiveOutOfScope++;
        } else if (result.isNeedsClarification()) {
            this.dialogPhase = DialogPhase.CLARIFYING;
        } else if (isSlotComplete(requiredSlots)) {
            this.dialogPhase = DialogPhase.RETRIEVING;
        } else {
            this.dialogPhase = DialogPhase.SLOT_FILLING;
        }

        this.turnCount++;
    }

    /**
     * 检查必填槽位是否已完整
     */
    public boolean isSlotComplete(List<SlotDefinition> requiredSlots) {
        if (requiredSlots == null || requiredSlots.isEmpty()) return true;
        return requiredSlots.stream()
                .filter(SlotDefinition::isRequired)
                .allMatch(sd -> filledSlots.containsKey(sd.getSlotName())
                        && filledSlots.get(sd.getSlotName()) != null
                        && !filledSlots.get(sd.getSlotName()).isBlank());
    }

    /**
     * 获取缺失的必填槽位
     */
    public List<SlotDefinition> getMissingSlots(List<SlotDefinition> allSlots) {
        if (allSlots == null) return Collections.emptyList();
        return allSlots.stream()
                .filter(SlotDefinition::isRequired)
                .filter(sd -> !filledSlots.containsKey(sd.getSlotName())
                        || filledSlots.get(sd.getSlotName()) == null
                        || filledSlots.get(sd.getSlotName()).isBlank())
                .toList();
    }
}
