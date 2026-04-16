# AI 超级智能体（AI Super Agent）

一个基于 **Spring Boot 3.5 + Vue 3** 构建的全栈 AI 智能体应用，集成了阿里云 DashScope（通义千问）大模型、RAG 知识库检索、MCP 工具协议、自主智能体（ReAct 模式）等多项 AI 能力，提供实时流式对话体验。

---

## 项目架构

```
AI_Agent/
├── src/                              # 后端 - Spring Boot 3.5 (Java 21)
│   └── main/java/com/qh/ai_agent/
│       ├── AiAgentApplication.java   # 启动入口
│       ├── app/                      # 核心应用层
│       │   ├── LoveApp.java          # 情感顾问聊天应用（多策略 RAG + 工具调用）
│       │   └── ImageChatApp.java     # 多模态图片理解应用
│       ├── Agent/                    # 自主智能体框架
│       │   ├── BaseAgent.java        # 基础智能体（状态管理、SSE 推送）
│       │   ├── ReActAgent.java       # ReAct 推理-行动模式
│       │   ├── ToolCallAgent.java    # 工具调用智能体
│       │   └── HeManus.java          # 超级智能体（自主规划 + 多工具协作）
│       ├── controller/               # API 控制器
│       │   ├── AiController.java     # AI 对话接口（SSE 流式输出）
│       │   └── ImageChatController.java  # 图片理解接口
│       ├── tools/                    # AI 可调用的工具集
│       │   ├── ToolRegistration.java # 工具统一注册中心
│       │   ├── WebSearchTool.java    # 网络搜索（博查 AI）
│       │   ├── WebScrapingTool.java  # 网页抓取
│       │   ├── FileOperationTool.java    # 文件读写
│       │   ├── ResourceDownloadTool.java # 资源下载
│       │   ├── TerminalOperationTool.java # 终端命令执行
│       │   ├── PDFGenerationTool.java    # PDF 生成
│       │   └── TerminateTool.java    # 终止工具
│       ├── advisor/                  # 对话拦截器链
│       │   ├── PermissionAdvisor.java    # 权限校验
│       │   ├── BannedWordAdvisor.java    # 敏感词过滤
│       │   ├── ReReadingAdvisor.java     # Re2 重读增强
│       │   └── My_loggerAdvisor.java     # 日志记录
│       ├── rag/                      # RAG 检索增强生成
│       │   ├── LoveAppDocumentLoder.java         # 文档加载器
│       │   ├── LoveAppVectorStoreConfig.java     # 内存向量库
│       │   ├── PgvectorVectorStoreConfig.java    # PGVector 向量库
│       │   ├── LoveAppRagCloudAdvisorConfig.java # 云端知识库
│       │   └── LoveAppRagCustomAdvisorFactory.java # 自定义过滤工厂
│       ├── chatmemory/               # 会话记忆持久化
│       │   ├── FileBasedChatMemory.java  # 基于文件的会话记忆（Kryo 序列化）
│       │   └── MySqlChatMemory.java      # 基于数据库的会话记忆（MyBatis-Plus）
│       ├── service/                  # 业务服务
│       └── config/                   # 配置类（CORS、HTTP 客户端等）
├── AI_Agent_Fronted/                 # 前端 - Vue 3 + Vite
│   └── src/
│       ├── views/                    # 页面视图
│       │   ├── Home.vue              # 首页（功能导航）
│       │   ├── LoveChat.vue          # 情感顾问对话页
│       │   └── ManusChat.vue         # 超级智能体对话页
│       ├── components/               # 可复用组件
│       │   ├── ChatInput.vue         # 聊天输入框
│       │   └── ChatMessage.vue       # 消息气泡
│       ├── api/chat.js               # API 接口封装
│       ├── utils/sse.js              # SSE 流式通信工具
│       └── router/index.js           # 路由配置
├── image-search-mcp-server/          # 独立 MCP 服务 - 图片搜索
│   └── src/.../Tools/
│       └── ImageSearchTool.java      # Pexels 图片搜索工具
└── pom.xml                           # Maven 构建配置
```

---

## 核心功能

### 1. 情感顾问（Love Advisor）
- 基于通义千问大模型，扮演资深情感咨询师角色
- 支持**四种 RAG 检索增强策略**：
  - 本地内存向量库（SimpleVectorStore）
  - PGVector 向量数据库（PostgreSQL）
  - 阿里云 DashScope 云端知识库
  - 自定义元数据过滤工厂
- 多轮对话记忆（文件持久化 / 数据库持久化可切换）
- SSE 实时流式输出

### 2. 超级智能体（HeManus）
- 基于 **ReAct（Reason + Act）模式**的自主智能体
- 智能体自主规划执行步骤，最多 20 步
- 内置 **7 类工具**可供自主调用：
  - 网络搜索（博查 AI 搜索引擎）
  - 网页内容抓取（Jsoup 解析）
  - 文件读写操作
  - 资源下载（支持重试、进度追踪）
  - 终端命令执行（安全沙箱，命令白名单）
  - PDF 文档生成（支持表格、图片、报告）
  - 终止信号
- 通过 **MCP 协议**接入外部工具：
  - 高德地图服务（地点搜索、路线规划）
  - Pexels 图片搜索服务

### 3. 多模态图片理解
- 支持通过 URL 或文件上传分析图片
- 使用 `qwen3-vl-plus` 视觉大模型
- 自动识别图片格式（PNG/JPEG/GIF/WebP/BMP）

### 4. 安全与治理
- 敏感词过滤（可动态增删）
- 用户权限校验拦截器
- Re2 重读增强（提升模型推理准确度）
- 终端命令安全沙箱（危险命令黑名单）

