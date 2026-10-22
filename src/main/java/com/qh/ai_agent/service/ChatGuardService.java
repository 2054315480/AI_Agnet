package com.qh.ai_agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话护栏：把 BannedWordAdvisor / SensitiveInfoAdvisor 的 before/after 逻辑
 * 落到活客服链路上（该链路直接调 ChatModel，不经过 ChatClient，Advisor 不会生效）。
 *
 * - 输入侧：违禁词拦截 + 敏感信息脱敏
 * - 输出侧：违禁词过滤 + 敏感信息脱敏
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatGuardService {

    private final BannedWordService bannedWordService;
    private final SensitiveInfoService sensitiveInfoService;

    /** 护栏结果：allowed=false 时 text 为给用户的提示语 */
    public record GuardResult(boolean allowed, String text, String reason) {
    }

    public GuardResult guardInput(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new GuardResult(true, userInput, null);
        }
        if (bannedWordService.containsBannedWord(userInput)) {
            log.warn("[ChatGuard] 输入命中违禁词，已拦截");
            return new GuardResult(false, "您的问题包含不适宜内容，请调整后再试。", "banned_word");
        }
        String masked = sensitiveInfoService.maskSensitiveInfo(userInput);
        return new GuardResult(true, masked, null);
    }

    public String guardOutput(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String filtered = bannedWordService.filterBannedWords(answer);
        // 去掉 Q:/A: 内部标记与重复问句，避免把知识库格式直接抛给用户
        filtered = AnswerFormatter.clean(filtered);
        return sensitiveInfoService.maskSensitiveInfo(filtered);
    }
}
