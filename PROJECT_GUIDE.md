# AI Agent 项目全景指南

> **版本:** v4.0 | **最后更新:** 2026-04-23 | **作者:** 秋鹤
>
> 本文档供 AI Agent（Claude Code）快速理解项目全貌。每次修改项目结构、新增模块或变更架构后，请同步更新本文档。

---

## 一、项目概览

一个基于 Spring Boot 3.5 + Vue 3 的**智能客服问答与多轮对话 Agent 平台**。核心功能：

- **智能客服 Agent** — 意图识别、槽位提取、知识库检索、主动澄清、拒识转人工
- **多轮对话管理** — 上下文跟踪、对话状态机、指代消解、话题切换
- **知识库检索问答** — 两级检索（FAQ 直接匹配 + RAG PgVector 语义检索）、出处标注、推荐相关问题
- **对话分析** — 意图分布统计、转人工率、对话日志导出
- **FAQ 管理** — 管理员上传 Markdown 导入 FAQ、知识库重载
- **用户系统** — JWT 认证、对话历史持久化、多设备同步

> **v2.0 改造说明：** 原有"恋爱大师"和"超级智能体(HeManus)"已被替换为智能客服模式。底层 Agent 框架（BaseAgent→ReActAgent→ToolCallAgent）完整保留，业务层全部改为客服场景。

---

## 二、技术栈

### 后端（Java）

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.10 | 基础框架 |
| Java | 21 | 运行时 |
| Spring AI | 1.1.2 | AI 抽象层（BOM 统一版本管理） |
| Spring AI Alibaba | 1.1.2.0 | LLM 集成（DashScope/Qwen） |
| DashScope SDK | 2.19.1 | 阿里云大模型 SDK |
| LangChain4j | 1.0.0-beta3 | AI 框架（备用，非主用） |
| MyBatis-Plus | 3.5.9 | ORM |
| PostgreSQL + pgvector | 17 | 关系数据库 + 向量存储 |
| Spring Security + jjwt | 0.12.6 | 认证鉴权 |
| Redis | — | 聊天记忆（可选） |
| iText 9 + PDFBox 2 | — | PDF 生成/解析 |
| Alibaba Cloud OSS | 3.18.1 | 文件存储 |
| Kryo | 5.6.2 | 文件聊天记忆高性能序列化 |
| jsonschema-generator | 4.38.0 | 结构化输出支持 |
| Lombok | 1.18.38 | 注解式代码简化 |
| Hutool | 5.8.38 | 工具库 |
| Jsoup | 1.19.1 | HTML 解析 |
| Knife4j | 4.4.0 | API 文档 |

### 前端（Vue）

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | ^3.4.0 | UI 框架（Composition API / `<script setup>`） |
| Vue Router | ^4.3.0 | 路由 |
| Vite | ^5.4.0 | 构建工具 |
| marked | ^12.0.0 | Markdown 渲染 |

**注意：** 无第三方 UI 组件库、无 Tailwind。全部使用手写 CSS + CSS 自定义属性设计系统。

### 基础设施

- **Docker** 多阶段构建 + docker-compose（PostgreSQL + 后端 + Nginx）
- **Nginx** 反向代理（SSE 流式支持：`proxy_buffering off`，300s 超时）

---

## 三、项目目录结构

