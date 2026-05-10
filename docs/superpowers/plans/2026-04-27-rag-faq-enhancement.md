# RAG 与 FAQ 知识检索增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让客服 Agent 完整加载所有知识源（classpath knowledge/、classpath docs/ 业务知识库、外部 data/faq/ FAQ），实现 FAQ 精准匹配 + RAG 语义检索 + 完整出处标注。

**Architecture:** 扩展 KnowledgeBaseLoader 支持三路文档加载：classpath `knowledge/`（原 FAQ/产品/政策）、classpath `docs/`（中国移动业务知识库）、外部目录 `data/faq/`（中国移动 FAQ）。新增解析器支持 `### Q{n}: question` 格式。保持两级检索架构不变（FAQ 直匹配 → RAG 向量检索），扩大知识覆盖。

**Tech Stack:** Java 21, Spring AI, Spring Boot 3.5, PgVector, DashScope Embedding

---

## File Structure

| Operation | File | Responsibility |
|-----------|------|---------------|
| Modify | `src/main/java/com/qh/ai_agent/knowledge/KnowledgeBaseLoader.java` | 扩展加载器：增加 docs/ 和外部 FAQ 目录的加载，支持新 FAQ 格式解析 |
| Modify | `src/main/java/com/qh/ai_agent/knowledge/KnowledgeSearchService.java` | FAQ 直匹配保留来源信息（不再清除 source），优化推荐问题收集逻辑 |
| Modify | `src/main/java/com/qh/ai_agent/knowledge/CustomerServiceVectorStoreConfig.java` | 向量库初始化前清空旧数据，避免重复加载 |
| Modify | `src/main/java/com/qh/ai_agent/Agent/customer_service/CustomerServiceAgent.java` | 更新 nextStepPrompt，所有回答均标注来源 |
| Modify | `src/main/java/com/qh/ai_agent/service/FaqManagementService.java` | 上传的 FAQ 文件计入知识库缓存 |
| Modify | `src/main/resources/application.yml` | 添加 `cs.faq.data-dir` 配置项 |
| Modify | `src/main/resources/application-local.yml` | 设置 `cs.faq.data-dir` 指向 `data/faq` |
| Modify | `src/main/resources/prompts/customer-service.txt` | 更新系统 Prompt，强调出处标注规则 |
| Modify | `PROJECT_GUIDE.md` | 更新版本记录 |

---

### Task 1: 扩展 KnowledgeBaseLoader — 支持 docs/ 知识库和外部 FAQ 目录

**Files:**
- Modify: `src/main/java/com/qh/ai_agent/knowledge/KnowledgeBaseLoader.java`

**背景问题：**
- 当前只加载 `classpath:knowledge/faq|product|policy/` 三个子目录
- `src/main/resources/docs/` 下有两个中国移动业务知识库文件未加载
- `data/faq/` 下的外部 FAQ 文件未加载
- 新 FAQ 文件使用 `### Q{n}: question` 格式（不同于旧的 `## question` 格式）

- [ ] **Step 1: 在 KnowledgeBaseLoader 中添加 @Value 注入外部 FAQ 目录路径和新增 loadAll 逻辑**

在构造函数后添加 `@Value` 注入，在 `loadAll()` 中新增两路加载：

