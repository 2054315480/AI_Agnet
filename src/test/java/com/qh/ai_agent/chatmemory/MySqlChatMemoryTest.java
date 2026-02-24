package com.qh.ai_agent.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qh.ai_agent.entity.ChatMemoryEntity;
import com.qh.ai_agent.mapper.ChatMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySqlChatMemory 测试类
 * 使用 H2 内存数据库进行测试
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl"
})
class MySqlChatMemoryTest {

    @Autowired
    private MySqlChatMemory chatMemory;

    @Autowired
    private ChatMemoryMapper chatMemoryMapper;

    private static final String CONVERSATION_ID = "test-conversation-1";

    /**
     * 每次测试前清空数据
     */
    @BeforeEach
    void setUp() {
        // 使用 MyBatis-Plus 的方式删除所有数据
        LambdaQueryWrapper<ChatMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        chatMemoryMapper.delete(wrapper);
    }

    @Test
    @Transactional
    void testSaveAndRetrieveMessages() {
        Message userMessage = new UserMessage("Hello, how are you?");
        Message assistantMessage = new AssistantMessage("I'm doing well, thank you!");

        chatMemory.add(CONVERSATION_ID, userMessage);
        chatMemory.add(CONVERSATION_ID, assistantMessage);

        List<Message> messages = chatMemory.get(CONVERSATION_ID);

        assertEquals(2, messages.size());
        assertEquals("Hello, how are you?", ((UserMessage) messages.get(0)).getText());
        assertEquals(MessageType.USER, messages.get(0).getMessageType());
        assertEquals("I'm doing well, thank you!", ((AssistantMessage) messages.get(1)).getText());
        assertEquals(MessageType.ASSISTANT, messages.get(1).getMessageType());
    }

    @Test
    @Transactional
    void testClearConversation() {
        Message message = new UserMessage("Test message");
        chatMemory.add(CONVERSATION_ID, message);

        List<Message> beforeClear = chatMemory.get(CONVERSATION_ID);
        assertEquals(1, beforeClear.size());

        chatMemory.clear(CONVERSATION_ID);

        List<Message> afterClear = chatMemory.get(CONVERSATION_ID);
        assertTrue(afterClear.isEmpty());
    }

    @Test
    @Transactional
    void testMultipleRoundsOfConversation() {
        for (int i = 1; i <= 3; i++) {
            Message userMessage = new UserMessage("User message " + i);
            Message assistantMessage = new AssistantMessage("Assistant response " + i);

            chatMemory.add(CONVERSATION_ID, List.of(userMessage, assistantMessage));
        }

        List<Message> messages = chatMemory.get(CONVERSATION_ID);

        assertEquals(6, messages.size());
        assertEquals("User message 1", ((UserMessage) messages.get(0)).getText());
        assertEquals("Assistant response 1", ((AssistantMessage) messages.get(1)).getText());
        assertEquals("User message 2", ((UserMessage) messages.get(2)).getText());
        assertEquals("Assistant response 2", ((AssistantMessage) messages.get(3)).getText());
        assertEquals("User message 3", ((UserMessage) messages.get(4)).getText());
        assertEquals("Assistant response 3", ((AssistantMessage) messages.get(5)).getText());
    }

    @Test
    @Transactional
    void testAddMultipleMessagesAtOnce() {
        Message message1 = new UserMessage("First message");
        Message message2 = new AssistantMessage("Second message");
        Message message3 = new UserMessage("Third message");

        chatMemory.add(CONVERSATION_ID, List.of(message1, message2, message3));

        List<Message> messages = chatMemory.get(CONVERSATION_ID);

        assertEquals(3, messages.size());
    }

    @Test
    @Transactional
    void testGetNonExistentConversation() {
        List<Message> messages = chatMemory.get("non-existent-conversation");
        assertTrue(messages.isEmpty());
    }
}
