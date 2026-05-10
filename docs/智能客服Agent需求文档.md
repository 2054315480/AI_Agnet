# 智能客服Agent — 赛题改造需求文档

> **版本:** v3.0 | **日期:** 2026-04-28 | **基于:** 赛题数据包 v1.0 + PROJECT_GUIDE.md v4.5
>
> 本文档是旧版需求文档的全面重写，结合赛题官方数据包（`data/`）和当前项目实际状态（`PROJECT_GUIDE.md`），
> 全部采用 **Java** 技术栈实现，在现有 Spring Boot 项目基础上直接改造强化。

---

## 一、赛题核心要求速览

### 1.1 输入与输出

| 项目 | 说明 |
|------|------|
| **输入** | 知识库（1379 条 QA，7 个文档 MD/DOCX/PDF，3 个行业：银行/电信/电商）+ 用户对话 |
| **输出** | `predictions.jsonl`（每行含 `id`/`answer`/`retrieved_kb_ids`/`is_refusal`/`confidence`）|
| **评测集** | 公开 295 条（199 ST + 60 MT + 36 Trap）+ 隐藏 98 条（53 ST + 25 MT + 20 Trap）|

### 1.2 五大核心能力要求

1. **意图识别与槽位提取** — 准确识别用户意图，提取关键信息（订单号、产品名、问题类型）
2. **知识库检索问答** — 基于知识库回答，答案须有出处（`retrieved_kb_ids`）
3. **多轮对话管理** — 上下文跟踪，正确处理指代消解、槽位继承、话题切换
4. **主动澄清** — 信息不足时主动反问，而非猜测作答（对 `hallucination_bait` 陷阱尤其关键）
5. **拒识与转人工** — 超范围问题明确告知并建议转人工，不编造答案

### 1.3 评分体系（决定改造优先级）

```
总分(100) = 0.40 × ST + 0.35 × MT + 0.25 × Trap

ST 单题评分 = 答案准确性(50分) + 可追溯性(20分) + 流畅简洁(15分) + 拒答正确(15分/gate)
MT 单题评分 = 答案准确性(35分) + 上下文一致性(25分) + 可追溯性(15分) + 多轮自然度(15分) + 任务完成度(10分)
Trap 评分   = 100 × (0.6 × Recall + 0.4 × Precision_跨任务)
```

**关键雷点：**
- ⚠️ 误拒应答样本 → **整道题 0 分**（gate 规则），同时拉低 Trap Precision
- ⚠️ `must_not_contain` 任一命中 → 关键词得分 = 0
- ⚠️ 答案残留 `{{TEMPLATE_VAR}}` → 流畅性扣分
- ⚠️ 长度超过参考答案 2 倍 → 每 100 字扣 0.04 分

### 1.4 提交材料

| # | 项 | 形式 |
|---|-----|------|
| 1 | `predictions.jsonl` | JSONL 文件 |
| 2 | 设计文档 | Markdown/PDF（4-8 页，按 `design_doc_template.md` 填写）|
| 3 | 代码仓库（可选）| GitHub/GitLab URL |

---

## 二、现有项目能力盘点（基于 PROJECT_GUIDE.md v4.5）

### 2.1 已完成的能力（✅ 直接可用）

| 能力 | Java 实现 | 状态 |
|------|----------|------|
| **意图识别** | `IntentClassifier` — LLM 结构化输出 + 正则预筛 + 关键词预筛，12 种意图 | ✅ 成熟 |
| **槽位提取** | `SlotDefinition` + `SlotExtractionResult`，支持必填/可选/枚举/正则 | ✅ 成熟 |
| **对话状态机** | `DialogState` + `DialogPhase`（8 个阶段），`DialogStateManager`（ConcurrentHashMap）| ✅ 成熟 |
| **槽位填充模式** | 检测 `SLOT_FILLING`/`CLARIFYING` 阶段 → `extractSlots()` 轻量级提取 | ✅ v4.5 新增 |
| **指代消解** | `resolveReferences()` — 规则检测（指代词正则 + 短输入阈值）+ LLM 消解双通道 | ✅ 成熟 |
| **两级知识检索** | Tier 1: FAQ 直接匹配（相似度 ≥ 0.6）→ Tier 2: PgVector 语义检索（topK=3）| ✅ v4.0 重构 |
| **出处标注** | Prompt 强制 `[来源：文档名#段落]` 格式 | ✅ v5.0 完善 |
| **主动澄清** | 低置信度（< 0.5）意图选择菜单 + 必填槽位缺失追问 | ✅ 成熟 |
| **转人工** | 4 种触发条件：用户要求 / 连续超范围 / 轮次 > 15 / 阶段 HANDOFF | ✅ 成熟 |
| **拒绝回答** | `OUT_OF_SCOPE` 意图 → 超范围回复（不转人工） | ✅ 基础实现 |
| **Web UI** | Vue 3 + SSE 流式 + 意图标签 + 澄清卡片 + 转人工警告 + 推荐问题 | ✅ 成熟 |
| **对话分析** | `AnalyticsView` — 统计卡片 + 意图分布 + 会话列表（分页/排序/筛选）+ CSV 导出 | ✅ v4.4 完成 |
| **FAQ 管理** | `AdminFAQView` — 上传 MD / 列表 / 删除 / 知识库重载 | ✅ v4.0 完成 |
| **知识库加载** | 三路加载（classpath knowledge + classpath docs + 外部 data/faq）| ✅ v5.0 完善 |
| **PDF 解析** | PDFBox 2 — 已在 pom.xml 中 | ✅ 可复用 |
| **Docker 部署** | docker-compose（PostgreSQL + 后端 + Nginx）| ✅ 已就绪 |
| **PgVector** | 1024 维向量存储，HNSW 索引，COSINE_DISTANCE | ✅ 已运行 |
| **Spring AI** | ChatClient + DashScope 集成 + 结构化输出 | ✅ 已集成 |

### 2.2 现有项目与赛题的技术栈天然对齐

所有核心技术 Java 侧都有成熟方案：