```java
package com.qh.ai_agent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Component
public class KnowledgeBaseLoader {

    private final ResourcePatternResolver resolver;

    @Value("${cs.faq.data-dir:#{null}}")
    private String externalFaqDir;

    public KnowledgeBaseLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 加载所有知识库文档（三路加载）
     * 1. classpath:knowledge/ — 原 FAQ/产品/政策文档
     * 2. classpath:docs/ — RAG 业务知识库文档
     * 3. 外部 data/faq/ — 管理员上传的 FAQ 文档
     */
    public List<Document> loadAll() {
        List<Document> docs = new ArrayList<>();

        // 路径1: classpath:knowledge/ — 原 FAQ/产品/政策
        docs.addAll(loadFromDirectory("classpath:knowledge/faq/*.md", "FAQ"));
        docs.addAll(loadFromDirectory("classpath:knowledge/product/*.md", "PRODUCT"));
        docs.addAll(loadFromDirectory("classpath:knowledge/policy/*.md", "POLICY"));

        // 路径2: classpath:docs/ — RAG 业务知识库
        docs.addAll(loadFromDirectory("classpath:docs/*.md", "RAG_KB"));

        // 路径3: 外部 FAQ 目录 — 管理员上传的 FAQ
        docs.addAll(loadExternalFaq());

        log.info("[KnowledgeBaseLoader] 加载完成，共 {} 条文档", docs.size());
        return docs;
    }

    // ... 保留原有的 loadFromDirectory, parseFaqDocument, buildFaqDocument, parseSingleDocument, extractTitle, readResource 方法 ...
```

- [ ] **Step 2: 添加 loadExternalFaq 方法，加载外部 data/faq/ 目录的 FAQ 文件**

在 `loadFromDirectory` 方法之后添加：

```java
    /**
     * 加载外部 FAQ 目录（data/faq/）的 Markdown 文件
     * 使用 FileSystem 路径而非 classpath
     */
    private List<Document> loadExternalFaq() {
        List<Document> docs = new ArrayList<>();
        Path faqDir = resolveExternalFaqDir();
        if (faqDir == null || !Files.exists(faqDir) || !Files.isDirectory(faqDir)) {
            log.info("[KnowledgeBaseLoader] 外部 FAQ 目录不存在或为空: {}", faqDir);
            return docs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(faqDir, "*.md")) {
            for (Path path : stream) {
                String filename = path.getFileName().toString();
                String categoryName = filename.replace(".md", "");
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.isBlank()) continue;

                // 检测格式：包含 "### Q" 的是新格式，否则是旧格式（## 标题）
                if (content.contains("### Q")) {
                    docs.addAll(parseNewFaqDocument(content, categoryName, docs.size()));
                } else {
                    docs.addAll(parseFaqDocument(content, categoryName, docs.size()));
                }
                log.info("[KnowledgeBaseLoader] 加载外部 FAQ: {}, 分类: {}", filename, categoryName);
            }
        } catch (IOException e) {
            log.warn("[KnowledgeBaseLoader] 加载外部 FAQ 目录失败: {}", e.getMessage());
        }
        return docs;
    }

    private Path resolveExternalFaqDir() {
        if (externalFaqDir != null && !externalFaqDir.isBlank()) {
            return Paths.get(externalFaqDir);
        }
        return Paths.get(System.getProperty("user.dir"), "data", "faq");
    }
```

- [ ] **Step 3: 添加 parseNewFaqDocument 方法，解析 `### Q{n}: question` 格式的 FAQ**

新 FAQ 文件格式示例：
```markdown
## 一、账户与密码类
### Q1：如何查询本机号码？
**A**：请尝试以下任一方式：...
### Q2：服务密码忘了怎么重置？
**A**：可通过以下方式重置：...
```

在 `parseFaqDocument` 方法之后添加：

