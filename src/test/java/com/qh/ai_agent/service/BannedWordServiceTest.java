package com.qh.ai_agent.service;

import com.qh.ai_agent.exception.BannedWordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BannedWordServiceTest {

    private BannedWordService service;

    @BeforeEach
    void setUp() {
        service = new BannedWordService("垃圾,暴力,色情");
    }

    @Test
    void testContainsBannedWord() {
        assertTrue(service.containsBannedWord("这是一条包含垃圾的消息"));
        assertTrue(service.containsBannedWord("这是一条包含暴力的消息"));
        assertTrue(service.containsBannedWord("这是一条包含色情的消息"));
    }

    @Test
    void testDoesNotContainBannedWord() {
        assertFalse(service.containsBannedWord("这是一条正常的消息"));
        assertFalse(service.containsBannedWord(""));
        assertFalse(service.containsBannedWord(null));
    }

    @Test
    void testCaseInsensitive() {
        assertTrue(service.containsBannedWord("这是一条包含垃圾的内容"));
        assertTrue(service.containsBannedWord("这是一条包含暴力测试"));
    }

    @Test
    void testCheckBannedWordThrowsException() {
        BannedWordException exception = assertThrows(
                BannedWordException.class,
                () -> service.checkBannedWord("这是一条包含垃圾的消息")
        );

        assertTrue(exception.getMessage().contains("垃圾"));
        assertEquals("垃圾", exception.getBannedWord());
    }

    @Test
    void testCheckBannedWordDoesNotThrow() {
        assertDoesNotThrow(() -> service.checkBannedWord("这是一条正常的消息"));
    }

    @Test
    void testFilterBannedWords() {
        String result = service.filterBannedWords("这是一条包含垃圾和暴力的消息");
        assertFalse(result.contains("垃圾"));
        assertFalse(result.contains("暴力"));
        assertTrue(result.contains("***"));
    }

    @Test
    void testFilterCleanText() {
        String original = "这是一条正常的消息";
        String result = service.filterBannedWords(original);
        assertEquals(original, result);
    }

    @Test
    void testFilterNullText() {
        String result = service.filterBannedWords(null);
        assertNull(result);
    }

    @Test
    void testFilterEmptyText() {
        String result = service.filterBannedWords("");
        assertEquals("", result);
    }

    @Test
    void testGetBannedWords() {
        Set<String> words = service.getBannedWords();
        assertTrue(words.contains("垃圾"));
        assertTrue(words.contains("暴力"));
        assertTrue(words.contains("色情"));
        assertEquals(3, words.size());
    }

    @Test
    void testAddBannedWord() {
        service.addBannedWord("赌博");
        assertTrue(service.containsBannedWord("这条消息包含赌博内容"));
    }

    @Test
    void testRemoveBannedWord() {
        service.removeBannedWord("垃圾");
        assertFalse(service.containsBannedWord("这条消息包含垃圾内容"));
    }

    @Test
    void testEmptyConfig() {
        BannedWordService emptyService = new BannedWordService("");
        assertFalse(emptyService.containsBannedWord("任何内容"));
    }

    @Test
    void testMultipleSeparators() {
        BannedWordService multiService = new BannedWordService("垃圾;暴力,色情 测试");
        assertTrue(multiService.containsBannedWord("垃圾"));
        assertTrue(multiService.containsBannedWord("暴力"));
        assertTrue(multiService.containsBannedWord("色情"));
        assertTrue(multiService.containsBannedWord("测试"));
    }

    @Test
    void testPartialMatch() {
        assertTrue(service.containsBannedWord("这是垃圾话"));
        assertTrue(service.containsBannedWord("暴力狂"));
    }

    @Test
    void testAddNullWord() {
        int originalSize = service.getBannedWords().size();
        service.addBannedWord(null);
        assertEquals(originalSize, service.getBannedWords().size());

        service.addBannedWord("");
        assertEquals(originalSize, service.getBannedWords().size());
    }
}