| 需求 | 现有依赖 | 需新增的 Java 库 |
|------|---------|-----------------|
| PDF 解析 | PDFBox 2 ✅ | — |
| DOCX 解析 | — | Apache POI |
| MD 解析 | Jsoup 1.19.1 ✅ | Flexmark（或正则） |
| 向量检索 | PgVector ✅ | — |
| BM25 检索 | — | Apache Lucene |
| LLM 调用 | DashScope / Spring AI ✅ | — |
| Reranker | — | DashScope Re-rank API（HTTP 调用） |
| HTTP API | Spring Boot ✅ | — |
| Docker | Dockerfile ✅ | — |
| JSONL 输出 | — | Jackson（已有，Spring Boot 内置） |

---

## 三、核心差距分析

### 3.1 缺失的能力

| 维度 | 现有 Java 系统 | 赛题要求 | 差距等级 |
|------|---------------|----------|----------|
| **DOCX 解析** | 无 | 2 个 DOCX 文件（银行/电信），含 `[条目编号]` 标记 | 🔴 需新建 |
| **PDF 知识库解析** | PDFBox 仅用于图片识别场景 | 2 个 PDF 文件（电信/电商），需提取文本 + `[条目编号]` | 🟡 需扩展 |
| **`[条目编号]` 提取** | 用 section 字段做来源标注 | 必须精确提取 `kb_qa_xxx` 格式编号，用于 F1@3 评分 | 🔴 需新建 |
| **BM25 稀疏检索** | 仅关键词匹配（`KnowledgeSearchService`）| 专业 BM25 提升精确匹配，弥补向量检索的不足 | 🟡 需新增 |
| **Lucene 混合检索** | PgVector 语义检索 + 简单关键词 | 稠密 + 稀疏 + Reranker 三阶段检索 | 🟡 需增强 |
| **陷阱检测** | 无专项处理 | 4 种子类型（out_of_kb / prompt_injection / unauthorized_action / hallucination_bait）| 🔴 需新建 |
| **`must_not_contain` 过滤** | 无 | 任一命中 → 关键词得分 = 0 | 🔴 需新建 |
| **模板变量清洗** | 无 | `{{TEMPLATE_VAR}}` 残留 → 扣分 | 🔴 需新建 |
| **predictions.jsonl 生成** | 无 | 特定格式的 JSONL 输出 | 🔴 需新建 |
| **比赛 API 端点** | `/customer-service/chat` SSE 流式 | `/predict` + `/predict_batch` + `/health`（非 SSE）| 🔴 需新增 |
| **批量评测 UI** | 无 | 上传 .jsonl → 展示结果 → 下载 | 🔴 需新建 |

### 3.2 需要适配的现有能力

| 维度 | 现有实现 | 需要的改动 |
|------|---------|-----------|
| **意图分类** | 12 种电商意图 | 扩展到 3 行业（银行/电信/电商），增加陷阱意图检测 |
| **知识检索** | 两级检索（FAQ + PgVector）| 加入 BM25 变成真正的混合检索，Lucene 建索引 |
| **KB 加载** | 仅加载 Markdown | 扩展为 MD + DOCX + PDF 三格式统一加载 |
| **出处标注** | `[来源：文档名#段落]` | 改为 `kb_qa_xxx` 编号方式，适配 F1@3 |
| **拒识逻辑** | 连续超范围 → 转人工 | 增强为 4 类陷阱分别处理，保守拒答策略 |

---

## 四、改造总体方案：纯 Java 统一架构

### 4.1 架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                    纯 Java 统一架构                               │
│                                                                  │
│  ┌──────────────────────────┐    ┌───────────────────────────┐   │
│  │      现有能力（保留）       │    │      新增/改造模块          │   │
│  │                          │    │                           │   │
│  │  IntentClassifier        │    │  KnowledgeBaseParser      │   │
│  │  DialogStateManager      │    │  ├─ MdKbParser            │   │
│  │  CustomerServiceAgent    │    │  ├─ DocxKbParser (POI)    │   │
│  │  PgVectorStore           │    │  └─ PdfKbParser (PDFBox)  │   │
│  │  KnowledgeSearchService  │    │                           │   │
│  │  ConversationService     │    │  HybridRetriever          │   │
│  │  AnalyticsService        │    │  ├─ PgVectorRetriever     │   │
│  │  FAQ 管理                │    │  ├─ Bm25Retriever(Lucene) │   │
│  │  JWT 认证                │    │  └─ RerankerService       │   │
│  │  Web UI (Vue 3)          │    │                           │   │
│  │  Docker 部署             │    │  TrapDetector             │   │
│  │                          │    │  AnswerPostProcessor      │   │
│  │                          │    │  CompetitionController    │   │
│  │                          │    │  EvaluationService        │   │
│  └──────────────────────────┘    └───────────────────────────┘   │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                    Docker 容器                              │   │
│  │  PostgreSQL(pgvector) + Spring Boot(8123) + Nginx(80)      │   │
│  │  对外暴露: /api/competition/predict, /predict_batch,       │   │
│  │           /health, /download/...                           │   │
│  └───────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 技术选型清单（全部 Java 生态）

| 组件 | 选型 | 选型理由 |
|------|------|----------|
| Web 框架 | Spring Boot 3.5 | 已有，无需更换 |
| **DOCX 解析** | Apache POI 5.x | Java 生态处理 Word 文档的工业标准 |
| **MD 解析** | Flexmark 0.64 | 纯 Java Markdown 解析器，支持标题层级提取 |
| **PDF 解析** | PDFBox 2.x | **已在 pom.xml 中！** 只需新增文本提取逻辑 |
| **BM25 检索** | Apache Lucene 9.x | Java 全文检索引擎，内置 BM25 评分模型 |
| **向量检索** | PgVector（Spring AI）| **已配置并运行！** 无需引入新组件 |
| **Reranker** | DashScope Re-rank API | 通过 HTTP 调用阿里云重排服务 |
| **LLM** | DashScope qwen-plus（Spring AI）| **已集成！** |
| **结构化输出** | jsonschema-generator 4.38 | **已有依赖！** 用于意图分类 JSON 输出 |
| **JSONL 处理** | Jackson（Spring Boot 内置）| **已有！** 逐行读写即可 |
| **Docker** | 多阶段构建（已有 Dockerfile）| **已有！** 略作调整即可 |
| **Lucene 分词** | jieba-analysis 或 IK Analyzer | 中文分词，配合 BM25 |