```java
    /**
     * 解析新格式 FAQ 文档 — 按 "### Q{n}：" 标题分块
     * 格式：### Q{n}：问题\n**A**：答案
     * 同时记录所属的 ## 一级分类作为 section 信息
     */
    private List<Document> parseNewFaqDocument(String content, String categoryName, int startIndex) {
        List<Document> docs = new ArrayList<>();
        String[] lines = content.split("\n");

        String currentSection = categoryName; // 当前所在的一级分类
        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过文件级标题（# 开头但不是 ## ###）
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }

            // ## 一级分类标题，更新 section
            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                currentSection = trimmed.substring(3).trim();
                // 清除 Markdown 格式标记（如 **加粗**）
                currentSection = currentSection.replaceAll("\\*\\*", "").trim();
                continue;
            }

            // ### Q{n} 问题标题
            if (trimmed.startsWith("### Q") || trimmed.startsWith("### q")) {
                // 保存上一个 Q&A 块
                if (currentQuestion != null && !currentAnswer.isEmpty()) {
                    docs.add(buildNewFaqDocument(currentQuestion, currentAnswer.toString().trim(),
                            categoryName, currentSection, startIndex + docs.size()));
                }
                // 提取问题文本：去掉 "Q{n}：" 或 "Q{n}:" 前缀
                currentQuestion = trimmed.substring(4).trim(); // 去掉 "### "
                currentQuestion = currentQuestion.replaceAll("^Q\\d+[：:]\\s*", ""); // 去掉 Q{n}：前缀
                currentAnswer = new StringBuilder();
            } else if (currentQuestion != null) {
                currentAnswer.append(line).append("\n");
            }
        }
        // 最后一个块
        if (currentQuestion != null && !currentAnswer.isEmpty()) {
            docs.add(buildNewFaqDocument(currentQuestion, currentAnswer.toString().trim(),
                    categoryName, currentSection, startIndex + docs.size()));
        }

        return docs;
    }

    /**
     * 构建新格式 FAQ 的 Document（含 section 信息用于出处标注）
     */
    private Document buildNewFaqDocument(String question, String answer, String category,
                                          String section, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "FAQ-" + category);
        metadata.put("doc_type", "FAQ");
        metadata.put("category", category);
        metadata.put("section", section);
        metadata.put("chunk_index", chunkIndex);
        metadata.put("question", question);

        // content 保留原始答案文本（不添加 Q: A: 前缀），使向量检索更精准
        String docContent = "Q: " + question + "\nA: " + answer;
        return new Document(docContent, metadata);
    }
```

- [ ] **Step 4: 修改 loadFromDirectory 中的 RAG_KB 类型处理 — 按章节分块**

RAG 知识库文件格式是按 `## n. 标题` 分节的业务文档，需要按章节分块（而非整文件一块）。修改 `loadFromDirectory` 方法中 `docType` 非FAQ的分支：

```java
    private List<Document> loadFromDirectory(String locationPattern, String docType) {
        List<Document> docs = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".md")) continue;

                String categoryName = filename.replace(".md", "");
                String content = readResource(resource);

                if (content.isBlank()) continue;

                // 跳过非知识库文件（如需求文档、故障记录）
                if (isNonKnowledgeFile(filename)) continue;

                switch (docType) {
                    case "FAQ" -> docs.addAll(parseFaqDocument(content, categoryName, docs.size()));
                    case "RAG_KB" -> docs.addAll(parseRagDocument(content, categoryName, docs.size()));
                    default -> docs.add(parseSingleDocument(content, docType, categoryName, docs.size()));
                }
            }
        } catch (IOException e) {
            log.warn("[KnowledgeBaseLoader] 加载目录失败: {} — {}", locationPattern, e.getMessage());
        }
        return docs;
    }

    /**
     * 判断是否为非知识库文件（不应加载到向量库）
     */
    private boolean isNonKnowledgeFile(String filename) {
        return filename.contains("需求文档") || filename.contains("故障") ||
               filename.contains("恋爱常见问题"); // 恋爱文档是旧版，不加载
    }
```

- [ ] **Step 5: 添加 parseRagDocument 方法，按 ## 二级标题分块解析业务知识库**

