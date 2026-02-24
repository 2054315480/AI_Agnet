package com.qh.ai_agent.advisor;

import com.qh.ai_agent.exception.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionAdvisorTest {

    private PermissionAdvisor advisor;
    private AdvisorChain chain;
    private ChatClientRequest request;

    @BeforeEach
    void setUp() {
        chain = mock(AdvisorChain.class);

        Message userMessage = new UserMessage("test message");
        Prompt prompt = new Prompt(userMessage);
        request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(prompt);
    }

    @Test
    void testAllowedUserPasses() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of("user1", "admin"))
                .currentUser("user1")
                .order(-100)
                .build();

        ChatClientRequest result = advisor.before(request, chain);
        assertNotNull(result);
    }

    @Test
    void testDeniedUserThrowsException() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of("user1", "admin"))
                .currentUser("unauthorized_user")
                .order(-100)
                .build();

        PermissionDeniedException exception = assertThrows(
                PermissionDeniedException.class,
                () -> advisor.before(request, chain)
        );

        assertTrue(exception.getMessage().contains("unauthorized_user"));
    }

    @Test
    void testEmptyAllowedUsersPasses() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of())
                .currentUser("any_user")
                .order(-100)
                .build();

        ChatClientRequest result = advisor.before(request, chain);
        assertNotNull(result);
    }

    @Test
    void testNullCurrentUserPasses() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of("user1", "admin"))
                .currentUser(null)
                .order(-100)
                .build();

        ChatClientRequest result = advisor.before(request, chain);
        assertNotNull(result);
    }

    @Test
    void testOrder() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of("user1"))
                .order(-100)
                .build();

        assertEquals(-100, advisor.getOrder());
    }

    @Test
    void testWithOrder() {
        advisor = PermissionAdvisor.builder()
                .allowedUsers(Set.of("user1"))
                .build();

        advisor.withOrder(-200);
        assertEquals(-200, advisor.getOrder());
    }
}