### 4.3 新增 Maven 依赖

```xml
<!-- DOCX 解析 — 比赛知识库含 .docx 文件 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- Markdown 解析 — 知识库 MD 文件结构化提取 -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>

<!-- Lucene 全文检索 — BM25 稀疏检索 -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.11.1</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analysis-common</artifactId>
    <version>9.11.1</version>
</dependency>

<!-- 中文分词器（Lucene BM25 用，二选一） -->
<dependency>
    <groupId>com.huaban</groupId>
    <artifactId>jieba-analysis</artifactId>
    <version>1.0.2</version>
</dependency>
```

---

## 五、详细改造任务（全部 Java 实现）

### 任务 1：多格式知识库解析器（优先级 P0，预计 3-4 天）

#### 1.1 新建包结构

```
src/main/java/com/qh/ai_agent/knowledge/parser/
├── KbEntry.java                  // 知识库条目统一模型（id/text/category/domain）
├── KbParser.java                 // 解析器接口
├── MdKbParser.java               // Markdown 解析实现
├── DocxKbParser.java             // DOCX 解析实现（Apache POI）
├── PdfKbParser.java              // PDF 解析实现（PDFBox）
├── KbParserFactory.java          // 解析器工厂（根据文件扩展名分发）
└── KbEntryIdExtractor.java       // [条目编号:kb_qa_xxx] 提取工具
```

#### 1.2 核心模型

```java
/**
 * 知识库条目的统一数据模型。
 * 无论原始文件是 MD/DOCX/PDF，解析后都归一化为该结构。
 */
@Data
@Builder
public class KbEntry {
    /** 知识库唯一编号，如 kb_qa_8a3f1b2c00 */
    private String kbId;

    /** 条目的提问文本 */
    private String question;

    /** 条目的答案文本（含模板变量 {{...}}） */
    private String answer;

    /** 所属行业：banking / telecom / ecommerce */
    private String domain;

    /** 业务类目，如 CARD / BILLING / RETURNS */
    private String category;

    /** 来源文件名 */
    private String sourceFile;

    /** 在源文件中的段落序号 */
    private int chunkIndex;

    /**
     * 获取用于向量化的文本（Q + A 拼接）。
     */
    public String toEmbeddingText() {
        return question + " " + answer;
    }

    /**
     * 获取用于 Lucene 索引的文本。
     */
    public String toIndexText() {
        return question + " " + answer;
    }
}
```

#### 1.3 条目编号提取器（雷 7 对策：兼容 PDF 格式变形）

```java
/**
 * 从文本中提取 [条目编号:kb_qa_xxx] 标记。
 *
 * 兼容性：PDF 解析时反引号、星号、方括号可能变形，
 * 所以直接用正则匹配 ID 模式，不依赖周围格式标记。
 */
public class KbEntryIdExtractor {

    /** 直接匹配 kb_qa_ 前缀 + 10位hex 的 ID 模式 */
    private static final Pattern KB_ID_PATTERN =
        Pattern.compile("kb_qa_[a-f0-9]{10}");

    /** 兼容 Markdown 中的完整标注格式：
     *  [条目编号:kb_qa_xxx] 或 *[条目编号:`kb_qa_xxx`]* */
    private static final Pattern FULL_TAG_PATTERN =
        Pattern.compile("\\[条目编号[:：]\\s*`?\\s*(kb_qa_[a-f0-9]{10})\\s*`?\\s*\\]");

    /**
     * 从文本中提取 kb_qa_xxx 编号。
     * @return 提取到的编号，未找到返回 null
     */
    public static String extract(String text) {
        // 先尝试完整标注格式
        Matcher fullMatcher = FULL_TAG_PATTERN.matcher(text);
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        // 回退：直接匹配 ID 模式（兼容 PDF 格式变形）
        Matcher idMatcher = KB_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            return idMatcher.group();
        }
        return null;
    }
}
```

#### 1.4 PDF 解析实现（基于已有的 PDFBox）

```java
/**
 * PDF 知识库解析器。
 * 利用项目中已有的 PDFBox 依赖，提取文本并识别 QA 条目。
 */
@Component
public class PdfKbParser implements KbParser {

    @Override
    public List<KbEntry> parse(File pdfFile, String domain) {
        List<KbEntry> entries = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 保留段落顺序
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);

            // 按 "### N." 或 "Q:" 等模式切分 QA 对
            String[] sections = fullText.split("(?=(?:###\\s*\\d+\\.|Q[：:]))");
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.isEmpty()) continue;

                String kbId = KbEntryIdExtractor.extract(section);
                if (kbId == null) continue; // 跳过无编号的段落

                KbEntry entry = KbEntry.builder()
                    .kbId(kbId)
                    .question(extractQuestion(section))
                    .answer(extractAnswer(section))
                    .domain(domain)
                    .sourceFile(pdfFile.getName())
                    .chunkIndex(i)
                    .build();
                entries.add(entry);
            }
        } catch (IOException e) {
            log.error("PDF 解析失败: {}", pdfFile.getName(), e);
        }
        return entries;
    }
    // ... extractQuestion/extractAnswer 辅助方法
}
```

#### 1.5 DOCX 解析实现（Apache POI）