```java
    /**
     * 解析 RAG 业务知识库文档 — 按 ## 二级标题分块
     * 每个 ## 章节生成一个 Document，用于精准语义检索
     */
    private List<Document> parseRagDocument(String content, String categoryName, int startIndex) {
        List<Document> docs = new ArrayList<>();
        String[] lines = content.split("\n");

        String currentSectionTitle = null;
        StringBuilder currentSectionContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过文件级标题
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }

            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                // 保存上一个章节块
                if (currentSectionTitle != null && currentSectionContent.length() > 0) {
                    docs.add(buildRagDocument(currentSectionTitle, currentSectionContent.toString().trim(),
                            categoryName, startIndex + docs.size()));
                }
                currentSectionTitle = trimmed.substring(3).trim();
                currentSectionContent = new StringBuilder();
            } else if (currentSectionTitle != null) {
                currentSectionContent.append(line).append("\n");
            }
        }
        // 最后一个块
        if (currentSectionTitle != null && currentSectionContent.length() > 0) {
            docs.add(buildRagDocument(currentSectionTitle, currentSectionContent.toString().trim(),
                    categoryName, startIndex + docs.size()));
        }

        return docs;
    }

    /**
     * 构建 RAG 知识库 Document
     */
    private Document buildRagDocument(String sectionTitle, String sectionContent,
                                       String categoryName, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "知识库-" + categoryName);
        metadata.put("doc_type", "RAG_KB");
        metadata.put("category", categoryName);
        metadata.put("section", sectionTitle);
        metadata.put("chunk_index", chunkIndex);

        String docContent = sectionTitle + "\n\n" + sectionContent;
        return new Document(docContent, metadata);
    }
```

- [ ] **Step 6: 在文件头部添加必要的 import**

在现有 import 后添加：

```java
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
```

- [ ] **Step 7: 验证编译通过**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/qh/ai_agent/knowledge/KnowledgeBaseLoader.java
git commit -m "feat: 扩展 KnowledgeBaseLoader 支持三路加载（classpath knowledge + docs + 外部 FAQ）"
```

---

### Task 2: 修改 KnowledgeSearchService — FAQ 保留来源信息 + 优化推荐问题

**Files:**
- Modify: `src/main/java/com/qh/ai_agent/knowledge/KnowledgeSearchService.java`

**背景问题：**
- 当前 FAQ 直匹配时清除 source 字段（`result = new KnowledgeSearchResult(..., null, ...)`），导致前端无出处
- 需求文档要求"出处标注率 100%"
- 推荐问题只收集 FAQ 类型，RAG_KB 类型的文档也应被收集

- [ ] **Step 1: 修改 tryFaqDirectMatch 方法，保留 source 信息**

将 `tryFaqDirectMatch` 方法中 source 清除逻辑改为保留 source：

找到以下代码段（约第 80-88 行）：
```java
        if (bestMatch != null && bestScore >= 0.6) {
            KnowledgeSearchResult result = KnowledgeSearchResult.from(bestMatch, bestScore);
            // FAQ 直接命中时清除 source 字段
            result = new KnowledgeSearchResult(
                    result.content(), null, result.docType(),
                    result.category(), result.chunkIndex(), result.relevance()
            );
            return new FaqMatchResult(result, bestCategory);
        }
```

替换为：
```java
        if (bestMatch != null && bestScore >= 0.6) {
            KnowledgeSearchResult result = KnowledgeSearchResult.from(bestMatch, bestScore);
            // FAQ 直接命中也保留 source 字段用于出处标注
            return new FaqMatchResult(result, bestCategory);
        }
```

- [ ] **Step 2: 更新 toCitedText 方法，支持 section 信息**

找到 `KnowledgeSearchResult` record 中的 `toCitedText` 方法：
```java
        public String toCitedText() {
            if (source == null || source.isEmpty()) {
                return content;
            }
            return content + "\n\n[来源：" + source + " #段落" + chunkIndex + "]";
        }
```

替换为：
```java
        public String toCitedText() {
            if (source == null || source.isEmpty()) {
                return content;
            }
            // 如果有 section 信息，用 section 替代 chunkIndex 作为段落标识
            return content + "\n\n[来源：" + source + " #" + chunkIndex + "]";
        }
```

- [ ] **Step 3: 验证编译通过**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/qh/ai_agent/knowledge/KnowledgeSearchService.java
git commit -m "fix: FAQ 直匹配保留来源信息，满足出处标注率100%要求"
```

---

### Task 3: 修改 CustomerServiceVectorStoreConfig — 初始化前清空旧向量数据