```
D:\AI_Agent\
├── image-search-mcp-server/          # 独立 MCP 微服务（Pexels 图片搜索）
│   ├── src/                          # Spring Boot 3.5.13 应用
│   └── pom.xml                       # spring-ai-starter-mcp-server 依赖
│
├── chat-memory/                      # 聊天记忆文件存储目录（Kryo 序列化）
│
├── AI_Agent_Fronted/                 # 前端 Vue 项目
│   ├── src/
│   │   ├── main.js                   # 入口：创建 Vue app、注册路由、初始化主题
│   │   ├── App.vue                   # 根组件：侧边栏 + 主区域 + 移动端响应
│   │   ├── api/
│   │   │   ├── auth.js               # 认证模块（单例 ref：user/token/isAuthenticated）
│   │   │   ├── chat.js               # 客服 SSE API（chatCustomerServiceStream）
│   │   │   └── admin.js              # 管理 API（FAQ 上传/列表/删除/重载）
│   │   ├── utils/
│   │   │   └── sse.js                # SSE 流解析（fetchSSE / fetchMultipartSSE）
│   │   ├── composables/
│   │   │   ├── useConversations.js   # 对话管理（单例，agentType=customer_service）
│   │   │   ├── useChat.js            # 客服消息发送（处理 intent/clarification/handoff/suggested_questions 事件）
│   │   │   └── useTheme.js           # 主题切换（dark/light，localStorage 持久化）
│   │   ├── components/
│   │   │   ├── LoginView.vue         # 登录/注册页（左右分栏布局）
│   │   │   ├── Sidebar.vue           # 侧边栏（智能客服模式 + FAQ 管理入口）
│   │   │   ├── ChatView.vue          # 聊天主区域（传递 intentInfo/isHandoff/suggestedQuestions 等 props）
│   │   │   ├── ChatMessage.vue       # 消息气泡（意图标签、澄清提示、转人工警告、推荐问题）
│   │   │   ├── ChatInput.vue         # 输入框（自动高度、图片上传、快捷发送）
│   │   │   ├── WelcomeScreen.vue     # 欢迎页（智能客服问候 + 快捷问题）
│   │   │   ├── ThemeToggle.vue       # 主题切换按钮
│   │   │   └── AdminFAQView.vue      # FAQ 管理页面（上传/列表/删除/重载）
│   │   ├── router/
│   │   │   └── index.js              # 路由：/login（guest）、/（需 auth）、/admin/faq（需 auth）
│   │   └── assets/styles/
│   │       └── global.css            # 全局 CSS 设计系统（70+ 变量，暗色/亮色主题）
│   ├── Dockerfile                    # 前端构建 + Nginx 部署
│   ├── vite.config.js                # 代理 /api → localhost:8123
│   └── package.json
│
├── src/main/java/com/qh/ai_agent/   # 后端 Java 源码
│   ├── AiAgentApplication.java       # Spring Boot 启动类
│   │
│   ├── controller/                   # REST 控制器
│   │   ├── AiController.java         # /ai/** — 原有对话端点（保留但非主入口）
│   │   ├── CustomerServiceController.java  # /customer-service/** — 智能客服主入口（SSE）
│   │   ├── AdminController.java      # /admin/** — FAQ 管理（上传/列表/删除/重载）
│   │   ├── AuthController.java       # /auth/** — 注册、登录、用户信息
│   │   ├── ConversationController.java  # /conversations/** — 对话 CRUD
│   │   └── ImageChatController.java  # /image-chat/** — 图片识别
│   │
│   ├── agent/                        # Agent 框架（核心）
│   │   ├── BaseAgent.java            # 抽象基类（run/stream、SSE、状态管理）
│   │   ├── ReActAgent.java           # ReAct 模式（think → act 循环）
│   │   ├── ToolCallAgent.java        # 工具调用（ToolCallingManager、日志增强）
│   │   ├── customer_service/         # 智能客服 Agent
│   │   │   ├── CustomerServiceAgent.java  # 客服 Agent（意图+槽位+澄清+转人工+推荐问题推送）
│   │   │   └── CustomerServiceTools.java  # 客服工具集（6 个 @Tool，searchKnowledgeBase 支持两级检索）
│   │   ├── HeManus.java              # 超级智能体（@Component 已注释，保留代码备用）
│   │   └── Model/
│   │       └── AgentState.java       # 状态枚举：IDLE / RUNNING / FINISHED / ERROR
│   │
│   ├── intent/                       # 意图识别体系（v2.0 新增）
│   │   ├── IntentClassifier.java     # 意图分类器（LLM 结构化输出 + 正则预筛双通道）
│   │   ├── IntentConfig.java         # 意图-槽位配置（12 种意图、槽位定义、澄清模板）
│   │   └── model/
│   │       ├── CustomerIntent.java   # 意图枚举（12 种：订单/产品/投诉/转人工等）
│   │       ├── IntentResult.java     # 意图识别结果 DTO（intent + confidence + slots）
│   │       └── SlotDefinition.java   # 槽位定义（名称/类型/是否必填/枚举值/正则）
│   │
│   ├── dialog/                       # 对话状态管理（v2.0 新增）
│   │   ├── DialogStateManager.java   # 对话状态管理器（ConcurrentHashMap 缓存）
│   │   └── model/
│   │       ├── DialogState.java      # 对话状态（意图/槽位/阶段/轮次/历史）
│   │       └── DialogPhase.java      # 对话阶段枚举（8 个：GREETING→SLOT_FILLING→HANDOFF 等）
│   │
│   ├── tools/                        # AI 工具（原有 10 个 + 注册配置）
│   │   ├── ToolRegistration.java     # @Configuration，注册 ToolCallback[] bean
│   │   ├── FileOperationTool.java    # 文件读写
│   │   ├── WebSearchTool.java        # Bocha AI 网络搜索
│   │   ├── WebScrapingTool.java      # Jsoup 网页抓取
│   │   ├── ResourceDownloadTool.java # 资源下载
│   │   ├── TerminalOperationTool.java # 终端命令
│   │   ├── TerminateTool.java        # 终止信号
│   │   ├── PDFGenerationTool.java    # PDF 生成
│   │   ├── DateTimeTool.java         # 日期时间
│   │   ├── DatabaseQueryTool.java    # 数据库查询
│   │   └── PdfParseTool.java         # PDF 解析
│   │
│   ├── knowledge/                    # 知识库管理（v2.1 新增）
│   │   ├── KnowledgeBaseLoader.java  # 知识库加载器（从 Markdown 文件加载）
│   │   ├── KnowledgeSearchService.java # 两级检索服务（FAQ 直接匹配 + PgVector 语义检索）
│   │   └── CustomerServiceVectorStoreConfig.java # 客服专用 PgVector 配置（cs_vector_store）
│   │
│   ├── rag/                          # RAG 检索增强生成（完整保留）
│   │   ├── LoveAppDocumentLoder.java # 文档加载（Markdown → 向量化）
│   │   ├── LoveAppVectorStoreConfig.java  # 内存向量库
│   │   ├── PgvectorVectorStoreConfig.java # PgVector 向量库
│   │   ├── LoveAppRagCloudAdvisorConfig.java  # 云 RAG
│   │   ├── LoveAppRagCustomAdvisorFactory.java # 过滤器工厂
│   │   ├── LoveAppContexttualQueryAugmenterFactory.java # 空上下文降级
│   │   ├── QueryReweiter.java        # LLM 查询重写
│   │   ├── MykeywordEnricher.java    # AI 关键词提取
│   │   ├── MyTokenTextSplitter.java  # 文档分块
│   │   ├── enricher/
│   │   │   └── DocumentMetadataEnricher.java  # 元信息丰富
│   │   ├── reader/
│   │   │   ├── GitHubDocumentReader.java  # GitHub 文档读取
│   │   │   └── GitHubApiService.java      # GitHub REST API
│   │   ├── transformer/
│   │   │   └── DictionaryQueryTransformer.java  # 字典查询转换
│   │   ├── retriever/
│   │   │   ├── KeywordSearchRetriever.java    # 关键词检索
│   │   │   └── HybridDocumentRetriever.java   # 混合检索
│   │   ├── config/
│   │   │   └── EnhancedRagConfig.java  # 增强版 RAG 配置
│   │   └── model/
│   │       └── RagRequest.java         # RAG 请求 DTO
│   │
│   ├── advisor/                      # Advisor 链（按优先级排序）
│   │   ├── PermissionAdvisor.java     # -100 用户权限校验
│   │   ├── SensitiveInfoAdvisor.java  # -75  敏感信息脱敏
│   │   ├── BannedWordAdvisor.java     # -50  违禁词过滤
│   │   ├── ReReadingAdvisor.java      # 0    RE2 查询复用
│   │   └── My_loggerAdvisor.java      # 99   请求/响应日志
│   │
│   ├── chatmemory/                   # 聊天记忆（3 种后端）
│   │   ├── FileConstant.java         # 常量：文件保存路径
│   │   ├── FileBasedChatMemory.java  # 文件（Kryo 序列化，默认）
│   │   ├── MySqlChatMemory.java      # PostgreSQL（MyBatis-Plus）
│   │   └── RedisChatMemory.java      # Redis（JSON + TTL）
│   │
│   ├── config/                       # 配置类
│   │   ├── SecurityConfig.java       # Spring Security（JWT + CORS + /customer-service/** 放行）
│   │   ├── JwtUtil.java              # JWT 工具
│   │   ├── JwtAuthenticationFilter.java  # JWT 过滤器
│   │   ├── PasswordConfig.java       # BCryptPasswordEncoder
│   │   ├── CorsConfig.java           # CORS 全开
│   │   ├── HttpClientConfig.java     # HTTP 代理配置
│   │   ├── BochaSearchConfig.java    # Bocha 搜索配置
│   │   ├── ImageChatConfig.java      # 图片对话配置
│   │   ├── ChatMemoryConfig.java     # 聊天记忆条件装配
│   │   ├── RedisConfig.java          # Redis 序列化
│   │   ├── OssConfig.java            # OSS 配置
│   │   └── SensitiveInfoConfig.java  # 脱敏开关
│   │
│   ├── entity/                       # 数据实体
│   │   ├── User.java                 # users 表
│   │   ├── Conversation.java         # conversations 表
│   │   ├── ConversationMessage.java  # conversation_messages 表
│   │   ├── ChatMemoryEntity.java     # chat_memory 表
│   │   └── bocha/                    # Bocha 搜索 API 请求/响应
│   │
│   ├── mapper/                       # MyBatis-Plus Mapper
│   │   ├── UserMapper.java
│   │   ├── ConversationMapper.java
│   │   ├── ConversationMessageMapper.java
│   │   └── ChatMemoryMapper.java
│   │
│   ├── service/                      # 业务服务
│   │   ├── UserService.java          # 注册/登录/查询
│   │   ├── ConversationService.java  # 对话 CRUD + 消息管理
│   │   ├── FaqManagementService.java # FAQ 文件管理 + 知识库重载
│   │   ├── OssService.java           # OSS 上传
│   │   ├── BannedWordService.java    # 违禁词检查
│   │   ├── PromptTemplateService.java # Prompt 模板加载/渲染
│   │   └── SensitiveInfoService.java # 敏感信息检测/脱敏
│   │
│   ├── model/                        # DTO
│   │   ├── ImageChatRequest.java
│   │   ├── ImageChatResponse.java
│   │   └── PromptTemplate.java
│   │
│   ├── exception/                    # 自定义异常
│   │   ├── PermissionDeniedException.java
│   │   ├── BannedWordException.java
│   │   └── PromptTemplateException.java
│   │
│   └── demo/                         # 示例代码（不影响运行）
│       └── invoke/                   # SDK/HTTP/SpringAI/LangChain 调用示例
│
├── src/main/resources/
│   ├── application.yml               # 主配置（端口 8123、context-path /api）
│   ├── application-local.yml         # 本地开发（PostgreSQL、DashScope key、MCP）
│   ├── application-dev.yml           # 免费模型（MiniMax-M2.1）
│   ├── application-prod.yml          # 生产环境
│   ├── MCP-servers.json              # MCP 服务器配置（高德地图、图片搜索）
│   ├── dictionaries/
│   │   └── synonyms.json             # 同义词（42 条）+ 停用词（17 个）
│   ├── prompts/                      # Prompt 模板
│   │   ├── customer-service.txt      # 智能客服系统 Prompt（v2.0 主用）
│   │   ├── love-advisor.txt          # 恋爱大师系统 Prompt（保留备用）
│   │   ├── he-manus.txt              # 超级智能体系统 Prompt（保留备用）
│   │   ├── image-analyst.txt         # 图片分析 Prompt
│   │   └── general-assistant.txt     # 通用助手 Prompt
│   ├── knowledge/                    # 客服知识库文档（v2.1 外部化）
│   │   ├── faq/                      # FAQ 文档（按 ## 标题分块）
│   │   │   ├── 订单相关.md            # 订单查询/取消/发货/地址修改（5 条）
│   │   │   ├── 退换货政策.md          # 退货/换货/运费/退款时效（5 条）
│   │   │   ├── 配送物流.md            # 运费/配送方式/物流追踪/丢失损坏（5 条）
│   │   │   ├── 售后保修.md            # 保修政策/维修/过保/延保（5 条）
│   │   │   └── 支付相关.md            # 支付方式/退款/发票/分期（5 条）
│   │   ├── product/                  # 产品文档（每文件一个产品）
│   │   │   ├── 智能手表Pro.md
│   │   │   ├── 无线蓝牙耳机Air.md
│   │   │   └── 智能音箱Home.md
│   │   └── policy/                   # 政策文档（每文件一个政策）
│   │       ├── 隐私保护政策.md
│   │       └── 售后服务承诺.md
│   └── docs/                         # RAG 知识库文档（原有恋爱大师文档）
│       ├── 智能客服Agent需求文档.md     # v2.0 赛题需求与实现规划
│       ├── 恋爱常见问题及回答-单身篇.md
│       ├── 恋爱常见问题及回答-恋爱篇.md
│       └── 恋爱常见问题及回答-结婚篇.md
│
├── docs/
│   └── 智能客服Agent需求文档.md         # 赛题需求文档（评分标准、功能拆解、实现规划）
│
├── pom.xml                           # Maven 依赖
├── Dockerfile                        # 多阶段构建
├── docker-compose.yml                # PostgreSQL + 后端 + Nginx
├── nginx.conf                        # 反向代理（SSE 支持）
└── .env.example                      # 环境变量模板
```