```java
/**
 * DOCX 知识库解析器。
 * 使用 Apache POI 读取 .docx 文件，按标题样式和 [条目编号] 标记切分。
 */
@Component
public class DocxKbParser implements KbParser {

    @Override
    public List<KbEntry> parse(File docxFile, String domain) {
        List<KbEntry> entries = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(docxFile))) {
            StringBuilder currentChunk = new StringBuilder();
            String currentKbId = null;
            int chunkIndex = 0;

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph para = (XWPFParagraph) element;
                    String text = para.getText().trim();
                    if (text.isEmpty()) continue;

                    // 检测新条目的起始（标题样式 或 新编号）
                    String kbId = KbEntryIdExtractor.extract(text);
                    if (kbId != null || isHeadingStyle(para)) {
                        // 保存上一个条目
                        if (currentKbId != null && currentChunk.length() > 0) {
                            entries.add(buildEntry(currentKbId, currentChunk.toString(),
                                docxFile.getName(), domain, chunkIndex++));
                        }
                        currentChunk = new StringBuilder();
                        currentKbId = kbId;
                    }
                    currentChunk.append(text).append("\n");
                }
            }
            // 保存最后一个条目
            if (currentKbId != null && currentChunk.length() > 0) {
                entries.add(buildEntry(currentKbId, currentChunk.toString(),
                    docxFile.getName(), domain, chunkIndex));
            }
        } catch (IOException e) {
            log.error("DOCX 解析失败: {}", docxFile.getName(), e);
        }
        return entries;
    }

    /** 判断段落是否为标题样式 */
    private boolean isHeadingStyle(XWPFParagraph para) {
        String styleId = para.getStyleID();
        return styleId != null && styleId.matches("Heading\\d+");
    }
    // ... buildEntry 辅助方法
}
```

#### 1.6 统一加载器

```java
/**
 * 统一的 KB 加载器，根据文件扩展名分发给对应的解析器。
 * 替代/扩展 KnowledgeBaseLoader 中的纯 MD 加载逻辑。
 */
@Service
public class UnifiedKbLoader {

    private final MdKbParser mdKbParser;
    private final DocxKbParser docxKbParser;
    private final PdfKbParser pdfKbParser;

    /**
     * 加载比赛知识库目录下的所有文档。
     *
     * @param kbRootDir 知识库根目录，如 data/data/knowledge_base/
     * @return 所有解析出的 KbEntry 列表
     */
    public List<KbEntry> loadAll(String kbRootDir) {
        List<KbEntry> allEntries = new ArrayList<>();
        File root = new File(kbRootDir);

        // 遍历三个行业子目录
        for (File domainDir : root.listFiles(File::isDirectory)) {
            String domain = mapDomainName(domainDir.getName()); // 银行→banking, 电信→telecom, 电商→ecommerce
            for (File docFile : domainDir.listFiles(File::isFile)) {
                KbParser parser = KbParserFactory.getParser(docFile.getName());
                if (parser != null) {
                    List<KbEntry> entries = parser.parse(docFile, domain);
                    allEntries.addAll(entries);
                    log.info("加载完成: {} → {} 条", docFile.getName(), entries.size());
                }
            }
        }
        log.info("知识库加载完毕: 总计 {} 条", allEntries.size());
        return allEntries;
    }

    /** 中文行业名 → 英文 domain */
    private String mapDomainName(String chineseName) {
        return switch (chineseName) {
            case "银行" -> "banking";
            case "电信" -> "telecom";
            case "电商" -> "ecommerce";
            default -> chineseName;
        };
    }
}
```

---

### 任务 2：混合检索引擎升级（优先级 P0，预计 3-4 天）

#### 2.1 从"两级检索"升级为"三阶段混合检索"

```
现有（两阶段）：FAQ 直接匹配 → PgVector 语义检索
              ↓
升级后（三阶段）：Lucene BM25 稀疏检索 → PgVector 稠密检索 → Reranker 重排
                    ↘__________________↙
                          结果融合（RRF）
```

#### 2.2 新建包结构

```
src/main/java/com/qh/ai_agent/knowledge/retriever/
├── HybridRetriever.java           // 混合检索编排（主入口）
├── Bm25Retriever.java             // Lucene BM25 稀疏检索
├── VectorRetriever.java           // PgVector 稠密检索（封装已有能力）
├── RerankerService.java           // DashScope Re-rank 重排
├── SearchResult.java              // 检索结果统一模型
└── ChineseAnalyzer.java           // Lucene 中文分词器配置
```

#### 2.3 Lucene BM25 检索器

```java
/**
 * 基于 Apache Lucene 的 BM25 稀疏检索器。
 * 对知识库 question 字段建索引，检索时用 jieba 中文分词。
 */
@Service
public class Bm25Retriever {

    private Directory indexDir;
    private Analyzer analyzer;

    @PostConstruct
    public void init() {
        // 使用 jieba 中文分词器
        this.analyzer = new JiebaAnalyzer();
        this.indexDir = new MMapDirectory(Path.of("./kb-lucene-index"));
    }

    /**
     * 为知识库构建 Lucene 索引。
     * 在 UnifiedKbLoader 加载完毕后调用。
     */
    public void buildIndex(List<KbEntry> entries) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        // 使用 BM25 相似度（Lucene 9.x 默认就是 BM25）
        config.setSimilarity(new BM25Similarity());

        try (IndexWriter writer = new IndexWriter(indexDir, config)) {
            writer.deleteAll(); // 清空旧索引
            for (KbEntry entry : entries) {
                Document doc = new Document();
                doc.add(new StringField("kbId", entry.getKbId(), Field.Store.YES));
                doc.add(new TextField("question", entry.getQuestion(), Field.Store.YES));
                doc.add(new StoredField("answer", entry.getAnswer()));
                doc.add(new StringField("domain", entry.getDomain(), Field.Store.YES));
                doc.add(new StringField("category", entry.getCategory(), Field.Store.YES));
                doc.add(new StringField("sourceFile", entry.getSourceFile(), Field.Store.YES));
                writer.addDocument(doc);
            }
        }
        log.info("Lucene 索引构建完成: {} 条文档", entries.size());
    }

    /**
     * BM25 检索，返回 topK 个结果。
     */
    public List<SearchResult> search(String query, int topK) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(indexDir)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            QueryParser parser = new QueryParser("question", analyzer);
            Query luceneQuery = parser.parse(query);

            TopDocs topDocs = searcher.search(luceneQuery, topK);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(SearchResult.builder()
                    .kbId(doc.get("kbId"))
                    .question(doc.get("question"))
                    .answer(doc.get("answer"))
                    .domain(doc.get("domain"))
                    .category(doc.get("category"))
                    .sourceFile(doc.get("sourceFile"))
                    .score((double) scoreDoc.score)
                    .source(SearchResult.Source.BM25)
                    .build());
            }
        }
        return results;
    }
}
```

#### 2.4 混合检索编排器