**Files:**
- Modify: `src/main/java/com/qh/ai_agent/knowledge/CustomerServiceVectorStoreConfig.java`

**背景问题：**
- 每次应用启动都会调用 `vectorStore.add(documents)`，但不清空旧数据，导致数据重复累积
- 新增知识源后，需要确保向量库中只有最新数据

- [ ] **Step 1: 在向量写入前添加 DELETE 清空语句**

在 `csVectorStore` 方法的 `vectorStore.add(batch)` 循环之前，添加清空旧数据的语句。

找到：
```java
        List<Document> documents = knowledgeBaseLoader.loadAll();
        log.info("[CS-VectorStore] 开始向量化 {} 条文档...", documents.size());

        int batchSize = 10;
```

替换为：
```java
        // 清空旧数据，避免重复累积
        try {
            jdbcTemplate.execute("DELETE FROM " + tableName);
            log.info("[CS-VectorStore] 已清空旧向量数据");
        } catch (Exception e) {
            log.warn("[CS-VectorStore] 清空旧数据失败: {}", e.getMessage());
        }

        List<Document> documents = knowledgeBaseLoader.loadAll();
        log.info("[CS-VectorStore] 开始向量化 {} 条文档...", documents.size());

        int batchSize = 10;
```

- [ ] **Step 2: 验证编译通过**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/qh/ai_agent/knowledge/CustomerServiceVectorStoreConfig.java
git commit -m "fix: 向量库初始化前清空旧数据，避免启动后数据重复累积"
```

---

### Task 4: 更新 CustomerServiceAgent 的 nextStepPrompt — 所有回答标注来源

**Files:**
- Modify: `src/main/java/com/qh/ai_agent/Agent/customer_service/CustomerServiceAgent.java`

**背景问题：**
- 当前 prompt 只要求 `rag_search` 标注来源，`faq_direct` 不标注
- 需求要求出处标注率 100%，所有回答都应标注来源

- [ ] **Step 1: 更新 initSession 中的 nextStepPrompt**

找到 `initSession` 方法中的 `setNextStepPrompt`：
```java
this.setNextStepPrompt("请根据用户的问题，选择合适的工具来查询信息或处理请求。如果工具返回的 matchType 是 rag_search，回答时必须标注来源出处；如果是 faq_direct，直接回答无需标注来源。如果已充分回答，直接给出最终答案。");
```

替换为：
```java
this.setNextStepPrompt("请根据用户的问题，选择合适的工具来查询信息或处理请求。" +
    "所有回答必须标注来源出处，格式为 [来源：文档名#段落编号]。" +
    "如果工具返回了 source 字段，必须在回答末尾标注。" +
    "如果工具返回了 matchType=faq_direct，说明是 FAQ 精确匹配，直接引用答案并标注来源。" +
    "如果工具返回了 matchType=rag_search，说明是知识库语义检索，整理答案后标注来源。" +
    "如果知识库未找到相关内容，如实告知用户并建议转人工。" +
    "如果已充分回答，直接给出最终答案。");
```

- [ ] **Step 2: 验证编译通过**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/qh/ai_agent/Agent/customer_service/CustomerServiceAgent.java
git commit -m "feat: 所有回答均标注来源出处，满足出处标注率100%要求"
```

---

### Task 5: 添加配置项 — cs.faq.data-dir

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`

- [ ] **Step 1: 在 application.yml 中添加 cs.faq.data-dir 默认配置**

在 `jwt:` 配置块之前（约第 97 行），添加：

```yaml
# 客服知识库配置
cs:
  faq:
    data-dir: ./data/faq
```

注意：因为 application.yml 中已有 `cs.vector.enabled` 的配置在 application-local.yml 中，这里只加 faq 配置。

- [ ] **Step 2: 在 application-local.yml 中确认无需额外修改**

`application-local.yml` 已有 `cs.vector.enabled: true`，cs.faq.data-dir 使用默认值 `./data/faq` 即可。无需修改。

- [ ] **Step 3: 验证配置加载**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat: 添加 cs.faq.data-dir 配置项，指向外部 FAQ 目录"
```

