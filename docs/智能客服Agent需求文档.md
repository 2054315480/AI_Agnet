# 智能客服问答与多轮对话 Agent — 需求文档

> **版本:** v1.0 | **日期:** 2026-04-22 | **项目:** 基于 AI Agent 项目改造

---

## 一、赛题概述

将现有 AI Agent 平台改造为智能客服 Agent，具备意图识别、知识库检索、多轮对话、主动澄清、拒识转人工等能力，并提供 Web UI 及对话分析功能。

---

## 二、评分标准与量化指标

### 总分 100 分，六维评分体系

| 维度 | 分值 | 评审方式 | 核心关注点 |
|------|------|----------|------------|
| 指标达成 | 30 分 | 自动化脚本评测 | 每题对应具体量化项 |
| 代码工程质量 | 20 分 | 人工评审 | 代码结构、可读性、规范性、CI/CD |
| 架构合理性 | 15 分 | 人工评审 | 组件划分、可扩展性、技术选型 |
| 创新性 | 15 分 | 人工评审 | 方法创新、工程巧思、亮点 |
| 可运行与可复现 | 10 分 | 实际验证 | 一键启动、环境无关、结果可复现 |
| 文档与演示 | 10 分 | 人工评审 | 架构文档、Demo 视频、README |

---

## 三、功能需求拆解

### 3.1 意图识别与槽位提取（指标达成 30 分核心）

#### 3.1.1 功能描述
准确识别用户意图，提取关键信息（订单号、产品名、问题类型）。

#### 3.1.2 量化指标（自动化脚本评测项）

| 指标 | 目标值 | 评测方式 |
|------|--------|----------|
| 意图识别准确率 | ≥ 90% | 标注测试集对比，正确意图数 / 总测试用例数 |
| 槽位提取 F1 值 | ≥ 85% | 精确匹配提取结果与标注槽位，计算 F1 |
| 订单号识别率 | ≥ 95% | 正则 + 模型双通道，覆盖多种格式（纯数字、带前缀等） |
| 产品名识别率 | ≥ 85% | 模糊匹配知识库产品列表 |
| 平均响应时间 | ≤ 2s | 从请求到意图+槽位返回的时间 |

#### 3.1.3 意图体系设计

```
核心意图枚举：
├── ORDER_QUERY        — 订单查询（物流、状态）
├── ORDER_CANCEL       — 取消订单
├── ORDER_REFUND       — 退款申请
├── PRODUCT_INFO       — 产品咨询（参数、功能、对比）
├── PRODUCT_RECOMMEND  — 产品推荐
├── COMPLAINT          — 投诉建议
├── AFTER_SALE         — 售后服务（维修、换货）
├── POLICY_QUERY       — 政策查询（退换货政策、保修等）
├── CHITCHAT           — 闲聊（寒暄、感谢）
├── REQUEST_HUMAN      — 转人工
├── CLARIFICATION      — 用户澄清（回答系统追问）
└── OUT_OF_SCOPE       — 超出范围
```

#### 3.1.4 槽位 Schema 定义

```json
{
  "ORDER_QUERY": {
    "required": ["order_id"],
    "optional": ["query_type"]
  },
  "ORDER_REFUND": {
    "required": ["order_id", "refund_reason"],
    "optional": ["refund_amount", "product_name"]
  },
  "PRODUCT_INFO": {
    "required": ["product_name"],
    "optional": ["info_type", "compare_with"]
  },
  "COMPLAINT": {
    "required": ["issue_type"],
    "optional": ["order_id", "product_name", "issue_detail"]
  }
}
```

#### 3.1.5 实现方案

- **技术路线：** LLM 结构化输出（Spring AI `BeanOutputConverter`）+ 正则预筛
- **双通道策略：** 格式固定的字段（订单号、手机号）用正则先提取，语义字段用 LLM 提取
- **Advisor 集成：** 新增 `IntentRecognitionAdvisor`（优先级 -90），在请求链中自动识别意图并注入上下文

#### 3.1.6 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| Spring AI ChatClient | ✅ 复用 | 调用 LLM 做意图分类 |
| jsonschema-generator | ✅ 复用 | 结构化输出 |
| Advisor 链模式 | ✅ 复用 | 新 Advisor 插入链中 |
| IntentClassifier | 🆕 新建 | 意图分类核心服务 |
| IntentConfig | 🆕 新建 | 意图与槽位的配置定义 |