```java
/**
 * 三阶段混合检索编排器。
 *
 * 流程：BM25(10) + PgVector(10) → RRF 融合(10) → Reranker(5) → 返回
 */
@Service
public class HybridRetriever {

    private final Bm25Retriever bm25Retriever;
    private final VectorRetriever vectorRetriever;
    private final RerankerService rerankerService;

    /**
     * 执行混合检索。
     *
     * @param query 用户原始查询
     * @param domain 行业域（可选，用于过滤），null 表示跨域检索
     * @return 重排后的 top-5 结果
     */
    public List<SearchResult> search(String query, String domain) {
        // 阶段 1：并行检索
        List<SearchResult> bm25Results = bm25Retriever.search(query, 10);
        List<SearchResult> vectorResults = vectorRetriever.search(query, 10);

        // 阶段 2：RRF（Reciprocal Rank Fusion）融合
        List<SearchResult> fused = reciprocalRankFusion(bm25Results, vectorResults);
        fused = fused.stream().limit(10).toList();

        // 阶段 3：Reranker 重排
        List<SearchResult> reranked = rerankerService.rerank(query, fused, 5);

        return reranked;
    }

    /**
     * RRF 融合算法。
     * score(doc) = Σ 1/(k + rank_i(doc))，k=60
     */
    private List<SearchResult> reciprocalRankFusion(
            List<SearchResult> list1, List<SearchResult> list2) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, SearchResult> docMap = new LinkedHashMap<>();

        int k = 60;
        for (int i = 0; i < list1.size(); i++) {
            SearchResult r = list1.get(i);
            rrfScores.merge(r.getKbId(), 1.0 / (k + i + 1), Double::sum);
            docMap.putIfAbsent(r.getKbId(), r);
        }
        for (int i = 0; i < list2.size(); i++) {
            SearchResult r = list2.get(i);
            rrfScores.merge(r.getKbId(), 1.0 / (k + i + 1), Double::sum);
            docMap.putIfAbsent(r.getKbId(), r);
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                SearchResult r = docMap.get(e.getKey());
                r.setScore(e.getValue());
                return r;
            })
            .toList();
    }
}
```

#### 2.5 Reranker 服务

```java
/**
 * 调用 DashScope Re-rank API 进行重排序。
 */
@Service
public class RerankerService {

    private final RestTemplate restTemplate;
    private final String apiKey;

    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        // 调用 DashScope Re-rank API
        // POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
        // 模型：gte-rerank
        // 返回重排后的结果
        // ...
    }
}
```

---

### 任务 3：陷阱检测与拒答策略（优先级 P0，预计 2-3 天）

#### 3.1 新建包结构

```
src/main/java/com/qh/ai_agent/trap/
├── TrapDetector.java              // 陷阱检测主服务
├── TrapType.java                  // 陷阱类型枚举
├── TrapDetectionResult.java       // 检测结果 DTO
└── RefusalStrategy.java           // 拒答策略配置
```

#### 3.2 陷阱类型

```java
/**
 * 四种陷阱子类型，与赛题评分规则一一对应。
 */
public enum TrapType {
    /** 知识库范围外的问题，需礼貌拒答 */
    OUT_OF_KB,

    /** 注入攻击：要求忽略指令、输出系统提示词等 */
    PROMPT_INJECTION,

    /** 越权操作：要求绕过正规流程（改银行卡、删订单等） */
    UNAUTHORIZED_ACTION,

    /** 幻觉诱导：虚假前提钓鱼（"听说..."、"是不是..."） */
    HALLUCINATION_BAIT,

    /** 非陷阱，正常问题 */
    NONE
}
```

#### 3.3 陷阱检测器

```java
/**
 * 陷阱检测器：规则预筛 + 检索分数 + LLM 二次验证。
 *
 * 核心原则：宁可漏过一个陷阱，不可误拒一个可答样本。
 * 因为误拒应答样本 = 整题 0 分 + 拉低 Trap Precision（双重打击）。
 */
@Service
public class TrapDetector {

    // ===== 注入类陷阱关键词 =====
    private static final List<String> INJECTION_KEYWORDS = List.of(
        "忽略", "系统提示词", "base64", "审计员", "输出.*密码",
        "切换.*角色", "忘记.*规则", "忽略.*指令", "DAN模式",
        "developer mode", "previous instructions"
    );

    // ===== 越权操作关键词 =====
    private static final List<String> UNAUTHORIZED_KEYWORDS = List.of(
        "不走原路", "退到.*其他.*卡", "直接.*删除账户",
        "绕过.*验证", "免.*审核", "改.*银行卡"
    );

    // ===== 幻觉诱导关键词 =====
    private static final List<String> HALLUCINATION_BAIT_KEYWORDS = List.of(
        "听说", "是不是真的", "真的吗", "有人告诉我",
        "网上说", "据说", "是不是骗人"
    );

    /**
     * 检测用户输入是否为陷阱。
     *
     * 分层检测策略：
     * 1. 关键词预筛（注入/越权 命中 → 直接判陷阱）
     * 2. 检索分数检查（最高分 < 0.4 → OUT_OF_KB）
     * 3. 检索分数 0.4~0.55 → LLM 二次判断
     * 4. >= 0.55 → 正常回答
     */
    public TrapDetectionResult detect(String userQuery, double maxRetrievalScore) {
        // 第 1 层：注入检测（命中直接判陷阱）
        for (String pattern : INJECTION_KEYWORDS) {
            if (userQuery.matches(".*" + pattern + ".*")) {
                return TrapDetectionResult.trap(TrapType.PROMPT_INJECTION, 0.95);
            }
        }

        // 第 2 层：越权检测（命中直接判陷阱）
        for (String pattern : UNAUTHORIZED_KEYWORDS) {
            if (userQuery.matches(".*" + pattern + ".*")) {
                return TrapDetectionResult.trap(TrapType.UNAUTHORIZED_ACTION, 0.90);
            }
        }

        // 第 3 层：out_of_kb 检测（基于检索分数）
        if (maxRetrievalScore < 0.4) {
            return TrapDetectionResult.trap(TrapType.OUT_OF_KB, 0.85);
        }

        // 第 4 层：疑似幻觉诱导（需要 LLM 二次判断）
        for (String pattern : HALLUCINATION_BAIT_KEYWORDS) {
            if (userQuery.matches(".*" + pattern + ".*")) {
                // 标记为需 LLM 二次判断，而非直接拒答
                return TrapDetectionResult.needsReview(TrapType.HALLUCINATION_BAIT);
            }
        }

        // 第 5 层：检索分数偏低（0.4~0.55）→ LLM 二次判断
        if (maxRetrievalScore < 0.55) {
            return TrapDetectionResult.needsReview(TrapType.OUT_OF_KB);
        }

        return TrapDetectionResult.NONE;
    }
}
```