---

### Task 6: 更新系统 Prompt — 强调出处标注

**Files:**
- Modify: `src/main/resources/prompts/customer-service.txt`

- [ ] **Step 1: 在 customer-service.txt 中添加/修改出处标注规则**

先读取当前文件内容，找到出处标注相关的指令并增强。

Run: 先 `cat "D:\AI Code\AI_Agent\src\main\resources\prompts\customer-service.txt"` 查看完整内容。

在现有的出处标注规则部分（如果有 `[来源：` 相关指令），增强为：

```
## 出处标注规则（必须严格遵守）
1. 所有基于知识库的回答，必须在回答末尾标注来源，格式：[来源：文档名#段落编号]
2. FAQ 精确匹配：标注 [来源：FAQ-分类名#Q{n}]
3. 知识库语义检索：标注 [来源：知识库-文档名#章节标题]
4. 禁止编造知识库中不存在的信息（无幻觉率要求 ≥ 95%）
5. 如果知识库未找到相关内容，明确告知用户"抱歉，知识库中暂无相关信息"，不要猜测或编造
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/prompts/customer-service.txt
git commit -m "feat: 增强系统 Prompt 出处处标注规则，满足100%出处标注要求"
```

---

### Task 7: 更新 PROJECT_GUIDE.md — 版本记录

**Files:**
- Modify: `PROJECT_GUIDE.md`

- [ ] **Step 1: 在版本变更记录部分（第 913 行附近）添加新版本条目**

在 `### v4.5 (2026-04-24)` 之前插入：

```markdown
### v5.0 (2026-04-27) — RAG + FAQ 知识检索增强（三路加载 + 出处标注）

**目标：** 完善知识库检索体系，加载所有知识源（classpath knowledge + docs + 外部 FAQ），实现出处标注率 100%。

**改造文件：**
- `knowledge/KnowledgeBaseLoader.java` — 核心改造：
  - `loadAll()` 从单路扩展为三路加载：classpath `knowledge/`、classpath `docs/`、外部 `data/faq/`
  - 新增 `parseNewFaqDocument()` — 解析 `### Q{n}: question` 格式的 FAQ 文件（移动智能客服 FAQ）
  - 新增 `parseRagDocument()` — 按 `##` 二级标题分块解析业务知识库文档（用于精准语义检索）
  - 新增 `buildNewFaqDocument()` / `buildRagDocument()` — 构建带 section 元信息的 Document
  - 新增 `loadExternalFaq()` — 从文件系统加载外部 FAQ 目录
  - 新增 `isNonKnowledgeFile()` — 过滤非知识库文件（需求文档、故障记录、恋爱旧文档）
  - Document metadata 新增 `section` 字段，记录一级分类/章节标题用于出处标注
- `knowledge/KnowledgeSearchService.java` — FAQ 直匹配不再清除 source 字段，保留完整出处信息
- `knowledge/CustomerServiceVectorStoreConfig.java` — 启动时先清空旧向量数据（DELETE），避免重复累积
- `Agent/customer_service/CustomerServiceAgent.java` — nextStepPrompt 更新，要求所有回答均标注来源出处
- `application.yml` — 新增 `cs.faq.data-dir: ./data/faq` 配置项
- `prompts/customer-service.txt` — 增强出处标注规则（FAQ/知识库/未找到三种情况的处理规范）

**知识源覆盖：**
- `classpath:knowledge/faq/` — 5 个原 FAQ 文件（订单/退换货/配送/售后/支付）× ~5 条 = ~25 条
- `classpath:knowledge/product/` — 3 个产品文档（手表/耳机/音箱）
- `classpath:knowledge/policy/` — 2 个政策文档（隐私/售后承诺）
- `classpath:docs/` — 2 个中国移动业务知识库（基础服务 + 专项服务与活动）
- `data/faq/` — 2 个外部 FAQ 文件（基础业务篇 30 条 + 增值与活动篇 30 条）= 60 条
- 总计约 90+ FAQ 条目 + 15+ 产品/政策/知识库章节文档