---

## 四、数据库设计

### PostgreSQL 表结构

```sql
-- 用户表
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname    VARCHAR(100),
    avatar      VARCHAR(500),
    role        VARCHAR(20) DEFAULT 'user',  -- v4.0: user / admin
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- 对话表
CREATE TABLE conversations (
    id          VARCHAR(64) PRIMARY KEY,    -- 前端生成
    user_id     BIGINT REFERENCES users(id),
    title       VARCHAR(200),
    agent_type  VARCHAR(30) NOT NULL DEFAULT 'customer_service',  -- customer_service / love / manus
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- 对话消息表（v3.0 扩展）
CREATE TABLE conversation_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,   -- user / assistant
    content         TEXT,
    image_url       VARCHAR(500),
    intent          VARCHAR(50),            -- v3.0: 识别到的意图
    slots           JSONB,                  -- v3.0: 提取的槽位
    confidence      FLOAT,                  -- v3.0: 意图置信度
    is_handoff      BOOLEAN DEFAULT FALSE,  -- v3.0: 是否触发转人工
    is_clarification BOOLEAN DEFAULT FALSE, -- v3.0: 是否触发澄清
    sources         JSONB,                  -- v3.0: 知识库出处
    created_at      TIMESTAMP
);

-- 会话分析聚合表（v3.0 新增）
CREATE TABLE dialog_analytics (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     VARCHAR(64) REFERENCES conversations(id) ON DELETE CASCADE,
    total_turns         INT DEFAULT 0,
    intent_distribution JSONB,
    handoff_triggered   BOOLEAN DEFAULT FALSE,
    clarification_count INT DEFAULT 0,
    avg_confidence      FLOAT,
    resolved            BOOLEAN,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 聊天记忆表（可选，MySqlChatMemory 使用）
CREATE TABLE chat_memory (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64),
    role            VARCHAR(20),
    content         TEXT,
    message_type    VARCHAR(20),
    created_at      TIMESTAMP
);

-- 向量存储表（由 PgVector 自动管理）
-- vector_store: id, content, metadata, embedding

-- 客服知识库向量表（v4.0 新增）
CREATE TABLE cs_vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);
-- 索引: cs_vector_store_embedding_idx (HNSW, cosine)
```

**索引：**
- `idx_conversations_user_id ON conversations(user_id)`
- `idx_conversation_messages_conv_id ON conversation_messages(conversation_id)`
- `idx_messages_intent ON conversation_messages(intent)` — v3.0
- `idx_analytics_conversation ON dialog_analytics(conversation_id)` — v3.0

---

## 五、API 端点一览

> 所有端点前缀为 `/api`（由 `server.servlet.context-path` 配置）

### 智能客服（CustomerServiceController — `/customer-service/**`）— 主入口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/customer-service/chat` | 客服对话 SSE 流式 | 无需 |
| GET | `/customer-service/session/{sessionId}/state` | 获取对话状态（调试） | 无需 |

**SSE 事件类型：**