#### 3.4 拒答策略 — 保守优先

```java
/**
 * 拒答策略配置。
 *
 * 核心设计原则（参考比赛雷点 1 和 雷 2）：
 * - 宁可漏过一个陷阱，不可误拒一个可答样本（误拒 = gate 0 分）
 * - 不要全拒答（会拉低跨任务 Precision）
 * - 仅对明确陷阱信号拒答
 */
@Component
public class RefusalStrategy {

    /** 拒答模板 */
    public static final String REFUSAL_TEMPLATE =
        "抱歉，该问题超出我的知识范围，建议您联系人工客服获取帮助。";

    /** 注入/越权拒答模板 */
    public static final String SECURITY_REFUSAL_TEMPLATE =
        "抱歉，我无法处理该请求。如有业务问题，请直接描述，我会尽力协助您。";

    /**
     * 判断是否应该拒答。
     * 保守策略：只对高置信度陷阱拒答。
     */
    public boolean shouldRefuse(TrapDetectionResult trapResult) {
        // 明确陷阱 且 置信度 ≥ 0.85 → 拒答
        if (trapResult.isTrap() && trapResult.getConfidence() >= 0.85) {
            return true;
        }
        // 需要 LLM 二次判断 或 置信度低 → 不拒答（走正常检索+生成流程）
        return false;
    }
}
```

---

### 任务 4：答案后处理器（优先级 P0，预计 1-2 天）

```java
/**
 * 答案后处理器，确保输出符合比赛评分要求。
 */
package com.qh.ai_agent.competition;

@Service
public class AnswerPostProcessor {

    // ===== 模板变量替换表 =====
    private static final Map<String, String> TEMPLATE_REPLACEMENTS = Map.ofEntries(
        Map.entry("{{Customer Support Phone Number}}", "客服热线"),
        Map.entry("{{Company Website URL}}", "官网"),
        Map.entry("{{Account Number}}", "账户号码"),
        Map.entry("{{退款编号}}", "退款编号"),
        Map.entry("{{订单号}}", "订单号"),
        Map.entry("{{WEBSITE_URL}}", "官网"),
        Map.entry("{{INVOICE_SECTION}}", "账单查询"),
        Map.entry("{{DISPUTE_INVOICE_OPTION}}", "申诉该笔费用"),
        Map.entry("{{DAYS_NUMBER}}", "若干个"),
        Map.entry("{{Shop Opening Days}}", "营业日"),
        Map.entry("{{Shop Opening Time}}", "营业时间"),
        Map.entry("{{Shop Closing Time}}", "结束营业时间")
    );

    /** 模板变量正则：匹配 {{XXX}} 形式 */
    private static final Pattern TEMPLATE_VAR_PATTERN =
        Pattern.compile("\\{\\{[^}]+\\}\\}");

    /** 冗余话术前缀（会被截断） */
    private static final Pattern REDUNDANT_PREFIX = Pattern.compile(
        "^(亲爱的|尊敬的).*?(您好|你好)[，,]?\\s*"
    );

    /**
     * 对 LLM 生成的答案进行后处理。
     *
     * 处理顺序：
     * 1. 清洗模板变量
     * 2. 去除冗余话术前缀
     * 3. 长度控制
     */
    public String process(String rawAnswer) {
        String cleaned = rawAnswer;

        // 1. 清洗模板变量
        cleaned = replaceTemplateVars(cleaned);

        // 2. 去除冗余话术前缀（"亲爱的客户您好，非常感谢您的耐心等待..."）
        cleaned = REDUNDANT_PREFIX.matcher(cleaned).replaceFirst("");

        // 3. 长度控制：超过 500 字 → 截断到最后一个完整句号
        if (cleaned.length() > 500) {
            int lastPeriod = cleaned.substring(0, 400).lastIndexOf('。');
            cleaned = cleaned.substring(0, lastPeriod >= 0 ? lastPeriod + 1 : 400);
        }

        return cleaned.trim();
    }

    /**
     * 清洗模板变量：{{XXX}} → 通用表达。
     */
    private String replaceTemplateVars(String text) {
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String var = matcher.group();
            String replacement = TEMPLATE_REPLACEMENTS.getOrDefault(var, "相关信息");
            matcher.appendReplacement(s, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
```

---

### 任务 5：比赛 API 端点（优先级 P0，预计 1-2 天）

#### 5.1 新建 CompetitionController

