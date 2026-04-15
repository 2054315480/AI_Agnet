package com.qh.ai_agent.controller;


import com.qh.ai_agent.Agent.HeManus;
import com.qh.ai_agent.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController()
@RequestMapping("/ai")
public class AiController {


    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private LoveApp loveApp;

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
        // 创建一个超市使劲按较长的 SseEmitter
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
     *  流式调用超级智能体
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        HeManus heManus = new HeManus(allTools,dashscopeChatModel);

        return heManus.runStream(message);

    }

}