---

## 技术栈

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.10 | 应用框架 |
| Java | 21 | 编程语言 |
| Spring AI | 1.1.2 | AI 模型集成框架 |
| DashScope SDK | 2.19.1 | 阿里云通义千问模型接入 |
| Spring AI Alibaba | 1.1.2.0 | 阿里云 AI 增强集成 |
| LangChain4j | 1.0.0-beta3 | 备选 AI 框架 |
| PGVector | - | PostgreSQL 向量数据库扩展 |
| MyBatis-Plus | 3.5.9 | ORM 框架 |
| Knife4j | 4.4.0 | OpenAPI 接口文档 |
| Hutool | 5.8.38 | Java 工具库 |
| Jsoup | 1.19.1 | HTML 解析 |
| iText | 9.1.0 | PDF 生成（含中文字体） |
| Kryo | 5.6.2 | 高性能序列化 |
| MCP Client | - | Model Context Protocol 客户端 |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4 | UI 框架（Composition API） |
| Vue Router | 4.3 | SPA 路由 |
| Vite | 5.4 | 构建工具 & 开发服务器 |
| Axios | 1.7 | HTTP 客户端 |
| Marked | 12.0 | Markdown 渲染 |

---

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/love_app/chat/sync` | 情感顾问同步对话 |
| GET | `/api/ai/love_app/chat/sse` | 情感顾问 SSE 流式对话 |
| GET | `/api/ai/love_app/chat/sse_emitter` | 情感顾问 SseEmitter 流式对话 |
| GET | `/api/ai/manus/chat` | 超级智能体对话（SSE 流式） |
| POST | `/api/image-chat/explain` | 图片理解（URL 方式） |
| POST | `/api/image-chat/upload-explain` | 图片理解（上传文件方式） |

---

## 快速开始

### 环境要求

- **JDK 21+**
- **Node.js 18+**
- **PostgreSQL 14+**（需安装 PGVector 扩展）
- **Maven 3.8+**
- **通义千问 API Key**（[申请地址](https://dashscope.console.aliyun.com/)）
- **Pexels API Key**（[申请地址](https://www.pexels.com/api/)，用于图片搜索）
- **博查 AI 搜索 API Key**（[申请地址](https://open.bocha.io/)）
- **高德地图 API Key**（[申请地址](https://lbs.amap.com/)，可选）

### 1. 数据库准备

```sql
-- 创建数据库
CREATE DATABASE "Love_Agent";

-- 安装 PGVector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 后端配置

编辑 `src/main/resources/application-local.yml`，填入你的 API Key：

```yaml
spring:
  ai:
    dashscope:
      api-key: your-dashscope-api-key      # 通义千问 API Key
      agent-key: your-agent-key             # 云端知识库 Key

ai:
  bocha:
    api:
      key: your-bocha-api-key               # 博查搜索 API Key
```

编辑 `src/main/resources/MCP-servers.json`，配置高德地图和图片搜索服务：

```json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx.cmd",
      "args": ["-y", "@amap/amap-maps-mcp-server"],
      "env": {
        "AMAP_MAPS_API_KEY": "your-amap-api-key"
      }
    },
    "image-search-mcp-server": {
      "command": "java",
      "args": ["-jar", "path/to/image-search-mcp-server.jar"]
    }
  }
}
```

### 3. 启动后端

```bash
cd AI_Agent
./mvnw spring-boot:run
```

后端运行在 `http://localhost:8123/api`，接口文档地址 `http://localhost:8123/api/doc.html`。

### 4. 启动前端

```bash
cd AI_Agent_Fronted
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，自动代理 API 请求到后端。

---

## 智能体架构

项目实现了一套完整的智能体框架，采用分层继承设计：

```
BaseAgent (基础智能体)
  ├── 状态管理（IDLE → RUNNING → FINISHED / ERROR）
  ├── SSE 实时推送
  └── 异步执行（CompletableFuture）
      │
      └── ReActAgent (推理-行动智能体)
            ├── think() - 推理：分析当前状态，决定下一步
            └── act()  - 行动：执行推理结果
                  │
                  └── ToolCallAgent (工具调用智能体)
                        ├── think() - 携带工具定义请求模型
                        └── act()  - 执行模型选择的工具
                              │
                              └── HeManus (超级智能体)
                                    ├── 最大 20 步自主执行
                                    └── 注册全部 7 类工具
```

---

## 对话拦截器链

每次 AI 对话请求都会经过以下拦截器链处理：

```
请求 → PermissionAdvisor → BannedWordAdvisor → MessageChatMemoryAdvisor → My_loggerAdvisor → AI 模型
                                                                                              ↓
响应 ← PermissionAdvisor ← BannedWordAdvisor ← MessageChatMemoryAdvisor ← My_loggerAdvisor ← AI 模型
```

---

## 项目亮点

1. **多种 AI 框架集成**：同时集成了 Spring AI、DashScope SDK、LangChain4j，展示了不同的 AI 接入方式
2. **多策略 RAG**：实现了从本地内存到云端知识库的四种 RAG 方案，覆盖不同场景需求
3. **ReAct 智能体**：基于 Reason-Act 模式的自主智能体，能够独立规划并执行复杂任务
4. **MCP 协议**：通过 Model Context Protocol 实现工具的标准化接入，支持热插拔扩展
5. **全链路流式输出**：从后端模型调用到前端展示，全链路 SSE 实时流式传输
6. **安全治理**：敏感词过滤、权限校验、命令沙箱等多层安全防护

---

## 许可证

本项目仅供学习和练习使用。
