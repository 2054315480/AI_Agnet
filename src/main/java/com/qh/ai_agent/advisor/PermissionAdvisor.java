package com.qh.ai_agent.advisor;

import com.qh.ai_agent.exception.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

import java.util.Set;

@Slf4j
public class PermissionAdvisor implements BaseAdvisor {

    private final Set<String> allowedUsers;

    private final String currentUser;

    private int order = -100;

    public PermissionAdvisor(Set<String> allowedUsers, String currentUser) {
        this.allowedUsers = allowedUsers;
        this.currentUser = currentUser;
    }

    public PermissionAdvisor(Set<String> allowedUsers) {
        this(allowedUsers, null);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 验证用户权限
        if (currentUser != null && !allowedUsers.isEmpty() && !allowedUsers.contains(currentUser)) {
            log.warn("Permission denied for user: {}", currentUser);
            throw new PermissionDeniedException("User '" + currentUser + "' is not allowed to access this resource");
        }
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public PermissionAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Set<String> allowedUsers = Set.of();
        private String currentUser;
        private int order = 0;

        public Builder allowedUsers(Set<String> allowedUsers) {
            this.allowedUsers = allowedUsers;
            return this;
        }

        public Builder currentUser(String currentUser) {
            this.currentUser = currentUser;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public PermissionAdvisor build() {
            PermissionAdvisor advisor = new PermissionAdvisor(allowedUsers, currentUser);
            advisor.withOrder(order);
            return advisor;
        }
    }
}
