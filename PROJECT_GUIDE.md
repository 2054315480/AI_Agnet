# AI Agent 项目全景指南

> **版本:** v1.0 | **最后更新:** 2026-04-21 | **作者:** 秋鹤
>
> 本文档供 AI Agent（Claude Code）快速理解项目全貌。每次修改项目结构、新增模块或变更架构后，请同步更新本文档。

---

## 一、项目概览

一个基于 Spring Boot 3.5 + Vue 3 的多角色 AI 智能体对话平台。核心功能：

- **恋爱大师** — 基于 RAG 的情感咨询智能体（知识库 + 向量检索）
- **超级智能体 (HeManus)** — 自主规划执行复杂任务的 Agent（工具调用 + 思考链）
- **多模态** — 图片识别、PDF 生成/解析
- **用户系统** — JWT 认证、对话历史持久化、多设备同步

---

## 二、技术栈

### 后端（Java）

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.10 | 基础框架 |
| Java | 21 | 运行时 |
| Spring AI Alibaba | 1.1.2.0 | LLM 集成（DashScope/Qwen） |
| MyBatis-Plus | 3.5.9 | ORM |
| PostgreSQL + pgvector | 17 | 关系数据库 + 向量存储 |
| Spring Security + jjwt | 0.12.6 | 认证鉴权 |
| Redis | — | 聊天记忆（可选） |
| iText 9 + PDFBox 2 | — | PDF 生成/解析 |
| Alibaba Cloud OSS | 3.18.1 | 文件存储 |
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
├── AI_Agent_Fronted/                 # 前端 Vue 项目
│   ├── src/
│   │   ├── main.js                   # 入口：创建 Vue app、注册路由、初始化主题
│   │   ├── App.vue                   # 根组件：侧边栏 + 主区域 + 移动端响应
│   │   ├── api/
│   │   │   ├── auth.js               # 认证模块（单例 ref：user/token/isAuthenticated）
│   │   │   └── chat.js               # 聊天 API（4 个流式函数，自动带 auth header）
│   │   ├── utils/
│   │   │   └── sse.js                # SSE 流解析（fetchSSE / fetchMultipartSSE）
│   │   ├── composables/
│   │   │   ├── useConversations.js   # 对话管理（单例，服务端同步 + localStorage 降级）
│   │   │   ├── useChat.js            # 发送消息（协调 SSE 流 + 消息状态更新）
│   │   │   └── useTheme.js           # 主题切换（dark/light，localStorage 持久化）
│   │   ├── components/
│   │   │   ├── LoginView.vue         # 登录/注册页（左右分栏布局）
│   │   │   ├── Sidebar.vue           # 侧边栏（Agent 切换、历史记录、用户信息）
│   │   │   ├── ChatView.vue          # 聊天主区域（欢迎屏 / 消息列表 + 输入框）
│   │   │   ├── ChatMessage.vue       # 消息气泡（Markdown、思考链、图片预览）
│   │   │   ├── ChatInput.vue         # 输入框（自动高度、图片上传、快捷发送）
│   │   │   ├── WelcomeScreen.vue     # 空状态欢迎页（Agent 专属问候 + 快捷问题）
│   │   │   ├── ThemeToggle.vue       # 主题切换按钮
│   │   │   └── StepBlock.vue         # 折叠步骤块（当前未直接使用）
│   │   ├── router/
│   │   │   └── index.js              # 路由：/login（guest）、/（需 auth）
│   │   └── assets/styles/
│   │       └── global.css            # 全局 CSS 设计系统（70+ 变量，暗色/亮色主题）
│   ├── Dockerfile                    # 前端构建 + Nginx 部署
│   ├── vite.config.js                # 代理 /api → localhost:8123
│   └── package.json
│
├── src/main/java/com/qh/ai_agent/   # 后端 Java 源码
│   ├── AiAgentApplication.java       # Spring Boot 启动类
│   │
│   ├── controller/                   # REST 控制器（4 个）
│   │   ├── AiController.java         # /ai/** — AI 对话、RAG、工具（12 个端点）
│   │   ├── AuthController.java       # /auth/** — 注册、登录、用户信息（3 个端点）
│   │   ├── ConversationController.java  # /conversations/** — 对话 CRUD（6 个端点）
│   │   └── ImageChatController.java  # /image-chat/** — 图片识别（2 个端点）
│   │
│   ├── app/                          # 核心应用层
│   │   ├── LoveApp.java              # 恋爱大师（ChatClient + Advisor 链 + 15+ chat 方法）
│   │   └── ImageChatApp.java         # 图片对话（qwen-vl-plus 视觉模型）
│   │
│   ├── Agent/                        # Agent 框架
│   │   ├── BaseAgent.java            # 抽象基类（run/stream、SSE、状态管理）
│   │   ├── ReActAgent.java           # ReAct 模式（think → act 循环）
│   │   ├── ToolCallAgent.java        # 工具调用（ToolCallingManager、日志增强）
│   │   ├── HeManus.java              # 超级智能体（10 个工具、自主规划、maxStep=20）
│   │   └── Model/
│   │       └── AgentState.java       # 状态枚举：IDLE / RUNNING / FINISHED / ERROR
│   │
│   ├── tools/                        # AI 工具（10 个 + 注册配置）
│   │   ├── ToolRegistration.java     # @Configuration，注册 ToolCallback[] bean
│   │   ├── FileOperationTool.java    # 文件读写
│   │   ├── WebSearchTool.java        # Bocha AI 网络搜索
│   │   ├── WebScrapingTool.java      # Jsoup 网页抓取
│   │   ├── ResourceDownloadTool.java # 资源下载（10 个 @Tool 方法）
│   │   ├── TerminalOperationTool.java # 终端命令（白名单安全机制）
│   │   ├── TerminateTool.java        # 终止信号
│   │   ├── PDFGenerationTool.java    # PDF 生成（iText + OSS 上传）
│   │   ├── DateTimeTool.java         # 日期时间（9 个 @Tool 方法）
│   │   ├── DatabaseQueryTool.java    # 数据库查询（只读 SQL，Markdown 表格输出）
│   │   └── PdfParseTool.java         # PDF 解析（PDFBox）
│   │
│   ├── rag/                          # RAG 检索增强生成
│   │   ├── LoveAppDocumentLoder.java # 文档加载（Markdown → 向量化）
│   │   ├── LoveAppVectorStoreConfig.java  # 内存向量库（SimpleVectorStore）
│   │   ├── PgvectorVectorStoreConfig.java # PgVector 向量库（1024 维、COSINE_DISTANCE）
│   │   ├── LoveAppRagCloudAdvisorConfig.java  # 云 RAG（阿里百炼）
│   │   ├── LoveAppRagCustomAdvisorFactory.java # 过滤器工厂（status + sourceType + category）
│   │   ├── LoveAppContexttualQueryAugmenterFactory.java # 空上下文降级
│   │   ├── QueryReweiter.java        # LLM 查询重写
│   │   ├── MykeywordEnricher.java    # AI 关键词提取
│   │   ├── MyTokenTextSplitter.java  # 文档分块
│   │   ├── enricher/
│   │   │   └── DocumentMetadataEnricher.java  # 元信息丰富（类型/分类/标签/哈希）
│   │   ├── reader/
│   │   │   ├── GitHubDocumentReader.java  # GitHub 仓库文档读取
│   │   │   └── GitHubApiService.java      # GitHub REST API 客户端
│   │   ├── transformer/
│   │   │   └── DictionaryQueryTransformer.java  # 字典查询转换（同义词/停用词）
│   │   ├── retriever/
│   │   │   ├── KeywordSearchRetriever.java    # PostgreSQL ILIKE 关键词检索
│   │   │   └── HybridDocumentRetriever.java   # 混合检索（MERGE / FALLBACK 策略）
│   │   ├── config/
│   │   │   └── EnhancedRagConfig.java  # 增强版 RAG 配置（@ConditionalOnProperty）
│   │   └── model/
│   │       └── RagRequest.java         # RAG 请求 DTO
│   │
│   ├── advisor/                      # Advisor 链（按优先级排序）
│   │   ├── PermissionAdvisor.java     # -100 用户权限校验
│   │   ├── SensitiveInfoAdvisor.java  # -75  敏感信息脱敏（手机/身份证/邮箱/银行卡）
│   │   ├── BannedWordAdvisor.java     # -50  违禁词过滤
│   │   ├── ReReadingAdvisor.java      # 0    RE2 查询复用（提升推理质量）
│   │   └── My_loggerAdvisor.java      # 99   请求/响应日志
│   │
│   ├── chatmemory/                   # 聊天记忆（3 种后端）
│   │   ├── FileConstant.java         # 常量：文件保存路径
│   │   ├── FileBasedChatMemory.java  # 文件（Kryo 序列化，默认）
│   │   ├── MySqlChatMemory.java      # PostgreSQL（MyBatis-Plus）
│   │   └── RedisChatMemory.java      # Redis（JSON + TTL）
│   │
│   ├── config/                       # 配置类
│   │   ├── SecurityConfig.java       # Spring Security（JWT + CORS + 无状态会话）
│   │   ├── JwtUtil.java              # JWT 工具（HMAC-SHA384 签名）
│   │   ├── JwtAuthenticationFilter.java  # JWT 过滤器（Header 或 ?token= 查询参数）
│   │   ├── PasswordConfig.java       # BCryptPasswordEncoder（单独 @Configuration 避免循环依赖）
│   │   ├── CorsConfig.java           # CORS 全开
│   │   ├── HttpClientConfig.java     # HTTP 代理配置
│   │   ├── BochaSearchConfig.java    # Bocha 搜索 API 配置
│   │   ├── ImageChatConfig.java      # 图片对话配置
│   │   ├── ChatMemoryConfig.java     # 聊天记忆条件装配
│   │   ├── RedisConfig.java          # Redis 序列化
│   │   ├── OssConfig.java            # 阿里云 OSS 配置
│   │   └── SensitiveInfoConfig.java  # 脱敏开关
│   │
│   ├── entity/                       # 数据实体
│   │   ├── User.java                 # users 表
│   │   ├── Conversation.java         # conversations 表
│   │   ├── ConversationMessage.java  # conversation_messages 表
│   │   ├── ChatMemoryEntity.java     # chat_memory 表
│   │   └── bocha/                    # Bocha 搜索 API 请求/响应
│   │       ├── BochaSearchRequest.java
│   │       └── BochaSearchResponse.java
│   │
│   ├── mapper/                       # MyBatis-Plus Mapper
│   │   ├── UserMapper.java           # + findByUsername
│   │   ├── ConversationMapper.java
│   │   ├── ConversationMessageMapper.java  # + findByConversationId
│   │   └── ChatMemoryMapper.java     # + findByConversationIdOrderByCreatedAtAsc
│   │
│   ├── service/                      # 业务服务
│   │   ├── UserService.java          # 注册/登录/查询
│   │   ├── ConversationService.java  # 对话 CRUD + 消息管理
│   │   ├── OssService.java           # OSS 上传（懒加载客户端）
│   │   ├── BannedWordService.java    # 违禁词检查/过滤
│   │   ├── PromptTemplateService.java # Prompt 模板加载/渲染
│   │   └── SensitiveInfoService.java # 敏感信息检测/脱敏
│   │
│   ├── model/                        # DTO
│   │   ├── ImageChatRequest.java
│   │   ├── ImageChatResponse.java
│   │   └── PromptTemplate.java       # 模板变量替换（{variable} 语法）
│   │
│   ├── exception/                    # 自定义异常
│   │   ├── PermissionDeniedException.java
│   │   ├── BannedWordException.java
│   │   └── PromptTemplateException.java
│   │
│   └── demo/                         # 示例代码（不影响运行）
│       └── invoke/                   # SDK/HTTP/LangChain 调用示例
│
├── src/main/resources/
│   ├── application.yml               # 主配置（端口 8123、context-path /api）
│   ├── application-local.yml         # 本地开发（PostgreSQL、DashScope key）
│   ├── application-dev.yml           # 免费模型（MiniMax-M2.1）
│   ├── application-prod.yml          # 生产环境（无 Redis、qwen-plus）
│   ├── MCP-servers.json              # MCP 服务器配置（高德地图、图片搜索）
│   ├── dictionaries/
│   │   └── synonyms.json             # 同义词（42 条）+ 停用词（17 个）
│   ├── prompts/                      # Prompt 模板
│   │   ├── love-advisor.txt          # 恋爱大师系统 Prompt（四维框架）
│   │   ├── he-manus.txt              # 超级智能体系统 Prompt
│   │   ├── image-analyst.txt         # 图片分析 Prompt
│   │   └── general-assistant.txt     # 通用助手 Prompt
│   └── docs/                         # RAG 知识库文档
│       ├── 恋爱常见问题及回答-单身篇.md
│       ├── 恋爱常见问题及回答-恋爱篇.md
│       └── 恋爱常见问题及回答-结婚篇.md
│
├── pom.xml                           # Maven 依赖
├── Dockerfile                        # 多阶段构建（MCP + 后端 + Node.js）
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
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- 对话表
CREATE TABLE conversations (
    id          VARCHAR(64) PRIMARY KEY,    -- 前端生成
    user_id     BIGINT REFERENCES users(id),
    title       VARCHAR(200),
    agent_type  VARCHAR(20) NOT NULL DEFAULT 'love',  -- love / manus
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- 对话消息表
CREATE TABLE conversation_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,   -- user / assistant
    content         TEXT,
    image_url       VARCHAR(500),
    created_at      TIMESTAMP
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
```

**索引：**
- `idx_conversations_user_id ON conversations(user_id)`
- `idx_conversation_messages_conv_id ON conversation_messages(conversation_id)`

---

## 五、API 端点一览

> 所有端点前缀为 `/api`（由 `server.servlet.context-path` 配置）

### AI 对话（AiController — `/ai/**`）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/ai/love_app/chat/sse` | 恋爱大师 SSE 流式 | 无需 |
| POST | `/ai/love_app/chat/sse_with_image` | 恋爱大师 + 图片 | 无需 |
| GET | `/ai/manus/chat` | 超级智能体 SSE | 无需 |
| POST | `/ai/manus/chat_with_image` | 超级智能体 + 图片 | 无需 |
| GET | `/ai/love_app/chat/sync` | 同步对话（调试用） | 无需 |
| GET | `/ai/love_app/chat/server_sent_event` | SSE（ServerSentEvent） | 无需 |
| GET | `/ai/love_app/chat/sse_emitter` | SSE（SseEmitter） | 无需 |
| GET | `/ai/templates` | Prompt 模板列表 | 无需 |
| GET | `/ai/rag/github` | GitHub 仓库 RAG | 无需 |
| POST | `/ai/rag/github/load` | 加载 GitHub 文档 | 无需 |
| POST | `/ai/rag/enhanced` | 增强版 RAG | 无需 |
| POST | `/ai/tools/parse-pdf` | PDF 上传解析 | 无需 |

### 认证（AuthController — `/auth/**`）

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/auth/register` | 注册（返回 token + user） | 无需 |
| POST | `/auth/login` | 登录（返回 token + user） | 无需 |
| GET | `/auth/me` | 获取当前用户信息 | JWT |

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
| POST | `/image-chat/explain` | 通过 URL 解释图片 | 无需 |
| POST | `/image-chat/upload-explain` | 上传图片并解释 | 无需 |

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
                      └── HeManus (@Component)
                          └── 超级智能体：10 个工具、maxStep=20
```

### 2. Advisor 链（执行顺序）

```
请求 → PermissionAdvisor(-100) → SensitiveInfoAdvisor(-75) → BannedWordAdvisor(-50)
     → MessageChatMemoryAdvisor → ReReadingAdvisor(0) → My_loggerAdvisor(99)
     → ChatClient → 响应
```

### 3. RAG 管道

```
文档加载 → DocumentMetadataEnricher → TokenTextSplitter → PgVector 向量库
                                                            ↓
用户查询 → DictionaryQueryTransformer(同义词/停用词) → HybridDocumentRetriever
                                                            ↓
                                              VectorStoreRetriever + KeywordSearchRetriever
                                                            ↓
                                              RetrievalAugmentationAdvisor → ChatClient
```

**向量库双轨：**
- `loveAppVectorStore` — SimpleVectorStore（内存，开发用）
- `pgvectorVectorStore` — PgVector（生产用，1024 维，COSINE_DISTANCE，HNSW 索引）

### 4. 前端数据流

```
ChatInput emits 'send'
  → ChatView.handleSend()
    → useChat().sendMessage(text, imageFile, imageUrl)
      → addMessage(user)          ← 本地 + 服务端同步
      → addMessage(ai placeholder)
      → chatLoveStream / chatManusStream  ← SSE 流（带 JWT header）
        → onChunk/onStep → updateLastMessage()
          → onComplete → updateLastMessage({loading:false})
            → POST /conversations/{id}/messages  ← 服务端持久化
```

### 5. 认证流程

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
| 恋爱 | `--accent-love` / hover / bg / border | #f778ba / ... |
| 智能体 | `--accent-manus` / hover / bg / border | #3fb8c9 / ... |
| 危险 | `--danger` / `--danger-bg` | #f85149 / rgba(...) |
| 圆角 | `--radius` / `--radius-sm` / `--radius-xs` | 12px / 8px / 6px |
| 过渡 | `--transition` | all 0.2s ease |
| 布局 | `--sidebar-width` / `--chat-max-width` | 260px / 780px |

**亮色主题：** 通过 `[data-theme="light"]` 选择器覆盖所有变量。

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

## 九、部署架构

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

## 十、开发约定

### 后端

1. **工具注册：** 新工具类加 `@Component` + `@Tool` 注解 → 在 `ToolRegistration.java` 添加参数 → 自动注册
2. **Advisor 顺序：** 数字越小越先执行。权限(-100) → 脱敏(-75) → 违禁词(-50) → 日志(99)
3. **Entity 注解：** `@TableName` + `@Data`（Lombok）+ MyBatis-Plus `@TableId(type = ...)`
4. **条件装配：** RAG 增强功能用 `@ConditionalOnProperty` 守护，默认关闭
5. **SecurityConfig 路径：** `/auth/**`、`/ai/**`、`/image-chat/**` 免认证；其余需 JWT

### 前端

1. **组件通信：** Props down, Emits up。跨层级用 `provide/inject`（如 sidebarStore）
2. **状态管理：** 模块级单例 ref，不用 Pinia/Vuex
3. **SSE 请求：** 通过 `sse.js` 统一处理，自动带 auth header
4. **样式：** 使用全局 CSS 变量，组件内 `<style scoped>`
5. **路由守卫：** 检查 localStorage token，无 token 重定向到 `/login`

---

## 十一、常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `ChatModel bean not found` | 生产环境 API Key 未配置 | 检查 application-prod.yml 的 dashscope.api-key |
| 前端登录后 `router.push` undefined | `useRouter()` 在函数内调用 | 必须在 `<script setup>` 顶层调用 |
| PDFBox 3.x API 不兼容 | `PDDocument.load(File)` 在 3.x 已移除 | 使用 PDFBox 2.0.33 |
| SecurityConfig 循环依赖 | PasswordEncoder 定义在 SecurityConfig 内 | 拆分到独立的 PasswordConfig |
| `FilterExpressionBuilder.and()` 类型错误 | `.and()` 需要 `Op` 对象不是 `Filter.Expression` | 用 `.eq()` 返回的 `Op` 链式调用 |
| SSE 跨域或中断 | Nginx 默认缓冲 SSE | `proxy_buffering off; proxy_read_timeout 300s;` |
| Context-path 导致前端 404 | 后端 context-path 为 `/api` | 前端请求已经带 `/api` 前缀，Nginx 代理正确 |