---

### 3.2 知识库检索问答（带出处）

#### 3.2.1 功能描述
基于评委提供的知识库（FAQ + 产品文档）回答用户问题，答案须标注出处。

#### 3.2.2 量化指标

| 指标 | 目标值 | 评测方式 |
|------|--------|----------|
| 检索准确率 (Recall@5) | ≥ 85% | Top-5 检索结果是否包含正确文档 |
| 答案准确率 | ≥ 80% | 答案与标注答案的语义相似度 |
| 出处标注率 | 100% | 每个回答必须包含出处引用 |
| 无幻觉率 | ≥ 95% | 答案内容不得超出知识库范围 |

#### 3.2.3 实现方案

- **知识库加载：** 改造 `LoveAppDocumentLoader` → 通用 `KnowledgeBaseLoader`，支持 FAQ 表格、产品文档（Markdown/PDF/Word）
- **FAQ 特殊处理：** FAQ 项作为独立文档块，Q 作为 metadata 便于精确匹配
- **出处追踪：** 检索结果携带 `source_file`、`chunk_index`、`page_number` 等元信息，Prompt 强制要求引用格式 `[来源：文档名#段落]`
- **混合检索：** 复用 `HybridDocumentRetriever`，向量检索 + 关键词检索融合

#### 3.2.4 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| RAG 管道 | ✅ 复用 | 文档加载→分块→向量化→检索 |
| HybridDocumentRetriever | ✅ 复用 | 混合检索策略 |
| PgVectorStore | ✅ 复用 | 向量存储 |
| KnowledgeBaseLoader | 🔧 改造 | 从 LoveApp 定制→通用加载器 |
| 出处标注 Prompt | 🆕 新建 | 强制引用出处的系统 Prompt |
| SourceTracker | 🆕 新建 | 追踪检索结果来源并注入响应 |

---

### 3.3 多轮对话管理

#### 3.3.1 功能描述
支持上下文跟踪，正确处理指代消解、省略恢复、话题切换。

#### 3.3.2 量化指标

| 指标 | 目标值 | 评测方式 |
|------|--------|----------|
| 指代消解准确率 | ≥ 85% | "它"、"那个"等指代词能否正确还原 |
| 上下文继承率 | ≥ 90% | 省略信息能否从上文自动补全 |
| 话题切换检测率 | ≥ 80% | 用户转换话题时系统能否正确识别 |
| 多轮一致性 | ≥ 90% | 同一会话中信息不矛盾 |

#### 3.3.3 对话状态模型

```
DialogState:
├── session_id          — 会话 ID
├── current_intent      — 当前意图
├── filled_slots        — 已填充槽位 {slot_name: value}
├── dialog_phase        — 对话阶段
│   ├── GREETING        — 寒暄
│   ├── INTENT_COLLECT  — 意图收集
│   ├── SLOT_FILLING    — 槽位填充
│   ├── RETRIEVING      — 知识检索
│   ├── ANSWERING       — 回答中
│   ├── CLARIFYING      — 澄清中
│   ├── HANDOFF         — 转人工
│   └── CLOSED          — 已结束
├── turn_count          — 当前轮次
├── history_intents     — 历史意图列表（话题切换追踪）
├── unresolved_refs     — 未消解的指代
└── confidence_score    — 当前状态置信度
```

#### 3.3.4 实现方案

- **状态管理器：** `DialogStateManager` 服务，维护每个会话的结构化对话状态，存储在 Redis 或 PostgreSQL
- **指代消解：** LLM + 上下文窗口，将指代词替换为具体实体后再传入意图识别
- **话题切换：** 意图分类时对比上一轮 intent，若差异超过阈值则判定为话题切换，保存当前状态快照
- **省略恢复：** 检查当前输入是否缺少主语/宾语，从 `DialogState.filled_slots` 中补全

#### 3.3.5 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| ChatMemory | ✅ 复用 | 对话历史存储 |
| MessageChatMemoryAdvisor | ✅ 复用 | 记忆注入 |
| DialogStateManager | 🆕 新建 | 结构化状态管理 |
| DialogState 实体 | 🆕 新建 | 状态数据模型 |
| ContextResolutionAdvisor | 🆕 新建 | 指代消解 Advisor |

