package com.qh.ai_agent.demo.invoke.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MultiQueryExpanderDemo {

    //查询扩展
    private final ChatClient.Builder  chatClientBuilder;

    public MultiQueryExpanderDemo(ChatModel dashscopeChatModel) {
        this.chatClientBuilder =ChatClient.builder(dashscopeChatModel);

    }
    public List<Query> expand(String query) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries = queryExpander.expand(new Query("秋鹤是干什么的啊"));
        return queries;


    }
}
