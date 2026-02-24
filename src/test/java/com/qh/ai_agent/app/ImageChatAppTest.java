package com.qh.ai_agent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 图片对话应用单元测试
 */
@SpringBootTest
@Slf4j
class ImageChatAppTest {

    @Resource
    private ImageChatApp imageChatApp;

    /**
     * 测试通过URL解释图片
     * 使用公开的测试图片URL
     */
    @Test
    void testExplainImageByUrl() {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/280px-PNG_transparency_demonstration_1.png";
        String question = "请详细描述这张图片的内容";

        String response = imageChatApp.explainImage(imageUrl, question);

        log.info("图片解释结果: {}", response);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.trim().isEmpty());
    }

    /**
     * 测试通过Base64字节数组解释图片
     * 注意：实际测试时需要准备真实的图片数据
     */
    @Test
    void testExplainImageByBytes() {
        // 创建一个简单的测试图片数据（1x1像素的PNG）
        // 实际应用中应该使用真实的图片文件
        byte[] imageData = createTestImageData();

        String question = "请描述这张图片";
        String response = imageChatApp.explainImage(imageData, question);

        log.info("图片解释结果: {}", response);
        Assertions.assertNotNull(response);
    }

    /**
     * 测试自定义问题
     */
    @Test
    void testExplainImageWithCustomQuestion() {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/280px-PNG_transparency_demonstration_1.png";
        String question = "图片中有哪些颜色？请列出主要色调";

        String response = imageChatApp.explainImage(imageUrl, question);

        log.info("图片解释结果: {}", response);
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.length() > 10);
    }

    /**
     * 创建测试图片数据
     * 这是一个简单的1x1像素PNG图片的Base64编码
     */
    private byte[] createTestImageData() {
        // 1x1像素红色PNG图片的Base64
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        return java.util.Base64.getDecoder().decode(base64Image);
    }
}
