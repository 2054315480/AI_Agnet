package com.qh.ai_agent.advisor;

import com.qh.ai_agent.service.SensitiveInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感信息脱敏 Advisor
 * 自动检测并脱敏用户输入和 AI 响应中的敏感信息
 * （手机号、身份证号、邮箱、银行卡号）
 *
 * Order: -75，位于 PermissionAdvisor(-100) 和 BannedWordAdvisor(-50) 之间
 */
@Slf4j
public class SensitiveInfoAdvisor implements BaseAdvisor {

    private final SensitiveInfoService sensitiveInfoService;

    private final boolean maskResponse;

    private int order = -75;

    public SensitiveInfoAdvisor(SensitiveInfoService sensitiveInfoService) {
        this(sensitiveInfoService, true);
    }

    public SensitiveInfoAdvisor(SensitiveInfoService sensitiveInfoService, boolean maskResponse) {
        this.sensitiveInfoService = sensitiveInfoService;
        this.maskResponse = maskResponse;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        var userMessage = chatClientRequest.prompt().getUserMessage();
        if (userMessage == null) {
            return chatClientRequest;
        }

        String userText = userMessage.getText();
        if (userText != null && sensitiveInfoService.containsSensitiveInfo(userText)) {
            List<String> types = sensitiveInfoService.findSensitiveTypes(userText);
            String masked = sensitiveInfoService.maskSensitiveInfo(userText);
            log.info("检测到用户输入包含敏感信息类型: {}，已自动脱敏", types);

            // 构建脱敏后的 UserMessage，保留原始 media
            UserMessage maskedUserMessage = UserMessage.builder()
                    .text(masked)
                    .media(userMessage.getMedia())
                    .build();

            // 替换消息列表中的最后一条 UserMessage
            List<Message> newMessages = new ArrayList<>(chatClientRequest.prompt().getInstructions());
            for (int i = newMessages.size() - 1; i >= 0; i--) {
                if (newMessages.get(i) instanceof UserMessage) {
                    newMessages.set(i, maskedUserMessage);
                    break;
                }
            }

            var newPrompt = chatClientRequest.prompt().mutate()
                    .messages(newMessages)
                    .build();

            return chatClientRequest.mutate()
                    .prompt(newPrompt)
                    .build();
        }
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        if (maskResponse && chatClientResponse != null) {
            var response = chatClientResponse.chatResponse();
            if (response != null && response.getResult() != null) {
                String outputText = response.getResult().getOutput().getText();
                if (outputText != null && sensitiveInfoService.containsSensitiveInfo(outputText)) {
                    List<String> types = sensitiveInfoService.findSensitiveTypes(outputText);
                    log.warn("AI 响应中包含敏感信息类型: {}，建议审查", types);
                }
            }
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public SensitiveInfoAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SensitiveInfoService sensitiveInfoService;
        private boolean maskResponse = true;
        private int order = -75;

        public Builder sensitiveInfoService(SensitiveInfoService sensitiveInfoService) {
            this.sensitiveInfoService = sensitiveInfoService;
            return this;
        }

        public Builder maskResponse(boolean maskResponse) {
            this.maskResponse = maskResponse;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public SensitiveInfoAdvisor build() {
            SensitiveInfoAdvisor advisor = new SensitiveInfoAdvisor(sensitiveInfoService, maskResponse);
            advisor.withOrder(order);
            return advisor;
        }
    }
}