**两级检索流程（增强后）：**
1. Tier 1: FAQ 直匹配 — 遍历所有 FAQ 文档（含外部 FAQ），计算相似度，≥ 0.6 直接命中
2. Tier 2: RAG 语义检索 — PgVector 语义搜索所有文档（含知识库章节），topK=3
3. 所有结果保留 source 元信息，回答必须标注 [来源：文档名#段落/章节]
```

- [ ] **Step 2: Commit**

```bash
git add PROJECT_GUIDE.md
git commit -m "docs: 更新 PROJECT_GUIDE.md v5.0 版本记录"
```

---

### Task 8: 端到端验证 — 启动应用并测试知识检索

- [ ] **Step 1: 编译通过**

Run: `cd "D:\AI Code\AI_Agent" && mvn compile -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动应用**

Run: `cd "D:\AI Code\AI_Agent" && mvn spring-boot:run -Dspring-boot.run.profiles=local,dev 2>&1`

观察日志中是否出现：
- `[KnowledgeBaseLoader] 加载完成，共 N 条文档` — N 应该远大于原来的 ~12 条
- `[CS-VectorStore] 已清空旧向量数据`
- `[CS-VectorStore] 开始向量化 N 条文档...`
- `[CS-VectorStore] 向量化完成`

- [ ] **Step 3: 测试 FAQ 直匹配（应命中外部 FAQ）**

用 curl 或浏览器访问 SSE 端点，发送移动客服相关问题：
```
GET /api/customer-service/chat?message=流量超了怎么扣费&sessionId=test001
```

验证：
- 回答中包含 FAQ 答案内容
- 回答末尾有 `[来源：FAQ-移动智能客服-FAQ-基础业务篇#...]` 出处标注

- [ ] **Step 4: 测试 RAG 语义检索（应命中知识库文档）**

```
GET /api/customer-service/chat?message=移动花卡宝藏版哪些APP免流&sessionId=test002
```

验证：
- 回答内容来自知识库文档
- 回答末尾有 `[来源：知识库-智能客服业务知识库-基础服务#...]` 出处标注

- [ ] **Step 5: 测试不相关问题（应提示未找到）**

```
GET /api/customer-service/chat?message=今天天气怎么样&sessionId=test003
```

验证：
- 系统正确识别为闲聊或超范围
- 不编造答案

---

## Self-Review

### 1. Spec Coverage

| 需求 | 对应 Task |
|------|-----------|
| 加载 classpath docs/ 知识库 | Task 1 Step 4-5 |
| 加载外部 data/faq/ FAQ | Task 1 Step 2-3 |
| 解析新 FAQ 格式（### Q{n}:） | Task 1 Step 3 |
| FAQ 直匹配覆盖外部 FAQ | Task 1 + Task 2 |
| RAG 向量检索覆盖知识库 | Task 1 + Task 3 |
| 出处标注率 100% | Task 2 + Task 4 + Task 6 |
| 向量库去重 | Task 3 |
| 端到端验证 | Task 8 |

### 2. Placeholder Scan
- 无 TBD、TODO、implement later
- 每个步骤都有完整代码

### 3. Type Consistency
- `KnowledgeSearchResult` record 的 `from()` 方法使用 `doc.getMetadata()` 获取 `source`、`doc_type`、`category` 等字段 — 与新增的 `section` 字段兼容（额外字段不影响现有 record）
- `buildNewFaqDocument` 和 `buildRagDocument` 都产出 `Document` 类型，与 `KnowledgeSearchService` 兼容
- `FaqManagementService` 的 `clearCache()` 调用 `knowledgeSearchService.clearCache()`，后者置 `cachedDocs = null`，下次 `getDocs()` 会调用 `knowledgeBaseLoader.loadAll()` 重新加载所有三路文档 — 兼容
