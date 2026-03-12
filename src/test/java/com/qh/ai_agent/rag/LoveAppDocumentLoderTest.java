package com.qh.ai_agent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class LoveAppDocumentLoderTest {

    @Resource
    private LoveAppDocumentLoder loveAppDocumentLoder;
    @Test
    void loadMarkdown() {
        loveAppDocumentLoder.loadMarkdown();
    }
}