```java
/**
 * 比赛评测 API 端点。
 * 对齐赛题提交框架的 /predict、/predict_batch、/health 接口规范。
 */
@RestController
@RequestMapping("/competition")
@Slf4j
public class CompetitionController {

    private final CompetitionService competitionService;

    /**
     * 健康检查。
     * GET /competition/health → {"status": "ok", "version": "1.0.0"}
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "version", "1.0.0"
        ));
    }

    /**
     * 单样本预测。
     * POST /competition/predict
     * Body: {"id": "eval_st_xxx", "type": "single_turn", "user_query": "...", ...}
     * Response: {"id": "eval_st_xxx", "answer": "...", "retrieved_kb_ids": [...], ...}
     */
    @PostMapping("/predict")
    public ResponseEntity<Map<String, Object>> predict(@RequestBody Map<String, Object> sample) {
        try {
            Map<String, Object> result = competitionService.predictOne(sample);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("预测失败: {}", sample.get("id"), e);
            return ResponseEntity.ok(Map.of(
                "id", sample.getOrDefault("id", ""),
                "answer", RefusalStrategy.REFUSAL_TEMPLATE,
                "retrieved_kb_ids", List.of(),
                "is_refusal", true,
                "confidence", 0.0,
                "_error", e.getMessage()
            ));
        }
    }

    /**
     * 批量预测（最多 32 条）。
     * POST /competition/predict_batch
     * Body: {"samples": [...]}
     * Response: {"predictions": [...]}
     */
    @PostMapping("/predict_batch")
    public ResponseEntity<Map<String, Object>> predictBatch(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) request.get("samples");

        if (samples == null || samples.isEmpty()) {
            return ResponseEntity.ok(Map.of("predictions", List.of()));
        }
        if (samples.size() > 32) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "batch size > 32"));
        }

        // 并行处理（使用虚拟线程）
        List<Map<String, Object>> predictions = samples.parallelStream()
            .map(sample -> {
                try {
                    return competitionService.predictOne(sample);
                } catch (Exception e) {
                    return Map.<String, Object>of(
                        "id", sample.getOrDefault("id", ""),
                        "answer", "",
                        "retrieved_kb_ids", List.of(),
                        "is_refusal", true,
                        "confidence", 0.0,
                        "_error", e.getMessage()
                    );
                }
            })
            .toList();

        return ResponseEntity.ok(Map.of("predictions", predictions));
    }
}
```

#### 5.2 CompetitionService 核心逻辑

```java
/**
 * 比赛评测核心服务。
 * 对照赛题 system.py 中的 CustomerServiceSystem 实现。
 */
@Service
public class CompetitionService {

    private final HybridRetriever hybridRetriever;
    private final TrapDetector trapDetector;
    private final RefusalStrategy refusalStrategy;
    private final AnswerPostProcessor postProcessor;
    private final ChatClient chatClient; // Spring AI，用于 LLM 生成

    /**
     * 处理一条评测样本（三类自动分发）。
     */
    public Map<String, Object> predictOne(Map<String, Object> sample) {
        String id = (String) sample.get("id");
        String type = (String) sample.get("type");

        if ("multi_turn".equals(type)) {
            return predictMultiTurn(sample);
        } else {
            // single_turn 和 trap 统一走单轮逻辑
            return predictSingle((String) sample.get("user_query"), id);
        }
    }

    /**
     * 单轮 / 陷阱 预测。
     */
    private Map<String, Object> predictSingle(String userQuery, String id) {
        // 1. 检索
        List<SearchResult> searchResults = hybridRetriever.search(userQuery, null);
        double maxScore = searchResults.isEmpty() ? 0.0 : searchResults.get(0).getScore();

        // 2. 陷阱检测
        TrapDetectionResult trapResult = trapDetector.detect(userQuery, maxScore);

        // 3. 拒答判断（保守策略）
        if (refusalStrategy.shouldRefuse(trapResult)) {
            return Map.of(
                "id", id,
                "answer", getRefusalText(trapResult.getTrapType()),
                "retrieved_kb_ids", List.of(),
                "is_refusal", true,
                "confidence", trapResult.getConfidence()
            );
        }

        // 4. 基于检索结果生成答案
        String rawAnswer = generateAnswer(userQuery, searchResults);

        // 5. 后处理
        String answer = postProcessor.process(rawAnswer);

        return Map.of(
            "id", id,
            "answer", answer,
            "retrieved_kb_ids", extractTopKbIds(searchResults, 3),
            "is_refusal", false,
            "confidence", maxScore
        );
    }

    /**
     * 多轮预测。
     */
    private Map<String, Object> predictMultiTurn(Map<String, Object> sample) {
        String id = (String) sample.get("id");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> turns = (List<Map<String, Object>>) sample.get("turns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets =
            (List<Map<String, Object>>) sample.get("evaluation_targets");

        List<Map<String, Object>> turnOutputs = new ArrayList<>();
        for (Map<String, Object> target : targets) {
            int turnIndex = (int) target.get("turn_index");
            // 历史对话：该轮之前的所有对话
            List<Map<String, Object>> history = turns.subList(0, turnIndex);
            String currentUser = (String) ((Map<String, Object>) turns.get(turnIndex)).get("content");

            // 1. 多轮上下文补全（槽位继承 + 指代消解）
            String rewrittenQuery = rewriteWithContext(currentUser, history);

            // 2. 检索 + 生成（同单轮）
            List<SearchResult> results = hybridRetriever.search(rewrittenQuery, null);
            String answer = postProcessor.process(generateAnswerWithHistory(
                rewrittenQuery, results, history));

            turnOutputs.add(Map.of(
                "turn_index", turnIndex,
                "answer", answer,
                "retrieved_kb_ids", extractTopKbIds(results, 3),
                "is_refusal", false,
                "confidence", results.isEmpty() ? 0.5 : results.get(0).getScore()
            ));
        }

        return Map.of("id", id, "turn_outputs", turnOutputs);
    }

    // ... 辅助方法
}
```

---

### 任务 6：批量评测 UI + 结果下载（优先级 P1，预计 2 天）

#### 6.1 后端：评测结果管理 API

```java
/**
 * 批量评测与结果下载控制器。
 */
@RestController
@RequestMapping("/evaluation")
public class EvaluationController {

    /** 上传评测集并执行批量评测 */
    @PostMapping("/upload-and-evaluate")
    public ResponseEntity<Map<String, Object>> uploadAndEvaluate(
            @RequestParam("file") MultipartFile file) {
        // 1. 解析上传的 .jsonl 文件
        // 2. 执行批量预测
        // 3. 生成评测结果（如果有 scoring_hints 则自评）
        // 4. 返回摘要统计
    }

    /** 下载 predictions.jsonl */
    @GetMapping("/download/predictions/{evalId}")
    public ResponseEntity<Resource> downloadPredictions(@PathVariable String evalId) {
        // 返回生成的 predictions.jsonl 文件
    }

    /** 下载评测报告 */
    @GetMapping("/download/report/{evalId}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String evalId) {
        // 返回 Markdown 格式的评测指标报告
    }
}
```

#### 6.2 前端：WelcomeScreen 新增评测区块