| 事件类型 | 说明 |
|----------|------|
| `session_id` | 会话 ID（首轮返回） |
| `intent` | 识别到的意图（JSON：intent + displayName + confidence） |
| `clarification` | 澄清问题（槽位缺失时） |
| `handoff` | 转人工提示（超出范围时） |
| `tool_call` | 工具调用开始 |
| `tool_result` | 工具执行结果 |
| `answer` | 最终回答 |
| `suggested_questions` | 推荐相关问题（JSON 数组） |
| `done` | 对话结束 |

### AI 对话（AiController — `/ai/**`）— 保留备用

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/ai/love_app/chat/sse` | 恋爱大师 SSE 流式 | 无需 |
| GET | `/ai/manus/chat` | 超级智能体 SSE | 无需 |
| POST | `/ai/rag/enhanced` | 增强版 RAG | 无需 |
| POST | `/ai/tools/parse-pdf` | PDF 解析 | 无需 |

### 认证（AuthController — `/auth/**`）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/auth/register` | 注册 | 无需 |
| POST | `/auth/login` | 登录 | 无需 |
| GET | `/auth/me` | 当前用户信息 | JWT |

### 对话（ConversationController — `/conversations/**`）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/conversations` | 用户对话列表 | JWT |
| POST | `/conversations` | 创建对话 | JWT |
| GET | `/conversations/{id}` | 对话详情 + 消息 | JWT |
| PUT | `/conversations/{id}` | 更新标题 | JWT |
| DELETE | `/conversations/{id}` | 删除对话 | JWT |
| POST | `/conversations/{id}/messages` | 添加消息 | JWT |

### 图片对话（ImageChatController — `/image-chat/**`）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/image-chat/explain` | 图片解释 | 无需 |
| POST | `/image-chat/upload-explain` | 上传图片解释 | 无需 |

### 对话分析（AnalyticsController — `/analytics/**`）— v3.0 新增

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/analytics/overview` | 总览统计卡片（会话数/意图分布/转人工率/平均轮次） | 无需 |
| GET | `/analytics/intent-distribution` | 意图分布统计 | 无需 |
| GET | `/analytics/sessions` | 会话列表（含分析摘要） | 无需 |
| GET | `/analytics/sessions/{id}` | 单会话详情（完整对话回放） | 无需 |
| GET | `/analytics/export/csv` | 导出全部对话日志（CSV） | 无需 |
| GET | `/analytics/export/csv/{id}` | 导出单个会话对话日志（CSV） | 无需 |

### FAQ 管理（AdminController — `/admin/**`）— v4.0 新增

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/admin/faq/upload` | 上传 Markdown FAQ 文件 | 无需 |
| GET | `/admin/faq/list` | 获取 FAQ 文件列表（文件名+条目数+大小） | 无需 |
| DELETE | `/admin/faq/{filename}` | 删除指定 FAQ 文件 | 无需 |
| POST | `/admin/faq/reload` | 重新加载知识库（清缓存+重新向量化） | 无需 |

---

## 六、核心架构模式

### 1. Agent 层次结构

```
BaseAgent (抽象)
  ├── run(String) / runStream(String)    — 入口
  ├── sendSse() / sendSseEvent()         — SSE 推送
  └── abstract step()                    — 单步执行
      │
      └── ReActAgent (抽象)
          ├── abstract think()           — 思考（LLM 推理）
          ├── abstract act()             — 行动（工具执行）
          └── step() = think() → act()
              │
              └── ToolCallAgent
                  ├── think() — 调用 ChatModel，检测工具调用
                  └── act()   — 执行工具，结构化日志
                      │
                      ├── HeManus（@Component 已注释，保留备用）
                      │
                      └── CustomerServiceAgent（v2.1 主用）
                          ├── step() — 重写，插入客服专用逻辑：
                          │   0. getCurrentStep() > 1 → 直接 think→act（跳过意图识别）
                          │   1. resolveReferences() — 指代消解+上下文补全（规则+LLM双通道）
                          │   2. IntentClassifier.classify() — 意图识别+槽位提取
                          │   3. DialogStateManager.updateFromIntentResult() — 更新状态
                          │   4. 推送 intent 事件
                          │   5. shouldHandoff() → handoff 事件（连续超范围/轮次过长/用户要求）
                          │   6. OUT_OF_SCOPE → 超范围回复（不转人工）
                          │   7. needsClarification() → clarification 事件（低置信度/槽位缺失）
                          │   8. CHITCHAT → 寒暄回复
                          │   9. 正常 think() → actAndExtractSuggestions()（工具调用+知识库检索+推荐问题推送）
                          └── 工具：CustomerServiceTools（6 个工具，searchKnowledgeBase 两级检索+推荐问题）
```

### 2. 意图识别体系（v2.0 核心）

```
用户输入
  │
  ├── 正则预筛（快速提取格式固定字段）
  │   ├── 订单号：订单号[是：:为]?[A-Za-z0-9]{6,20}
  │   └── 手机号：1[3-9]\d{9}
  │
  └── LLM 结构化输出
      ├── 输入：用户文本 + 预筛结果 + 意图列表 + 槽位定义
      └── 输出：JSON { intent, confidence, slots, needsClarification, clarificationQuestion }
          │
          └── IntentResult
              ├── intent: CustomerIntent 枚举（12 种）
              ├── confidence: 0.0~1.0
              ├── slots: Map<String, String>
              └── needsClarification: boolean
```

**12 种意图：**

| 意图 | 中文名 | 必填槽位 |
|------|--------|----------|
| ORDER_QUERY | 订单查询 | order_id |
| ORDER_CANCEL | 取消订单 | order_id |
| ORDER_REFUND | 退款申请 | order_id, refund_reason |
| PRODUCT_INFO | 产品咨询 | product_name |
| PRODUCT_RECOMMEND | 产品推荐 | 无 |
| COMPLAINT | 投诉建议 | issue_type |
| AFTER_SALE | 售后服务 | issue_type |
| POLICY_QUERY | 政策查询 | 无 |
| CHITCHAT | 闲聊 | 无 |
| REQUEST_HUMAN | 转人工 | 无 |
| CLARIFICATION | 用户澄清 | 无 |
| OUT_OF_SCOPE | 超出范围 | 无 |

### 3. 对话状态机（v2.1 增强）

```
DialogState:
  sessionId, currentIntent, filledSlots, dialogPhase, turnCount,
  historyIntents, confidenceScore, consecutiveOutOfScope, handoffTriggered,
  lastClarificationSlot

DialogPhase 状态转换：
  GREETING → INTENT_COLLECT → SLOT_FILLING → RETRIEVING → ANSWERING → CLOSED
                        ↓                          ↓
                   CLARIFYING ←──── (槽位缺失)    HANDOFF (转人工)

转人工触发条件（shouldHandoff，满足任一）：
  1. 用户明确 REQUEST_HUMAN
  2. 连续 ≥2 轮 OUT_OF_SCOPE（HANDOFF_OUT_OF_SCOPE_THRESHOLD = 2）
  3. 对话轮次 >15（MAX_TURNS_BEFORE_HANDOFF = 15）
  4. DialogPhase 已被标记为 HANDOFF

澄清触发条件（needsClarification，满足任一）：
  1. 置信度 < 0.5（MIN_CONFIDENCE） → 提供意图选择菜单
  2. 必填槽位未填满 → 追问缺失槽位（单缺追问一个，多缺列出所有）
  排除：OUT_OF_SCOPE / CHITCHAT / REQUEST_HUMAN / POLICY_QUERY / PRODUCT_RECOMMEND 不触发澄清
```

### 4. 客服工具集

| 工具名 | 说明 | 参数 | 数据源 |
|--------|------|------|--------|
| queryOrder | 查询订单状态和物流 | orderId | [MOCK] 硬编码模拟订单 |
| queryProduct | 查询产品详情（参数、价格、评分） | productName | [MOCK] 硬编码模拟产品 |
| searchKnowledgeBase | 知识库搜索（两级检索：FAQ匹配+RAG向量） | query | KnowledgeSearchService（FAQ直接匹配→PgVector语义检索） |
| cancelOrder | 取消订单，返回退款信息 | orderId, reason | [MOCK] 硬编码模拟结果 |
| submitRefund | 提交退款申请 | orderId, reason | [MOCK] 硬编码模拟结果 |
| transferToHuman | 转接人工客服 | reason | 无外部数据 |

> **模拟数据替换指南：** 赛题提供真实数据后：
> - `queryOrder/queryProduct/cancelOrder/submitRefund` → 接入真实数据库/API（当前 [MOCK]）
> - `searchKnowledgeBase` → 已实现两级检索（FAQ 直接匹配 + PgVector 向量语义检索）
> - 知识库文档 → 直接替换 `src/main/resources/knowledge/` 目录下的 Markdown 文件，或通过管理后台上传

### 5. 两级知识检索体系（v4.0 重构）

```
用户提问 → searchKnowledgeBase(query)
  │
  ├── Tier 1: FAQ 直接匹配
  │   ├── 遍历所有 doc_type=FAQ 的文档 metadata.question
  │   ├── 相似度计算：双向包含(0.7) + 关键词匹配(0.3) + 编辑距离(0.2)
  │   ├── 阈值 ≥ 0.6 → 直接返回 FAQ 答案（无来源标注）
  │   └── 同时返回同分类下的其他 FAQ 问题作为推荐
  │
  └── Tier 2: RAG 向量语义检索（FAQ 未命中时）
      ├── PgVectorStore.similaritySearch(query, topK=3)
      ├── 返回结果带来源标注 [来源：文档名#段落X]
      └── 同时收集推荐相关问题

KnowledgeSearchService.search(query, topK) → SearchResult
  ├── results: List<KnowledgeSearchResult>  — 匹配结果
  ├── isFaqDirectMatch: boolean              — 是否 FAQ 直接命中
  ├── suggestedQuestions: List<String>       — 推荐相关问题
  └── FAQ 命中时清除 source 字段（无来源标注）
      RAG 检索时保留 source 字段（有来源标注）

CustomerServiceVectorStoreConfig（条件装配）
  ├── @ConditionalOnProperty(name="cs.vector.enabled", havingValue="true")
  ├── 创建 PgVectorStore bean("csVectorStore")，表名 cs_vector_store
  ├── 1024 维，COSINE_DISTANCE，HNSW 索引
  ├── 启动时从 KnowledgeBaseLoader.loadAll() 加载文档并批量写入
  └── 降级：csVectorStore 为 null 时回退到关键词匹配

知识库文档目录（Markdown 文件，可直接替换）
  ├── faq/*.md       — FAQ 文档，按 ## 标题自动分块为 Q&A
  ├── product/*.md   — 产品文档，每文件一个产品
  └── policy/*.md    — 政策文档，每文件一个政策
```

**FAQ 管理后台（v4.0 新增）：**
- 管理员通过前端 `/admin/faq` 页面上传 Markdown 文件导入 FAQ
- FAQ 文件保存到 `data/faq/` 目录（外部化，不嵌入 jar）
- 支持文件列表查看、删除、知识库重载
- API: `/admin/faq/upload|list|{filename}|reload`

**推荐相关问题（v4.0 新增）：**
- FAQ 命中：返回同分类下的其他 FAQ 问题
- RAG 检索：从匹配结果的 category 对应 FAQ 中收集推荐
- 前端展示在消息气泡下方（可点击快速提问）
- 通过 SSE `suggested_questions` 事件推送（JSON 数组）

### 6. 指代消解机制（v2.1 新增）

```
用户输入 → resolveReferences(userInput)
  │
  ├── 规则检测
  │   ├── REFERENCE_PATTERN: (它|这个|那个|这|那|他|她|这件|那件|这台|那台)
  │   └── SHORT_INPUT_THRESHOLD: <6字且无句末标点 → 视为省略输入
  │
  ├── 话题切换检测
  │   └── 仅在 hasReference || isShortInput 时触发消解
  │
  └── LLM 消解通道（resolveWithLlm）
      ├── 输入：userInput + buildContextForResolution()
      │   └── 上下文 = "当前意图：xxx；已知信息：key=value, ..."
      ├── 输出：消解后的完整用户输入
      └── 保护：LLM 失败或结果更短 → 返回原始输入
```

**设计要点：**
- 仅在首轮 step（getCurrentStep() == 1）执行意图识别前进行指代消解
- 后续轮次（getCurrentStep() > 1）直接走 think→act，不做消解
- 使用 DialogState 中的 filledSlots 和 currentIntent 构建上下文

### 7. Advisor 链（执行顺序，保留）

```
请求 → PermissionAdvisor(-100) → SensitiveInfoAdvisor(-75) → BannedWordAdvisor(-50)
     → MessageChatMemoryAdvisor → ReReadingAdvisor(0) → My_loggerAdvisor(99)
     → ChatClient → 响应
```

### 8. RAG 管道（保留，后续阶段改造为客服知识库）

```
文档加载 → DocumentMetadataEnricher → TokenTextSplitter → PgVector 向量库
                                                            ↓
用户查询 → DictionaryQueryTransformer(同义词/停用词) → HybridDocumentRetriever
                                                            ↓
                                              VectorStoreRetriever + KeywordSearchRetriever
                                                            ↓
                                              RetrievalAugmentationAdvisor → ChatClient
```

### 9. 前端数据流

```
ChatInput emits 'send'
  → ChatView.handleSend()
    → useChat().sendMessage(text, imageFile, imageUrl)
      → addMessage(user)          ← 本地 + 服务端同步
      → addMessage(ai placeholder)
      → chatCustomerServiceStream(message, sessionId, callbacks)
        → SSE 事件处理：
          ├── session_id → 保存到 currentSessionId
          ├── intent → 更新 msg.intentInfo（显示意图标签）
          ├── clarification → 更新 msg.content（显示澄清问题）
          ├── handoff → 更新 msg.isHandoff（显示转人工警告）
          ├── tool_call/tool_result → 追加到 msg.thinkingSteps
          ├── answer → 更新 msg.content
          ├── suggested_questions → 更新 msg.suggestedQuestions（推荐相关问题列表）
          └── done → msg.loading = false
```

### 10. 认证流程

```
LoginView → useAuth().login() → POST /auth/login → {token, user}
         → fetchUser() → GET /auth/me → user ref 更新
         → router.push('/')

App.vue onMounted → fetchUser() → 恢复会话
  → loadConversations() → GET /conversations → 服务端加载历史

SSE 请求 → authHeaders() → { Authorization: Bearer <token> }
```

---

## 七、前端设计系统

### CSS 变量体系（global.css）

**暗色主题（默认）：**

| 分类 | 关键变量 | 暗色值 |
|------|----------|--------|
| 背景 | `--bg-app` / `--bg-sidebar` / `--bg-chat` | #0d1117 / #161b22 / #0d1117 |
| 文字 | `--text-primary` / `--text-secondary` / `--text-tertiary` | #e6edf3 / #8b949e / #6e7681 |
| 边框 | `--border-primary` / `--border-input` | #30363d / #30363d |
| 主色 | `--accent-primary` / hover | #1f6feb / #388bfd |
| 客服 | 蓝色系（复用 --accent-primary） | #1f6feb |
| 危险 | `--danger` / `--danger-bg` | #f85149 / rgba(...) |
| 圆角 | `--radius` / `--radius-sm` / `--radius-xs` | 12px / 8px / 6px |
| 过渡 | `--transition` | all 0.2s ease |
| 布局 | `--sidebar-width` / `--chat-max-width` | 260px / 780px |

**亮色主题：** 通过 `[data-theme="light"]` 选择器覆盖所有变量。

### 前端新增组件样式

| 组件 | 样式特征 |
|------|----------|
| 意图标签 | `.intent-tag` — 圆角标签，蓝色背景，显示意图名+置信度 |
| 转人工警告 | `.handoff-alert` — 红色背景脉冲动画，图标+文字 |
| 澄清气泡 | `.bubble.is-clarification` — 黄色边框高亮 |
| 推荐问题 | `.suggested-questions` — 消息气泡下方可点击问题列表 |
| FAQ 管理页 | `AdminFAQView.vue` — 拖拽上传区域 + 文件列表 + 重载按钮 |

### 前端单例模式

`useAuth()`、`useConversations()`、`useChat()`、`useTheme()` 均使用**模块级 ref**（非 Pinia），所有调用方共享同一状态。

---

## 八、配置要点

### application.yml 关键配置

```yaml
server:
  port: 8123
  servlet:
    context-path: /api

spring:
  profiles:
    active: local,dev

# AI 模型
ai:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    chat:
      options:
        model: qwen-plus
    vision-model: qwen-vl-plus

# JWT
jwt:
  secret: ${JWT_SECRET:default-secret-key}
  expiration: 86400000  # 24h

# 聊天记忆
chat:
  memory:
    type: file        # file / mysql / redis
    dir: ./chat-memory

# RAG（默认关闭，按需开启）
ai:
  rag:
    github:
      enabled: false

# 客服知识库向量检索
cs:
  vector:
    enabled: true        # 启用 PgVector 向量检索（需要 DashScope Embedding）
    hybrid:
      enabled: false
      strategy: MERGE
    transformer:
      use-dictionary: false
```

### 环境变量

| 变量 | 用途 |
|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key |
| `DASHSCOPE_AGENT_KEY` | Agent 专用 Key |
| `BOCHA_API_KEY` | Bocha 搜索 API Key |
| `DB_PASSWORD` | PostgreSQL 密码 |
| `JWT_SECRET` | JWT 签名密钥 |
| `ALIYUN_OSS_*` | 阿里云 OSS 配置 |

---

## 九、MCP 服务器（外部工具集成）

### image-search-mcp-server（独立微服务）

独立 Spring Boot 3.5.13 应用，通过 MCP 为主应用提供图片搜索能力。

- **位置：** `image-search-mcp-server/`
- **框架：** `spring-ai-starter-mcp-server`
- **通信方式：** Stdio（无 Web 服务器，无 Banner）
- **工具：** `ImageSearchTool.searchImage(query, perPage)` — 搜索 Pexels API

### MCP 配置（MCP-servers.json）

| 服务 | 命令 | 用途 |
|------|------|------|
| amap-maps | `npx.cmd -y @amap/amap-maps-mcp-server` | 高德地图 |
| image-search-mcp-server | `java -jar image-search-mcp-server/target/...jar` | Pexels 图片搜索 |

> **注意：** MCP 客户端初始化依赖外部进程。若本地环境无 npx/java，启动会失败。当前 HeManus 的 `@Component` 已注释，避免 MCP 初始化阻塞应用启动。客服 Agent 不使用 MCP。

---

## 十、部署架构

```
Docker Compose
├── postgres (pgvector/pg17:latest)
│   ├── 5432 端口
│   └── pgdata 持久卷
│
├── backend (Spring Boot)
│   ├── 8123 端口
│   ├── 依赖 postgres healthy
│   ├── --spring.profiles.active=prod
│   └── chat-memory + tmp 持久卷
│
└── nginx (前端 + 反向代理)
    ├── 80 端口
    ├── / → 静态 HTML（Vue 构建产物）
    └── /api/ → backend:8123（SSE: proxy_buffering off, 300s timeout）
```

---

## 十一、开发约定

### 后端

1. **Agent 创建：** Controller 中 `new CustomerServiceAgent(...)` 手动创建，每次请求一个新实例
2. **意图配置：** 新增意图在 `CustomerIntent` 枚举添加，在 `IntentConfig.init()` 添加槽位定义
3. **工具注册：** 新工具类加 `@Component` + `@Tool` → 在 `ToolRegistration.java` 添加
4. **Advisor 顺序：** 数字越小越先执行。权限(-100) → 脱敏(-75) → 违禁词(-50) → 日志(99)
5. **SecurityConfig 路径：** `/auth/**`、`/ai/**`、`/customer-service/**`、`/image-chat/**`、`/admin/**` 免认证
6. **条件装配：** RAG 增强功能用 `@ConditionalOnProperty` 守护，默认关闭
7. **[MOCK] 标记：** 工具模拟数据搜索 `[MOCK]` 标签定位；知识库文档在 `src/main/resources/knowledge/` 目录
8. **知识库扩展：** 在 `knowledge/faq/`（或 product/policy）下新建/编辑 `.md` 文件，重启即生效，无需改 Java

### 前端

1. **SSE 事件类型：** session_id / intent / clarification / handoff / tool_call / tool_result / answer / suggested_questions / done
2. **组件通信：** Props down, Emits up。跨层级用 `provide/inject`
3. **状态管理：** 模块级单例 ref，不用 Pinia/Vuex
4. **SSE 请求：** 通过 `sse.js` 统一处理，自动带 auth header
5. **样式：** 使用全局 CSS 变量，组件内 `<style scoped>`

---

## 十二、常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| MCP 初始化失败 | HeManus @Component 自动注入 MCP | 已注释 @Component，客服 Agent 不依赖 MCP |
| `ChatModel bean not found` | 生产环境 API Key 未配置 | 检查 application-prod.yml 的 dashscope.api-key |
| 意图识别返回 OUT_OF_SCOPE | LLM 未正确输出 JSON | 检查 IntentClassifier 日志中的 rawResponse |
| 槽位提取失败 | 正则未匹配或 LLM 未返回 slots | 调整 IntentConfig 中的正则和 Prompt |
| 澄清误触发 | slots 未正确合并到 DialogState | 检查 IntentClassifier.parseLlmResponse 逻辑 |
| SSE 跨域或中断 | Nginx 默认缓冲 SSE | `proxy_buffering off; proxy_read_timeout 300s;` |
| 前端 404 | agentType 不匹配 | 默认 agentType 已改为 `customer_service` |
| 第二轮回答意图识别干扰 | step() 在后续轮次重复执行意图识别 | getCurrentStep() > 1 时跳过意图识别，直接 think→act |
| 指代消解结果不正确 | LLM 消解失败或上下文为空 | 检查 DialogState.filledSlots 和 currentIntent 是否有值 |
| 转人工过早触发 | consecutiveOutOfScope 计数偏高 | 调整 HANDOFF_OUT_OF_SCOPE_THRESHOLD（默认 2） |
| 知识库搜索无结果 | [MOCK] 关键词不匹配硬编码内容 | 调整知识库 Markdown 文件或通过管理后台上传新 FAQ |
| FAQ 直接匹配不准确 | 相似度阈值过高/过低 | 调整 KnowledgeSearchService 中的 FAQ_SIMILARITY_THRESHOLD（默认 0.6） |
| PgVector 初始化失败 | DashScope API 账户欠费 | 检查 API Key 和账户余额，或设置 `cs.vector.enabled: false` 降级为关键词匹配 |
| 推荐问题为空 | FAQ 文档分类下无其他问题 | 检查 knowledge/faq/ 下的 Markdown 文件是否有足够的条目 |

---

## 十三、版本变更记录

### v4.0 (2026-04-23) — 两级知识检索 + 管理员 FAQ 管理

**新建文件：**
- `knowledge/CustomerServiceVectorStoreConfig.java` — 客服专用 PgVector 配置（cs_vector_store 表，条件装配）
- `service/FaqManagementService.java` — FAQ 文件上传/列表/删除/知识库重载
- `controller/AdminController.java` — 管理后台 API（/admin/faq/upload|list|{filename}|reload）
- `docs/schema-v2-phase4.sql` — 数据库迁移（user.role 字段 + cs_vector_store 表）
- `AI_Agent_Fronted/src/api/admin.js` — 管理 API 调用
- `AI_Agent_Fronted/src/components/AdminFAQView.vue` — FAQ 管理页面（拖拽上传+列表+删除+重载）

**改造文件：**
- `knowledge/KnowledgeSearchService.java` — 重写为两级检索：
  - Tier 1: `tryFaqDirectMatch()` — FAQ 直接匹配（双向包含 + 关键词 + 编辑距离，阈值 0.6）
  - Tier 2: `vectorSearch()` — PgVector 语义检索（降级到关键词匹配）
  - 新增 `SearchResult` record：results + isFaqDirectMatch + suggestedQuestions
  - FAQ 命中清除 source 字段（无来源标注），RAG 保留 source 字段（有来源标注）
  - 新增 `collectSuggestedQuestions()` — 收集同分类推荐问题
  - 新增 `clearCache()` — 支持知识库热重载
- `Agent/customer_service/CustomerServiceTools.java` — searchKnowledgeBase 返回 JSON 新增：
  - `matchType`: "faq_direct" 或 "rag_search"
  - `suggestedQuestions`: 推荐相关问题数组
  - FAQ 命中时 results 不含 source 字段
- `Agent/customer_service/CustomerServiceAgent.java` — 新增：
  - `actAndExtractSuggestions()` — 执行工具后解析推荐问题
  - `extractAndSendSuggestedQuestions()` — 从工具结果 JSON 提取推荐问题并通过 SSE 推送
  - `sendSseEvent("suggested_questions", ...)` — 新 SSE 事件类型
  - 更新 nextStepPrompt — 区分 faq_direct 和 rag_search 的来源标注规则
- `entity/User.java` — 新增 `role` 字段（VARCHAR(20)，默认 "user"）
- `config/SecurityConfig.java` — 放行 `/admin/**`
- `application-local.yml` — 添加 `cs.vector.enabled: true`
- `AI_Agent_Fronted/src/composables/useChat.js` — SSE 新增 `suggested_questions` 事件处理
- `AI_Agent_Fronted/src/components/ChatMessage.vue` — 新增 `suggestedQuestions` prop + 推荐问题列表（可点击）
- `AI_Agent_Fronted/src/components/ChatView.vue` — 传递 suggestedQuestions + 处理 ask-question 事件
- `AI_Agent_Fronted/src/components/Sidebar.vue` — 添加「FAQ 管理」入口按钮
- `AI_Agent_Fronted/src/router/index.js` — 新增 `/admin/faq` 路由

**两级检索流程：**
1. 用户提问 → `searchKnowledgeBase` 工具 → `KnowledgeSearchService.search(query, 3)`
2. Tier 1: 遍历 FAQ 文档 metadata.question，计算相似度（双向包含 + 关键词 + 编辑距离）
3. 相似度 ≥ 0.6 → 直接返回 FAQ 答案（无来源标注）+ 同分类推荐问题
4. Tier 2: PgVector `similaritySearch(query, 3)` → 返回 RAG 结果（带来源标注）+ 推荐问题
5. Agent 从工具结果提取推荐问题 → SSE `suggested_questions` 事件推送到前端
6. 前端展示推荐问题列表 → 用户可点击快速提问

**FAQ 管理后台：**
- 前端 `/admin/faq` 页面（侧边栏入口）
- 拖拽/点击上传 Markdown 文件 → 保存到 `data/faq/` 目录
- 文件列表展示（文件名 + Q&A 条目数 + 大小）
- 删除文件 + 重载知识库按钮
- 上传后自动清除知识库缓存，下次搜索重新加载

**待执行 SQL：** `docs/schema-v2-phase4.sql`（ALTER TABLE users ADD role + CREATE TABLE cs_vector_store）

**新建文件：**
- `entity/DialogAnalytics.java` — dialog_analytics 表实体（会话聚合分析）
- `mapper/DialogAnalyticsMapper.java` — MyBatis-Plus Mapper
- `service/AnalyticsService.java` — 统计分析服务（总览/意图分布/会话列表/单会话详情/聚合更新）
- `service/ExportService.java` — CSV 日志导出服务（手写 CSV，无额外依赖）
- `controller/AnalyticsController.java` — 分析统计 REST API（6 个端点）
- `docs/schema-v2-phase3.sql` — 数据库迁移脚本（扩展表+新建表）

**改造文件：**
- `entity/ConversationMessage.java` — 新增 6 个字段：intent, slots(JSONB), confidence, isHandoff, isClarification, sources(JSONB)
- `mapper/ConversationMessageMapper.java` — 新增 5 个统计查询方法（countByIntent, countHandoffConversations 等）
- `service/ConversationService.java` — 新增 addMessage 重载（含意图/槽位/置信度等分析字段）
- `controller/CustomerServiceController.java` — SSE 流结束后异步持久化对话数据 + 更新聚合分析
- `config/SecurityConfig.java` — 放行 `/analytics/**`，全部 `permitAll`
- `app/LoveApp.java` — 注释 `@Component`（MCP 禁用后 ToolCallbackProvider bean 不存在）
- `controller/AiController.java` — 注释 `@RestController`（同上原因）
- `application-local.yml` — 禁用 MCP 客户端（`spring.ai.mcp.client.enabled: false`）

**Analytics API 端点：**
- `GET /analytics/overview` — 总览：总会话数、意图分布、转人工率、澄清次数、平均置信度、平均轮次
- `GET /analytics/intent-distribution` — 意图分布统计
- `GET /analytics/sessions` — 会话列表（含分析摘要）
- `GET /analytics/sessions/{id}` — 单会话完整对话回放
- `GET /analytics/export/csv` — 全量对话日志 CSV 导出
- `GET /analytics/export/csv/{id}` — 单会话 CSV 导出

**数据持久化流程：**
客服对话 SSE 流结束后，异步执行：
1. 自动创建 conversation 记录
2. 保存用户消息 + AI 回复（含意图/槽位/置信度/转人工/澄清标记）
3. 更新 dialog_analytics 聚合表

**待执行 SQL：** `docs/schema-v2-phase3.sql`（扩展 conversation_messages 表 + 新建 dialog_analytics 表）

### v2.2 (2026-04-22) — 知识库数据外部化

**改造文件：**
- `KnowledgeBaseLoader.java` — 从硬编码 Java 数据改为从 `classpath:knowledge/**/*.md` 加载 Markdown 文件
  - 使用 ResourcePatternResolver 扫描文件（复用 LoveAppDocumentLoder 同样的模式）
  - FAQ 文件按 `## 标题` 自动分块为独立 Q&A Document
  - 产品/政策文件每个生成一个 Document
  - 输出 Document 格式与硬编码版本完全一致，下游无感知
