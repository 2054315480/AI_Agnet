package com.qh.ai_agent.controller;


import com.qh.ai_agent.Agent.HeManus;
import com.qh.ai_agent.app.LoveApp;
import com.qh.ai_agent.service.PromptTemplateService;
import com.qh.ai_agent.service.SensitiveInfoService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController()
@RequestMapping("/ai")
public class AiController {


    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private LoveApp loveApp;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private SensitiveInfoService sensitiveInfoService;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Value("${spring.ai.dashscope.image.options.model:qwen-vl-plus}")
    private String visionModel;

    @Value("${spring.ai.dashscope.chat.options.enable-thinking:true}")
    private boolean chatEnableThinking;

    /**
     * 合并本地工具和 MCP 工具
     */
    private ToolCallback[] getAllToolsWithMcp() {
        ToolCallback[] mcpCallbacks = toolCallbackProvider.getToolCallbacks();
        ToolCallback[] merged = new ToolCallback[allTools.length + mcpCallbacks.length];
        System.arraycopy(allTools, 0, merged, 0, allTools.length);
        System.arraycopy(mcpCallbacks, 0, merged, allTools.length, mcpCallbacks.length);
        return merged;
    }

    /**
     * 同步调用AI 的恋爱大师方法
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String daChatWithLoveAppSync(String message,String chatId) {

        return loveApp.doChat(message,chatId);

    }


    /**
     * SSE调用AI 的恋爱大师方法
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> daChatWithLoveAppSSE(String message, String chatId) {

        return loveApp.doChatByStream(message,chatId);

    }


    /**
     * SSE调用AI 的恋爱大师方法
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> daChatWithLoveAppServerSentEvent(String message, String chatId) {

        return loveApp.doChatByStream(message,chatId)
                    .map(chunk -> ServerSentEvent.<String>builder()
                    .data(chunk)
                            .build());

    }


    /**
     * SSE调用AI 的恋爱大师方法
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter daChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L);

        // 获取Flux 响应式编程数据流并且直接通过订阅推送 给SseEmitter
        loveApp.doChatByStream(message,chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    }catch (Exception e){
                        sseEmitter.completeWithError(e);
                    }
                },sseEmitter::completeWithError,sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * Love Agent SSE 流式对话（支持图片上传）
     */
    @PostMapping(value = "/love_app/chat/sse_with_image", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatLoveWithImage(
            @RequestParam("message") String message,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        if (image != null && !image.isEmpty()) {
            try {
                Media media = createMediaFromMultipartFile(image);
                return loveApp.doChatWithImageByStream(message, chatId, media);
            } catch (IOException e) {
                throw new RuntimeException("处理图片失败", e);
            }
        }
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     *  流式调用超级智能体
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        HeManus heManus = new HeManus(getAllToolsWithMcp(), dashscopeChatModel, promptTemplateService, sensitiveInfoService);
        heManus.setVisionModel(visionModel);
        heManus.setChatEnableThinking(chatEnableThinking);
        return heManus.runStream(message);

    }

    /**
     * Manus 超级智能体 SSE 流式对话（支持图片上传）
     */
    @PostMapping("/manus/chat_with_image")
    public SseEmitter chatManusWithImage(
            @RequestParam("message") String message,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        HeManus heManus = new HeManus(getAllToolsWithMcp(), dashscopeChatModel, promptTemplateService, sensitiveInfoService);
        heManus.setVisionModel(visionModel);
        heManus.setChatEnableThinking(chatEnableThinking);

        if (image != null && !image.isEmpty()) {
            try {
                Media media = createMediaFromMultipartFile(image);
                return heManus.runStream(message, List.of(media));
            } catch (IOException e) {
                throw new RuntimeException("处理图片失败", e);
            }
        }
        return heManus.runStream(message);
    }

    /**
     * 获取可用的 Prompt 模板列表
     */
    @GetMapping("/templates")
    public Map<String, String> listTemplates() {
        return promptTemplateService.listTemplatesWithPreview();
    }

    /**
     * 将 MultipartFile 转换为 Spring AI 的 Media 对象
     * 使用 Media.builder().data(byte[]) 确保 DashScope 能正确处理
     */
    private Media createMediaFromMultipartFile(MultipartFile file) throws IOException {
        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }
        byte[] imageData = file.getBytes();
        return Media.builder()
                .mimeType(MimeType.valueOf(mimeType))
                .data(imageData)
                .build();
    }

}