---

### 3.4 主动澄清

#### 3.4.1 功能描述
信息不足时主动反问，而非猜测作答。

#### 3.4.2 量化指标

| 指标 | 目标值 | 评测方式 |
|------|--------|----------|
| 澄清触发准确率 | ≥ 85% | 应澄清时澄清 / 不应澄清时不误触发 |
| 澄清问题质量 | ≥ 80% | 追问是否针对性且有助用户补充信息 |
| 不猜测率 | ≥ 95% | 缺少必填信息时不得直接给出答案 |

#### 3.4.3 澄清策略

```
槽位完整性检查流程：
  intent 识别完成
    → 检查 required_slots
      → 缺少必填槽位 → 生成针对性追问
        → "请问您的订单号是多少？"
      → 必填槽位已满 → 检查置信度
        → 置信度低 → 提供选项确认
          → "您是想查询订单状态还是申请退款？"
        → 置信度高 → 进入知识检索
      → 意图模糊（多候选） → 让用户选择
        → "我理解您可能在问以下问题：1)... 2)... 请问是哪个？"
```

#### 3.4.4 实现方案

- **槽位完整性检查器：** `SlotFillingChecker`，根据意图定义的 `required_slots` 检查当前状态
- **澄清问题生成器：** 根据缺失的槽位类型，使用 LLM 生成自然语言的追问
- **Advisor 集成：** `ClarificationAdvisor`（优先级 -60），在意图识别之后、知识检索之前拦截

#### 3.4.5 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| ReActAgent 循环 | ✅ 复用 | think→act 中插入检查 |
| SlotFillingChecker | 🆕 新建 | 槽位完整性检查 |
| ClarificationAdvisor | 🆕 新建 | 澄清拦截 Advisor |
| 意图-槽位配置 | 🆕 新建 | YAML/JSON 定义每个意图的槽位需求 |

---

### 3.5 拒识与转人工

#### 3.5.1 功能描述
超出知识库范围的问题明确告知并建议转人工，不编造答案。

#### 3.5.2 量化指标

| 指标 | 目标值 | 评测方式 |
|------|--------|----------|
| 拒识准确率 | ≥ 90% | 超范围问题能否正确拒识 |
| 无幻觉率 | ≥ 95% | 不得编造知识库中不存在的信息 |
| 转人工触发率 | 适中 | 过低=覆盖不足，过高=体验差 |
| 转人工建议合理性 | ≥ 85% | 转人工时机是否恰当 |

#### 3.5.3 转人工触发条件

```
触发转人工的条件（满足任一）：
1. 连续 2 轮意图识别为 OUT_OF_SCOPE
2. 知识库检索相似度 < 0.6 且无法通过澄清补全
3. 用户明确表达转人工意图（"找人工"、"转人工"、"找客服"）
4. 用户情绪检测为负面（可选增强）
5. 对话轮次 > 15 仍未解决问题
```

#### 3.5.4 实现方案

- **范围检测器：** `OutOfScopeDetector`，结合意图分类结果和知识库检索相似度判断
- **置信度阈值：** 可配置的置信度阈值，低于阈值触发拒识
- **拒识 Prompt：** 严格的系统 Prompt，明确禁止编造，要求模型在不确定时声明不确定
- **转人工状态：** 触发后 `DialogState` 切换为 `HANDOFF`，前端显示醒目提示

#### 3.5.5 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| BannedWordAdvisor | ✅ 参考模式 | 拦截器模式可参考 |
| OutOfScopeDetector | 🆕 新建 | 超范围检测服务 |
| 转人工 Prompt | 🆕 新建 | 拒识+转人工的系统指令 |
| ConfidenceThreshold 配置 | 🆕 新建 | 可配置的置信度参数 |

---

### 3.6 Web UI（交互式测试界面）

#### 3.6.1 功能描述
提供 Web UI 供评委进行交互式测试。

#### 3.6.2 界面模块

