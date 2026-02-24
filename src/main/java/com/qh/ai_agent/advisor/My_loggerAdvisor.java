//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//  根据自己的需求进行自定义拦截器
//

package com.qh.ai_agent.advisor;

import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

@Slf4j
public class My_loggerAdvisor implements CallAdvisor, StreamAdvisor {
    private static final Logger logger = LoggerFactory.getLogger(My_loggerAdvisor.class);
    private final int order;




    public My_loggerAdvisor( int order) {
        this.order = order;
    }

    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        this.logRequest(chatClientRequest);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        this.logResponse(chatClientResponse);
        return chatClientResponse;
    }

    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        this.logRequest(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponses, this::logResponse);
    }
    private String extractUserText(ChatClientRequest request) {
        return request.prompt()
                .getUserMessage()  // 获取用户消息
                .getText();        // 获取文本内容
    }
    protected void logRequest(ChatClientRequest request) {
        logger.info("AI request {} " + request.prompt().getUserMessage().getText());
    }

    protected void logResponse(ChatClientResponse chatClientResponse) {

        logger.info("AI response {} "  ,chatClientResponse.chatResponse().getResult().getOutput().getText() );
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return this.order;
    }

    public String toString() {
        return My_loggerAdvisor.class.getSimpleName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int order = 0;

        private Builder() {
        }

        public Builder requestToString(int order) {
            this.order=order;
            return this;
        }



        public Builder order(int order) {
            this.order = 0;
            return this;
        }

        public My_loggerAdvisor build() {
            return new My_loggerAdvisor( this.order);
        }
    }
}
