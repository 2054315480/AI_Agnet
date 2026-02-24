package com.qh.ai_agent.advisor;

import com.qh.ai_agent.exception.BannedWordException;
import com.qh.ai_agent.service.BannedWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BannedWordAdvisorTest {

    private BannedWordService bannedWordService;
    private BannedWordAdvisor advisor;
    private AdvisorChain chain;
    private ChatClientRequest request;
    private ChatClientResponse response;

    @BeforeEach
    void setUp() {
        bannedWordService = new BannedWordService("垃圾,暴力,色情");
        chain = mock(AdvisorChain.class);
    }

    private void setupRequest(String text) {
        Message userMessage = new UserMessage(text);
        Prompt prompt = new Prompt(userMessage);
        request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(prompt);
    }

    private void setupResponse(String text) {
        AssistantMessage assistantMessage = new AssistantMessage(text);
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);

        response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);
    }

    @Test
    void testCleanMessagePasses() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        setupRequest("这是一条正常消息");
        ChatClientRequest result = advisor.before(request, chain);
        assertNotNull(result);
    }

    @Test
    void testBannedWordInRequestThrowsException() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        setupRequest("这是一条包含垃圾的消息");

        BannedWordException exception = assertThrows(
                BannedWordException.class,
                () -> advisor.before(request, chain)
        );

        assertTrue(exception.getMessage().contains("垃圾"));
    }

    @Test
    void testCleanResponsePasses() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .filterResponse(true)
                .order(-50)
                .build();

        setupResponse("这是一条正常的响应");

        ChatClientResponse result = advisor.after(response, chain);
        assertNotNull(result);
    }

    @Test
    void testBannedWordInResponseLogsWarning() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .filterResponse(true)
                .order(-50)
                .build();

        setupResponse("这是一条包含暴力的响应");

        ChatClientResponse result = advisor.after(response, chain);
        assertNotNull(result);
    }

    @Test
    void testFilterResponseDisabled() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .filterResponse(false)
                .order(-50)
                .build();

        setupResponse("这是一条包含色情的响应");

        ChatClientResponse result = advisor.after(response, chain);
        assertNotNull(result);
    }

    @Test
    void testMultipleBannedWords() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        setupRequest("这条消息包含垃圾和暴力内容");

        BannedWordException exception = assertThrows(
                BannedWordException.class,
                () -> advisor.before(request, chain)
        );

        assertNotNull(exception.getBannedWord());
    }

    @Test
    void testCaseInsensitive() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        setupRequest("这条消息包含垃圾测试内容");

        BannedWordException exception = assertThrows(
                BannedWordException.class,
                () -> advisor.before(request, chain)
        );

        assertTrue(exception.getMessage().toLowerCase().contains("垃圾"));
    }

    @Test
    void testEmptyMessageText() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        setupRequest("");

        ChatClientRequest result = advisor.before(request, chain);
        assertNotNull(result);
    }

    @Test
    void testOrder() {
        advisor = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .order(-50)
                .build();

        assertEquals(-50, advisor.getOrder());
    }
}