```
页面结构：
├── 客服对话页面（改造现有 ChatView）
│   ├── 对话输入区
│   ├── 消息展示区
│   │   ├── 用户消息
│   │   ├── AI 回复（含出处标注、Markdown 渲染）
│   │   ├── 意图标签（显示每轮识别的意图）
│   │   ├── 澄清卡片（可点击选项）
│   │   └── 转人工提示（醒目警告框）
│   └── 对话状态指示器（当前阶段）
│
├── 会话分析仪表盘（新页面）
│   ├── 总览卡片（总会话数、平均轮次、转人工率）
│   ├── 意图分布饼图
│   ├── 转人工率趋势
│   ├── 平均对话轮次
│   └── 澄清触发统计
│
└── 对话日志页面（新页面）
    ├── 会话列表（可搜索、筛选）
    ├── 单会话详情（完整对话回放）
    └── 导出按钮（CSV/Excel）
```

#### 3.6.3 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| Vue 3 前端架构 | ✅ 复用 | 整体框架 |
| SSE 流式 | ✅ 复用 | fetchSSE |
| JWT 认证 | ✅ 复用 | 用户系统 |
| ChatView.vue | 🔧 改造 | 增加意图/出处/澄清/转人工展示 |
| Sidebar.vue | 🔧 改造 | 角色切换→客服模式 |
| AnalyticsView.vue | 🆕 新建 | 统计仪表盘页面 |
| ExportView.vue | 🆕 新建 | 日志导出页面 |
| IntentTag.vue | 🆕 新建 | 意图标签组件 |
| ClarificationCard.vue | 🆕 新建 | 澄清选项卡片组件 |
| HandoffAlert.vue | 🆕 新建 | 转人工提示组件 |

---

### 3.7 对话日志导出与会话分析

#### 3.7.1 功能描述
支持对话日志导出，含意图分布、转人工率、平均轮次等统计分析。

#### 3.7.2 量化指标

| 指标 | 说明 |
|------|------|
| 意图分布统计 | 按意图类型统计出现次数和占比 |
| 转人工率 | 触发转人工的会话数 / 总会话数 |
| 平均对话轮次 | 所有会话的平均消息轮数 |
| 澄清触发率 | 触发澄清的轮次 / 总轮次 |
| 无答案率 | 知识库未命中的查询占比 |
| 单会话导出 | 导出某个会话的完整对话（CSV/Excel） |
| 全量导出 | 按时间/意图等条件筛选后批量导出 |

#### 3.7.3 数据库扩展

```sql
-- 扩展 conversation_messages 表
ALTER TABLE conversation_messages ADD COLUMN intent VARCHAR(50);
ALTER TABLE conversation_messages ADD COLUMN slots JSONB;
ALTER TABLE conversation_messages ADD COLUMN confidence FLOAT;
ALTER TABLE conversation_messages ADD COLUMN is_handoff BOOLEAN DEFAULT FALSE;
ALTER TABLE conversation_messages ADD COLUMN is_clarification BOOLEAN DEFAULT FALSE;
ALTER TABLE conversation_messages ADD COLUMN sources JSONB;  -- 知识库出处

-- 新建会话分析表（可选，也可实时从 messages 聚合）
CREATE TABLE dialog_analytics (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) REFERENCES conversations(id),
    total_turns     INT,
    intent_distribution JSONB,
    handoff_triggered BOOLEAN DEFAULT FALSE,
    clarification_count INT DEFAULT 0,
    avg_confidence  FLOAT,
    resolved        BOOLEAN,
    created_at      TIMESTAMP
);
```

#### 3.7.4 实现方案

- **分析 API：** 新建 `AnalyticsController`，提供聚合查询接口
- **导出服务：** `ExportService`，支持 CSV 和 Excel 格式导出
- **定时聚合：** 可选，定时任务将原始消息聚合为 `dialog_analytics` 表

#### 3.7.5 与现有项目的关系

| 项目 | 状态 | 说明 |
|------|------|------|
| ConversationService | ✅ 复用 | 会话 CRUD |
| ConversationMessage | 🔧 改造 | 扩展字段 |
| AnalyticsController | 🆕 新建 | 分析统计 API |
| AnalyticsService | 🆕 新建 | 统计计算逻辑 |
| ExportService | 🆕 新建 | 日志导出功能 |
| dialog_analytics 表 | 🆕 新建 | 分析数据表 |

---

## 四、评分维度映射

### 4.1 指标达成（30 分）— 自动化脚本评测

