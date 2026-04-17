package com.qh.ai_agent.app;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.qh.ai_agent.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * 图片对话应用，支持多模态图片理解
 */
@Component
@Slf4j
public class ImageChatApp {

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String visionModel;

    public ImageChatApp(ChatModel chatModel,
                        PromptTemplateService promptTemplateService,
                        @Value("${spring.ai.dashscope.image.options.model:qwen-vl-plus}") String visionModel) {
        log.info("初始化 ImageChatApp，ChatModel 类型: {}, 视觉模型: {}", chatModel.getClass().getName(), visionModel);
        this.chatClient = ChatClient.builder(chatModel).build();
        this.visionModel = visionModel;
        this.systemPrompt = promptTemplateService.loadTemplate("image-analyst")
                .renderWithDefaults(null);
        log.info("ImageChatApp 初始化完成，系统提示词长度: {}", systemPrompt.length());
    }

    /**
     * 解释图片（通过URL）
     * 先下载图片为字节数组，再以 base64 方式发送给百炼 API
     */
    public String explainImage(String imageUrl, String question) {
        log.info("解释图片 - URL: {}, 问题: {}, 视觉模型: {}", imageUrl, question, visionModel);

        try {
            // 先下载图片到字节数组，避免 URL 兼容性问题
            java.net.URL url = new java.net.URI(imageUrl).toURL();
            byte[] imageData = url.openStream().readAllBytes();
            log.info("图片下载成功，大小: {} bytes", imageData.length);

            // 委托给字节数组方法
            return explainImage(imageData, question);
        } catch (Exception e) {
            log.error("解释图片失败", e);
            throw new RuntimeException("解释图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据URL检测图片类型
     */
    private String detectMimeTypeFromUrl(String imageUrl) {
        String lowerUrl = imageUrl.toLowerCase();
        if (lowerUrl.endsWith(".png")) {
            return "image/png";
        } else if (lowerUrl.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerUrl.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerUrl.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg"; // 默认
    }

    /**
     * 解释图片（通过字节数组，自动检测图片类型）
     */
    public String explainImage(byte[] imageData, String question) {
        log.info("解释图片 - 数据大小: {} bytes, 问题: {}, 视觉模型: {}", imageData.length, question, visionModel);

        try {
            // 根据文件头判断图片类型
            String mimeType = detectMimeType(imageData);
            log.info("检测到图片类型: {}", mimeType);

            // 关键：用 Media.builder().data(byte[]) 传入字节数组
            // 不能用 new Media(MimeType, Resource)，因为 DashScope 的 fromMediaData 只处理 byte[] 和 String
            Media media = Media.builder()
                    .mimeType(MimeType.valueOf(mimeType))
                    .data(imageData)
                    .build();

            String response = chatClient.prompt()
                .options(DashScopeChatOptions.builder()
                    .withModel(visionModel)
                    .withMultiModel(true)
                    .withEnableThinking(false)
                    .build())
                .system(systemPrompt)
                .user(u -> u
                    .text(question)
                    .media(media)
                )
                .call()
                .content();

            log.info("图片解释完成，响应长度: {}", response.length());
            return response;

        } catch (Exception e) {
            log.error("解释图片失败 - 数据大小: {} bytes, 问题: {}", imageData.length, question, e);
            throw new RuntimeException("解释图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件头检测图片类型
     */
    private String detectMimeType(byte[] imageData) {
        if (imageData == null || imageData.length < 4) {
            return "image/jpeg"; // 默认
        }

        // PNG: 89 50 4E 47
        if (imageData[0] == (byte) 0x89 && imageData[1] == 0x50 &&
            imageData[2] == 0x4E && imageData[3] == 0x47) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8 &&
            imageData[2] == (byte) 0xFF) {
            return "image/jpeg";
        }

        // GIF: 47 49 46 38 (GIF8)
        if (imageData[0] == 0x47 && imageData[1] == 0x49 &&
            imageData[2] == 0x46 && imageData[3] == 0x38) {
            return "image/gif";
        }

        // WebP: 52 49 46 46 ... 57 45 42 50
        if (imageData[0] == 0x52 && imageData[1] == 0x49 &&
            imageData[2] == 0x46 && imageData[3] == 0x46) {
            return "image/webp";
        }

        // BMP: 42 4D
        if (imageData[0] == 0x42 && imageData[1] == 0x4D) {
            return "image/bmp";
        }

        return "image/jpeg"; // 默认使用 JPEG
    }
}