- `KnowledgeSearchService.java` — 新增懒加载初始化日志

**新建文件（知识库文档目录）：**
- `knowledge/faq/订单相关.md` — 5 条 Q&A（查询/取消/待发货/发货时间/修改地址）
- `knowledge/faq/退换货政策.md` — 5 条 Q&A（政策/质量问题/运费/退款/换其他商品）
- `knowledge/faq/配送物流.md` — 5 条 Q&A（运费时效/配送方式/物流追踪/指定配送/丢失损坏）
- `knowledge/faq/售后保修.md` — 5 条 Q&A（保修政策/申请维修/维修时间/过保维修/延保）
- `knowledge/faq/支付相关.md` — 5 条 Q&A（支付方式/申请退款/支付失败/发票/分期）
- `knowledge/product/智能手表Pro.md` — 产品参数+功能+常见问题
- `knowledge/product/无线蓝牙耳机Air.md` — 产品参数+功能+常见问题
- `knowledge/product/智能音箱Home.md` — 产品参数+功能+常见问题
- `knowledge/policy/隐私保护政策.md` — 信息收集/保护/共享/用户权利
- `knowledge/policy/售后服务承诺.md` — 退换货/保修/时效/投诉

**知识库扩展：** 原 7 条硬编码 FAQ → 25 条文件化 FAQ（5 个分类文件 × 5 条）+ 3 个产品文档 + 2 个政策文档
**新增/替换指引：** 在 `knowledge/` 对应子目录新建/编辑 `.md` 文件即可，无需改 Java 代码

