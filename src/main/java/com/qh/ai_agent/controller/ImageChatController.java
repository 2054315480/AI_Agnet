package com.qh.ai_agent.controller;

import com.qh.ai_agent.app.ImageChatApp;
import com.qh.ai_agent.model.ImageChatRequest;
import com.qh.ai_agent.model.ImageChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * 图片对话控制器，提供图片解释接口
 */
@RestController
@RequestMapping("/image-chat")
@Tag(name = "图片对话", description = "多模态图片对话接口")
@Slf4j
public class ImageChatController {

    @Resource
    private ImageChatApp imageChatApp;

    /**
     * 通过图片URL解释图片
     */
    @PostMapping("/explain")
    @Operation(summary = "解释图片", description = "通过图片URL解释图片内容")
    public ResponseEntity<ImageChatResponse> explainImage(@RequestBody ImageChatRequest request) {
        try {
            log.info("收到图片解释请求 - URL: {}, 问题: {}", request.getImageUrl(), request.getQuestion());

            // 参数校验
            if (request.getImageUrl() == null || request.getImageUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ImageChatResponse("图片URL不能为空", null));
            }

            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                request.setQuestion("请详细描述这张图片的内容");
            }

            String content = imageChatApp.explainImage(request.getImageUrl(), request.getQuestion());
            return ResponseEntity.ok(ImageChatResponse.success(content));

        } catch (Exception e) {
            log.error("图片解释失败", e);
            return ResponseEntity.internalServerError()
                .body(new ImageChatResponse("图片解释失败: " + e.getMessage(), null));
        }
    }

    /**
     * 通过上传图片文件解释图片
     */
    @PostMapping("/upload-explain")
    @Operation(summary = "上传并解释图片", description = "上传图片文件并解释内容")
    public ResponseEntity<ImageChatResponse> explainUploadedImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "question", required = false) String question) {
        try {
            log.info("收到图片上传解释请求 - 文件名: {}, 大小: {}, ContentType: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

            // 参数校验
            if (file.isEmpty()) {
                log.warn("上传文件为空");
                return ResponseEntity.badRequest()
                    .body(new ImageChatResponse("上传文件不能为空", null));
            }

            // 校验文件类型
            String contentType = file.getContentType();
            log.info("文件ContentType: {}", contentType);

            // 允许的图片类型
            if (contentType == null || (!contentType.startsWith("image/"))) {
                log.warn("不支持的文件类型: {}", contentType);
                return ResponseEntity.badRequest()
                    .body(new ImageChatResponse("只支持图片文件（jpg、png、gif、webp、bmp）", null));
            }

            // 设置默认问题
            if (question == null || question.trim().isEmpty()) {
                question = "请详细描述这张图片的内容";
                log.info("使用默认问题: {}", question);
            } else {
                log.info("用户问题: {}", question);
            }

            // 将文件转换为字节数组
            byte[] imageData = file.getBytes();
            log.info("图片数据读取成功，大小: {} bytes", imageData.length);

            // 调用AI服务解释图片
            String content = imageChatApp.explainImage(imageData, question);
            log.info("图片解释成功，响应长度: {}", content.length());

            return ResponseEntity.ok(ImageChatResponse.success(content));

        } catch (IOException e) {
            log.error("读取上传文件失败 - 文件名: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError()
                .body(new ImageChatResponse("读取上传文件失败: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("图片解释失败 - 文件名: {}, 问题: {}", file.getOriginalFilename(), question, e);
            return ResponseEntity.internalServerError()
                .body(new ImageChatResponse("图片解释失败: " + e.getMessage(), null));
        }
    }
}