| 评测项 | 量化指标 | 目标值 |
|--------|----------|--------|
| 意图识别准确率 | 正确意图数 / 总用例数 | ≥ 90% |
| 槽位提取 F1 | Precision & Recall 综合 | ≥ 85% |
| 知识库答案准确率 | 语义相似度评分 | ≥ 80% |
| 出处标注完整率 | 有出处的回答 / 总回答 | 100% |
| 指代消解准确率 | 正确消解 / 总指代 | ≥ 85% |
| 主动澄清触发率 | 正确澄清 / 应澄清轮次 | ≥ 85% |
| 拒识准确率 | 正确拒识 / 超范围问题 | ≥ 90% |
| 无幻觉率 | 无编造 / 总回答 | ≥ 95% |
| 多轮一致性 | 信息无矛盾 / 总会话 | ≥ 90% |
| 响应时间 | 平均端到端延迟 | ≤ 3s |

### 4.2 代码工程质量（20 分）

| 要求 | 实现方式 |
|------|----------|
| 代码结构 | 分层清晰（controller/service/agent/tools/model），包职责单一 |
| 可读性 | 命名规范、方法职责单一、适当注释 |
| 规范性 | 统一异常处理、参数校验、API 文档（Knife4j） |
| CI/CD | GitHub Actions：自动构建、测试、Docker 镜像发布 |
| 测试覆盖 | 核心逻辑单元测试 + 意图/槽位集成测试 |

### 4.3 架构合理性（15 分）

| 要求 | 实现方式 |
|------|----------|
| 组件划分 | Agent / Service / Advisor / Tool / RAG 各层职责清晰 |
| 可扩展性 | 意图/槽位配置化，工具热插拔，Advisor 链可编排 |
| 技术选型 | Spring AI 主导 + PgVector 向量库 + 结构化输出 |
| 对话管理 | 状态机模型，DialogState 持久化，支持多后端 |

### 4.4 创新性（15 分）

| 创新点 | 说明 |
|--------|------|
| 双通道意图识别 | 正则预筛 + LLM 语义理解，兼顾速度和准确度 |
| 槽位渐进填充 | 多轮逐步收集槽位，非一次性要求用户提供所有信息 |
| 知识库出处溯源 | 检索结果带完整来源链路，答案可验证 |
| 情绪感知（增强） | 检测用户情绪变化，情绪恶化时主动转人工 |
| 对话回放与分析 | 不仅是日志导出，还能复盘对话流程、分析瓶颈 |
| 置信度驱动决策 | 置信度 < 阈值 → 澄清；连续低置信 → 转人工；阈值可配置 |

### 4.5 可运行与可复现（10 分）

| 要求 | 实现方式 |
|------|----------|
| 一键启动 | `docker-compose up` 启动全部服务 |
| 环境无关 | Docker 多阶段构建，无本地依赖 |
| 数据初始化 | 知识库自动加载脚本（SQL + 文档导入） |
| 配置外置 | `.env.example` 模板，环境变量驱动 |
| 结果可复现 | 固定 seed + 确定性测试用例 |

### 4.6 文档与演示（10 分）

| 要求 | 实现方式 |
|------|----------|
| README | 项目介绍、架构图、快速启动、API 说明 |
| 架构文档 | 系统架构图、数据流图、对话状态机图 |
| Demo 视频 | 录制 3-5 分钟功能演示视频 |
| API 文档 | Knife4j 自动生成 + 补充说明 |

---

## 五、整体实现规划

### 阶段一：基础架构搭建（预计 2-3 天）

**目标：** 改造项目骨架，建立客服 Agent 核心框架

| 序号 | 任务 | 优先级 | 说明 |
|------|------|--------|------|
| 1.1 | 创建客服 Agent 包结构 | P0 | `agent/customer_service/` 下组织核心类 |
| 1.2 | 定义意图枚举与槽位 Schema | P0 | `CustomerIntent` 枚举 + `SlotDefinition` 配置 |
| 1.3 | 实现 IntentClassifier | P0 | LLM 结构化输出 + 正则预筛 |
| 1.4 | 实现 DialogState 模型 | P0 | 状态数据类 + 持久化 |
| 1.5 | 实现 DialogStateManager | P0 | 状态 CRUD、轮次追踪 |
| 1.6 | 改造 HeManus → CustomerServiceAgent | P0 | 继承 ToolCallAgent，集成意图识别 |

### 阶段二：核心对话能力（预计 2-3 天）

**目标：** 实现意图识别→槽位填充→知识检索→回答的完整链路