### v2.1 (2026-04-22) — 核心对话能力增强

**新增模块：**
- `knowledge/KnowledgeBaseLoader.java` — 知识库加载器 [MOCK]（7 条 FAQ + 3 条产品文档 + 2 条政策文档）
- `knowledge/KnowledgeSearchService.java` — 知识库搜索服务 [MOCK]（关键词匹配模拟向量检索，含出处标注）

**CustomerServiceAgent 增强：**
- `resolveReferences()` — 指代消解与上下文补全（规则检测 + LLM 双通道）
  - 中文指代词检测：它/这个/那个/这/那/他/她/这件/那件/这台/那台
  - 短输入省略检测：< 6 字且无句末标点
  - LLM 消解：基于 DialogState 上下文（filledSlots + currentIntent）
- `shouldHandoff()` — 增强转人工判断（4 种触发条件）
- `needsClarification()` / `buildClarification()` — 增强澄清（低置信度意图选择菜单 + 多槽位逐个/批量追问）
- step() 循环修复：`getCurrentStep() > 1` 时跳过意图识别，直接执行 think→act

**CustomerServiceTools 增强：**
- `searchKnowledgeBase` — 接入 KnowledgeSearchService，返回带来源标注的搜索结果
  - 返回字段：content, source, docType, category, chunkIndex, relevance