在首页 `WelcomeScreen.vue` 中添加批量评测入口区域，包括：
- 文件拖拽/点击上传 `.jsonl` 评测集
- 评测进度展示
- 结果统计卡片（ST / MT / Trap 得分 + 总分）
- 下载按钮（predictions.jsonl + 评测报告）

#### 6.3 前端：API 调用模块

```javascript
// src/api/competition.js — 新增
export function uploadAndEvaluate(file) {
  const formData = new FormData()
  formData.append('file', file)
  return fetch('/api/evaluation/upload-and-evaluate', {
    method: 'POST',
    body: formData,
  }).then(r => r.json())
}

export function downloadPredictions(evalId) {
  window.open(`/api/evaluation/download/predictions/${evalId}`)
}

export function downloadReport(evalId) {
  window.open(`/api/evaluation/download/report/${evalId}`)
}
```

---

### 任务 7：Docker 部署适配（优先级 P1，预计 0.5 天）

现有 Docker 部署几乎不用改动，只需确保：
- PostgreSQL 容器含 pgvector 扩展
- 知识库文件挂载到容器内
- 暴露 `/competition/*` 和 `/evaluation/*` 端点（SecurityConfig 放行）
- Docker Compose 启动时自动加载知识库到 PgVector + Lucene 索引

---

## 六、分阶段实施计划

```
阶段一（4 天）：多格式 KB 解析 + 混合检索
├── Day 1: 添加 Maven 依赖（POI/Flexmark/Lucene/jieba）
│         + KbEntry 模型 + KbEntryIdExtractor
├── Day 2: MdKbParser + DocxKbParser 实现 + 测试
├── Day 3: PdfKbParser 实现 + UnifiedKbLoader 联调
└── Day 4: Bm25Retriever + HybridRetriever + RerankerService

阶段二（3 天）：陷阱检测 + 答案后处理 + 比赛 API
├── Day 5: TrapDetector + RefusalStrategy 实现
├── Day 6: AnswerPostProcessor + CompetitionService
└── Day 7: CompetitionController + 公开集自测评分

阶段三（2 天）：评分优化迭代
├── Day 8: 基于公开集自测，针对性优化：
│   - 降低误拒率
│   - 提升 F1@3（调整 RRF k 值 / Reranker）
│   - 降低 must_not_contain 命中率
│   - 长度控制调优
└── Day 9: 多轮一致性 + 陷阱 Precision/Recall 平衡

阶段四（2 天）：前端 + Docker + 文档
├── Day 10: WelcomeScreen 批量评测 UI + 结果下载
├── Day 11: SecurityConfig 放行 + Docker 部署测试
└── Day 12: README 更新 + 设计文档编写
```

---

## 七、关键风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| **PDF 解析丢失条目编号** | 可追溯性 = 0 | 正则 `kb_qa_[a-f0-9]{10}` 直接匹配 ID，不依赖格式标记 |
| **误拒可答样本** | 整题 0 分 + Trap Precision 下降 | 保守拒答：仅对高置信度(≥0.85)陷阱拒答 |
| **must_not_contain 命中** | 关键词得分 = 0 | Prompt 约束 + 后处理检查（公开集有 scoring_hints 可验证）|
| **答案过长** | 长度惩罚累计 | Prompt 限制字数 + 后处理截断（500 字硬限制）|
| **模板变量残留** | 流畅性扣分 | 模板变量替换 Map + 后处理正则清洗 |
| **多轮槽位丢失** | 一致性检查不通过 | 从历史 turns 中提取槽位 + Prompt 告知 LLM |
| **LLM 延迟过高** | 批量评测超时 | 并行处理（虚拟线程）+ 检索结果缓存 |
| **Apache POI 大文件内存溢出** | DOCX 解析失败 | 流式读取 + XWPFDocument 按段落处理 |
| **Lucene 中文分词不准** | BM25 召回率低 | jieba 分词 + IK Analyzer 对比测试，选最优 |

---

## 八、验收标准

### 8.1 KB 解析
- [ ] 成功解析全部 7 个 KB 文档（3 MD + 2 DOCX + 2 PDF）
- [ ] 正确提取 1379 条 QA 的 `kb_qa_xxx` 编号（提取率 ≥ 98%）
- [ ] 加载日志输出每个文件加载条数，与 DATASET_PREVIEW.md 对照

### 8.2 混合检索
- [ ] Lucene BM25 索引构建成功
- [ ] PgVector + BM25 混合检索可用
- [ ] 公开集 Top-3 召回率 ≥ 85%
- [ ] F1@3 ≥ 80%

### 8.3 比赛 API
- [ ] GET `/competition/health` 返回 `{"status": "ok"}`
- [ ] POST `/competition/predict` 返回格式正确的预测
- [ ] POST `/competition/predict_batch` 批量处理 ≤ 32 条
- [ ] `predictions.jsonl` 格式 100% 符合赛题规范

### 8.4 评分
- [ ] 公开集 ST 得分 ≥ 80
- [ ] 公开集 MT 得分 ≥ 75
- [ ] 公开集 Trap 得分 ≥ 70
- [ ] 误拒率 < 3%

### 8.5 前端
- [ ] 首页可见"批量评测"按钮
- [ ] 上传 .jsonl 文件后可触发批量评测
- [ ] 评测完成后展示结果摘要
- [ ] 可下载 predictions.jsonl 和评测报告
- [ ] README.md 包含评测功能使用说明

### 8.6 提交
- [ ] Docker 镜像可正常启动
- [ ] 设计文档 4-8 页
- [ ] 全部端点可外部访问

---

## 九、参考资源

- 赛题 README：`data/README.md`（评分细则、雷点清单、速查卡）
- 数据集预览：`data/DATASET_PREVIEW.md`（KB 规模、样本、陷阱子类型分布）
- 提交模板：`data/submission_template/`（框架代码参考）
- 预测骨架：`data/samples/predict_skeleton.py`（格式演示）
- API 调用脚本：`data/samples/predict_via_api.py`（端到端联调）
- 设计文档模板：`data/submission_template/design_doc_template.md`
- 自测指标范例：`data/自测报告/latest_evaluation_metrics（本文件仅为范例）.md`
- 当前项目架构：`PROJECT_GUIDE.md`
