package com.qh.ai_agent.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 图片对话应用，支持多模态图片理解
 */
@Component
@Slf4j
public class ImageChatApp {

    private final ChatClient chatClient;
    private final String systemPrompt;

    public ImageChatApp(ChatModel chatModel,
                        @Value("classpath:prompts/image-analyst.txt") Resource systemPromptResource) throws IOException {
        log.info("初始化 ImageChatApp，使用的 ChatModel 类型: {}", chatModel.getClass().getName());
        this.chatClient = ChatClient.builder(chatModel).build();
        this.systemPrompt = systemPromptResource.exists() ?
            systemPromptResource.getContentAsString(StandardCharsets.UTF_8) : getDefaultSystemPrompt();
        log.info("ImageChatApp 初始化完成，系统提示词长度: {}", systemPrompt.length());
    }

    /**
     * 解释图片（通过URL）
     */
    public String explainImage(String imageUrl, String question) {
        log.info("解释图片 - URL: {}, 问题: {}", imageUrl, question);

        try {
            // 根据URL判断图片类型
            String mimeType = detectMimeTypeFromUrl(imageUrl);

            // 创建 Media 对象，使用 URI
            Media media = new Media(MimeType.valueOf(mimeType), new java.net.URI(imageUrl));

            String response = chatClient.prompt()
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
        log.info("解释图片 - 数据大小: {} bytes, 问题: {}", imageData.length, question);

        try {
            // 根据文件头判断图片类型
            String mimeType = detectMimeType(imageData);
            log.info("检测到图片类型: {}", mimeType);

            // 创建 Resource 对象
            org.springframework.core.io.Resource imageResource =
                new org.springframework.core.io.ByteArrayResource(imageData) {
                    @Override
                    public String getFilename() {
                        String extension = switch (mimeType) {
                            case "image/png" -> "png";
                            case "image/gif" -> "gif";
                            case "image/webp" -> "webp";
                            case "image/bmp" -> "bmp";
                            default -> "jpg";
                        };
                        return "image." + extension;
                    }
                };

            // 创建 Media 对象
            Media media = new Media(MimeType.valueOf(mimeType), imageResource);

            String response = chatClient.prompt()
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

    private String getDefaultSystemPrompt() {
        return "你是一位专业的图片分析师，擅长详细描述图片内容、分析图片中的元素并提供深入的见解。";
    }
}