| 序号 | 任务 | 优先级 | 说明 |
|------|------|--------|------|
| 2.1 | 改造知识库加载器 | P0 | 支持客服 FAQ + 产品文档格式 |
| 2.2 | 实现出处追踪机制 | P0 | 检索结果携带来源，Prompt 强制引用 |
| 2.3 | 实现 SlotFillingChecker | P0 | 检查槽位完整性 |
| 2.4 | 实现 ClarificationAdvisor | P0 | 信息不足时生成追问 |
| 2.5 | 实现 ContextResolutionAdvisor | P1 | 指代消解和省略恢复 |
| 2.6 | 实现 OutOfScopeDetector | P0 | 超范围检测 + 转人工触发 |
| 2.7 | 编写客服系统 Prompt | P0 | 严格拒识、出处标注、转人工指令 |

### 阶段三：数据持久化与分析（预计 1-2 天）

**目标：** 扩展数据库，实现统计分析和日志导出

| 序号 | 任务 | 优先级 | 说明 |
|------|------|--------|------|
| 3.1 | 扩展 conversation_messages 表 | P0 | 新增 intent/slots/confidence 等字段 |
| 3.2 | 创建 dialog_analytics 表 | P1 | 聚合统计数据表 |
| 3.3 | 实现 AnalyticsService | P0 | 统计计算逻辑 |
| 3.4 | 实现 AnalyticsController | P0 | 统计 API 端点 |
| 3.5 | 实现 ExportService | P0 | CSV/Excel 导出 |

### 阶段四：前端改造（预计 2-3 天）

**目标：** 改造 Vue 前端为客服交互界面 + 分析仪表盘

| 序号 | 任务 | 优先级 | 说明 |
|------|------|--------|------|
| 4.1 | 改造 Sidebar | P0 | 去掉恋爱大师/超级智能体，统一客服入口 |
| 4.2 | 改造 ChatView | P0 | 增加意图标签、出处标注、澄清卡片 |
| 4.3 | 新建 HandoffAlert 组件 | P0 | 转人工提示 UI |
| 4.4 | 新建 AnalyticsView | P1 | 统计仪表盘页面 |
| 4.5 | 新建导出页面 | P1 | 日志浏览与导出 |
| 4.6 | 改造 SSE 数据格式 | P0 | 携带意图/槽位/出处等元信息 |

### 阶段五：优化与收尾（预计 1-2 天）

**目标：** 性能优化、测试、文档、部署

| 序号 | 任务 | 优先级 | 说明 |
|------|------|--------|------|
| 5.1 | 编写自动化评测脚本 | P0 | 测试意图识别、槽位提取等量化指标 |
| 5.2 | 编写单元测试 | P1 | 核心逻辑覆盖率 |
| 5.3 | 配置 CI/CD | P1 | GitHub Actions 自动构建+测试 |
| 5.4 | 完善 Docker 部署 | P0 | 一键启动，数据自动初始化 |
| 5.5 | 编写 README 和架构文档 | P0 | 项目文档 |
| 5.6 | 录制 Demo 视频 | P1 | 功能演示 |

---

## 六、技术依赖总结

### 可直接复用（约 50-60%）

- Spring AI 框架 + DashScope 集成
- RAG 管道（文档加载→分块→向量化→混合检索）
- Agent 层次结构（BaseAgent → ReActAgent → ToolCallAgent）
- Advisor 链模式
- ChatMemory 三种后端
- SSE 流式推送
- Vue 3 前端架构 + JWT 认证
- Docker 部署方案

### 需要改造

- `LoveAppDocumentLoader` → 通用知识库加载器
- `HeManus` → `CustomerServiceAgent`
- `ChatView.vue` → 客服对话界面
- `conversation_messages` 表结构扩展
- 系统Prompt → 客服专用 Prompt

### 需要新建

- `IntentClassifier` — 意图识别服务
- `DialogStateManager` — 对话状态管理
- `SlotFillingChecker` — 槽位完整性检查
- `ClarificationAdvisor` — 主动澄清 Advisor
- `ContextResolutionAdvisor` — 指代消解 Advisor
- `OutOfScopeDetector` — 超范围检测
- `AnalyticsService/Controller` — 统计分析
- `ExportService` — 日志导出
- 前端分析仪表盘和导出页面
