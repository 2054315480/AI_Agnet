package com.qh.ai_agent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

public class LoveAppContexttualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance () {
        PromptTemplate emptypromptTemplate = new PromptTemplate(
                "你应该直接输出下面的内容" +
                        "非常不好意思，我暂时只能回复恋爱相关的问题" +
                        "其他问题暂时帮助不到您哦" +
                        "如果有疑问，请联系关系员" +
                        "或者直接访问 https://gitee.com/qiuhee/ai-super-intelligent-agent"

        );
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptypromptTemplate)
                .build();

    }

}