**客服系统 Prompt 增强（customer-service.txt）：**
- 新增出处标注规则：`[来源：文档名#段落X]`
- 新增多轮对话规范：关注上下文、不重复询问、话题切换自然过渡
- 新增转人工规则：超出服务范围/用户要求/敏感操作/多次未解决
- 新增严格禁止条目：不编造信息、不提供专业建议、不透露个人信息

**已知限制（模拟数据）：**
- KnowledgeBaseLoader：12 条硬编码文档 → 赛题提供数据后替换
- KnowledgeSearchService：关键词匹配 → 后续接入 PgVector 向量检索
- queryOrder/queryProduct/cancelOrder/submitRefund：硬编码 JSON → 接入真实数据源

### v2.0 (2026-04-22) — 智能客服改造

**新增模块：**
- `intent/` — 意图识别体系（IntentClassifier + IntentConfig + CustomerIntent + SlotDefinition + IntentResult）
- `dialog/` — 对话状态管理（DialogStateManager + DialogState + DialogPhase）
- `agent/customer_service/` — 客服 Agent（CustomerServiceAgent + CustomerServiceTools）
- `controller/CustomerServiceController.java` — 客服 API 端点
- `prompts/customer-service.txt` — 客服系统 Prompt
- `docs/智能客服Agent需求文档.md` — 赛题需求文档

**改造文件：**
- `SecurityConfig.java` — 新增 `/customer-service/**` 放行
- `HeManus.java` — 注释 `@Component`，保留代码备用
- `chat.js` — 替换为 `chatCustomerServiceStream`，处理新 SSE 事件
- `useChat.js` — 替换为客服模式消息发送逻辑
- `useConversations.js` — 默认 agentType 改为 `customer_service`
- `Sidebar.vue` — 去掉角色切换，改为纯客服模式
- `ChatMessage.vue` — 增加意图标签、转人工警告、澄清气泡
- `ChatView.vue` — 传递 intentInfo/isClarification/isHandoff props
- `WelcomeScreen.vue` — 改为智能客服欢迎页
- `Conversation.java` — agentType 默认值改为 `customer_service`

### v1.0 — 初始版本（恋爱大师 + 超级智能体）
