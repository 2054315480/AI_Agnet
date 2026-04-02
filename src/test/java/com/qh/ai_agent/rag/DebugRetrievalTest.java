package com.qh.ai_agent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class DebugRetrievalTest {

    @Resource
    private VectorStore pgvectorVectorStore;

    @Test
    void debugWhatIsRetrieved() {
        System.out.println("========== 调试：检索返回的内容 ==========");

        List<org.springframework.ai.document.Document> results = pgvectorVectorStore.similaritySearch(
            SearchRequest.builder()
                .query("婚后关系不好怎么办")
                .topK(3)
                .build()
        );

        System.out.println("检索到 " + results.size() + " 条文档：\n");

        for (int i = 0; i < results.size(); i++) {
            var doc = results.get(i);
            System.out.println("===== 文档 " + (i + 1) + " =====");
            System.out.println("元数据: " + doc.getMetadata());
            System.out.println("内容长度: " + doc.getText().length() + " 字符");
            System.out.println("内容前200字符:");
            System.out.println(doc.getText().substring(0, Math.min(200, doc.getText().length())));
            System.out.println();

            // 检查是否包含链接
            String content = doc.getText();
            if (content.contains("http")) {
                System.out.println("✓ 包含 http 链接");
                int httpIndex = content.indexOf("http");
                System.out.println("链接内容: " + content.substring(httpIndex, Math.min(httpIndex + 100, content.length())));
            } else if (content.contains("gitee")) {
                System.out.println("✓ 包含 gitee 但没有 http");
            } else {
                System.out.println("✗ 不包含任何链接");
            }
            System.out.println();
        }
    }
}
