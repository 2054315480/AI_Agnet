package com.qh.ai_agent.advisor;

import com.qh.ai_agent.exception.BannedWordException;
import com.qh.ai_agent.service.BannedWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

@Slf4j
public class BannedWordAdvisor implements BaseAdvisor {

    private final BannedWordService bannedWordService;

    private final boolean filterResponse;

    private int order = -50;

    public BannedWordAdvisor(BannedWordService bannedWordService) {
        this(bannedWordService, true);
    }

    public BannedWordAdvisor(BannedWordService bannedWordService, boolean filterResponse) {
        this.bannedWordService = bannedWordService;
        this.filterResponse = filterResponse;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 检查请求中的违禁词
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        if (userText != null) {
            bannedWordService.checkBannedWord(userText);
        }
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 检查响应中的违禁词
        if (filterResponse && chatClientResponse != null) {
            var response = chatClientResponse.chatResponse();
            if (response != null && response.getResult() != null) {
                String outputText = response.getResult().getOutput().getText();
                if (outputText != null && bannedWordService.containsBannedWord(outputText)) {
                    log.warn("Response contains banned words, filtering...");
                    String filteredText = bannedWordService.filterBannedWords(outputText);
                    log.info("Filtered text: {}", filteredText);
                }
            }
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public BannedWordAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BannedWordService bannedWordService;
        private boolean filterResponse = true;
        private int order = 0;

        public Builder bannedWordService(BannedWordService bannedWordService) {
            this.bannedWordService = bannedWordService;
            return this;
        }

        public Builder filterResponse(boolean filterResponse) {
            this.filterResponse = filterResponse;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public BannedWordAdvisor build() {
            BannedWordAdvisor advisor = new BannedWordAdvisor(bannedWordService, filterResponse);
            advisor.withOrder(order);
            return advisor;
        }
    }
}
