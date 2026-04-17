package com.qh.ai_agent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 图片对话应用单元测试
 */
@SpringBootTest
@Slf4j
class ImageChatAppTest {

    @Resource
    private ImageChatApp imageChatApp;

    /**
     * 测试通过本地图片文件（字节数组）解释图片
     * 使用本地图片文件，避免 URL 访问问题
     */
    @Test
    void testExplainImageByLocalFile() throws Exception {
        // 读取本地图片文件
        Path imagePath = Paths.get("D:\\AI_Agent\\tmp\\Download\\images\\test.webp");
        byte[] imageData = Files.readAllBytes(imagePath);
        log.info("读取图片成功，文件大小: {} bytes", imageData.length);

        String question = "请详细描述这张图片的内容";
        String response = imageChatApp.explainImage(imageData, question);

        log.info("图片解释结果: {}", response);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.trim().isEmpty());
    }

    /**
     * 测试通过URL解释图片
     * 使用公开的测试图片URL
     */
    @Test
    void testExplainImageByUrl() {
        String imageUrl = "https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg";
        String question = "请详细描述这张图片的内容";

        String response = imageChatApp.explainImage(imageUrl, question);

        log.info("图片解释结果: {}", response);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.trim().isEmpty());
    }
}
