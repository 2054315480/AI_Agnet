# 界面设计说明（v6.0 案头）

## 为什么改

旧版是典型的"AI 产品"视觉：通用蓝主色、玻璃拟态、发光阴影、大面积渐变，加上"AI Agent / 智能体"这类自我描述。功能没问题，但看起来像模板生成的 demo，缺少"这是给客服人员天天用的工具"的感觉。

这一版定的方向是 **案头（Ink & Paper Workbench）**：像一份摊在桌上的工单和便签，而不是一个科幻控制台。

## 设计系统

| 维度 | 做法 | 原因 |
| --- | --- | --- |
| 主色 | 青瓷 `#5E948A`（深色）/ `#3F6F66`（浅色） | 冷蓝太"科技感"，青瓷更接近办公用品，且和暖色底不打架 |
| 辅助色 | 黄铜 `#C2A36B`，只用于"转人工/需确认" | 一个颜色只承担一种语义，避免满屏彩色 |
| 背景 | 深色 = 暖墨 `#0B0D0C`；浅色 = 纸 `#F6F4EE` | 比纯黑/纯白柔和，长时间看不刺眼 |
| 文字 | 暖纸白 / 墨黑，次级文字降低不透明度 | 层级靠明度差，不靠颜色跳变 |
| 字体 | 正文系统字体栈；标题与数字用衬线 | 衬线标题是"出版物/工单"的语感，能立刻拉开和 AI 模板的差距；同时去掉 Google Fonts 外链 |
| 分隔 | 全部用 1px 发丝线 `--rule` | 代替阴影和卡片堆叠，更像纸质文档 |
| 圆角 | 10 / 7 / 5px（原来是 12 / 16 / 24） | 收敛"圆滚滚"的按钮感 |
| 阴影 | 只保留极轻的投影，取消所有 glow | 发光是最典型的"AI 味"来源之一 |
| 底纹 | 极低透明度的噪点 + 极轻的环境光 | 去掉纯色塑料感，但不干扰阅读 |

## 页面改动

### 登录页
左栏做成"书封"：线框标识 + 衬线标题 + 黄铜色小标签（知识库 / 对话 / 复盘），右栏是表单卡片。文案从"AI Agent 智能体平台"改成"客服工作台"，副标题说明它到底怎么工作："先查知识库，再回答。每一条答复都能追溯到出处。"

### 空态（欢迎页）
不再自我介绍，直接给下一步：标题"今天处理哪一类咨询？"，下面按业务分成 4 组高频问题（订单物流 / 售后保修 / 政策资费 / 产品选购），每条都是可以直接发问的真实句子，点击即发送。分组之间有发丝线，hover 时左侧细线展开。

### 对话区
- 头像改成排版式方形字块（"我" / "客"），去掉卡通 SVG
- 助手消息有消息头："客服工作台 + 时间"，读起来像记录而不是聊天
- 回答用浅底 + 发丝线的"纸页"面板；用户消息用墨块
- 处理过程改成运行日志样式（等宽小标签 + 左侧竖线）
- 出处改成脚注（上分隔线 + 小 chip）
- 推荐追问改成行式列表，不是一排按钮
- 流式输出时用方块光标闪烁

### 输入区
便签式容器，焦点态由外层容器统一表达（避免 textarea 里再套一圈描边），右下角发送按钮在可发送时变为青瓷实心，底部弱提示键盘操作。

### 侧边栏
线性图标 + "客服工作台 / SERVICE DESK" 双行标识；新增会话搜索；"会话 / 工具"分区标签；选中项用左侧 2px 细线表示，而不是整块高亮。

### 分析页 / 批量测试 / FAQ 管理
硬编码颜色全部映射到 token；紫色图表色移除，图表改用 6 个语义色（青瓷、黄铜、苔绿、赭黄、砖红、灰）；KPI 数字用衬线 + 等宽数字。顺带修了 FAQ 管理页被内容宽度挤成 316px 窄列的布局 bug。

## 动效清单

| 动效 | 位置 | 时长/曲线 |
| --- | --- | --- |
| rise（上浮 + 淡入） | 欢迎页标题、分组、消息进场 | 280-420ms，cubic-bezier(0.16,1,0.3,1) |
| 左侧细线展开 | 欢迎页示例行 hover | 140ms |
| 顶部 1px 进度线 | 对话加载中 | 1.4s 循环 |
| 方块光标闪烁 | 流式输出 | 1s steps |
| 呼吸点 | 打字指示器 | 1.2s |
| 主题切换 | 全局背景/文字 | 320ms |

全部动效在 `prefers-reduced-motion: reduce` 下自动关闭。

## 去 AI 味的具体做法

1. **换掉自我描述**：不再说"AI Agent / 智能体"，改说"客服工作台"；不把内部实现（意图种类数量）写给用户看。
2. **换掉通用蓝**：蓝紫渐变 + 发光是 AI 模板最典型的组合。
3. **去掉装饰性动画**：只保留有信息含义的动效（加载、流式、进场）。
4. **排版代替装饰**：用衬线标题、发丝线、留白建立层级，而不是靠卡片阴影和图标。
5. **文案具体化**：示例问题写成用户真会说的话（"耳机左耳没声音了，还能保修吗？"），而不是"探索 AI 的无限可能"。

## 截图

见 `docs/screenshots/`：

- `00-login-dark.png`
- `01-chat-dark.png` / `02-chat-light.png`
- `03-analytics-dark.png`
- `04-faq-admin-dark.png`
- `05-mobile-dark.png`


## 图表（ECharts）

分析页的三张图从手写 div 条换成了 **ECharts 6**，按需引入（Bar / Line / Pie + Grid / Tooltip / Legend / Title + CanvasRenderer），并用一层薄封装隔离主题：

- `components/charts/chartTokens.js`：用 `getComputedStyle` 读取 CSS 变量，输出调色板与坐标轴/提示框样式，token 改了图表自动跟着变。
- `components/charts/BaseChart.vue`：负责 init / resize（ResizeObserver）/ 主题切换重建（MutationObserver 监听 `data-theme`）/ dispose。
- 三张图：
  1. **转人工与澄清占比**（环形图）：黄铜=转人工、赭黄=澄清、灰=其余，中心显示总会话数；
  2. **意图分布**（横向条形图）：语义色优先（转人工=黄铜、投诉=砖红、超范围=灰），其余用青瓷按排名递减透明度，避免"彩虹配色"；
  3. **会话趋势**（面积折线图）：按天聚合会话数，青瓷低透明度渐变。
- 单个接口失败只影响对应图表，显示"数据加载失败"，不白屏。

## 会话信息抽屉

对话页顶部加了一条 48px 的工具条（会话标题 + 消息数 + 「会话信息」按钮），右侧是 340px 的抽屉，宽屏下常驻并给对话区让位，窄屏下浮层 + 遮罩：

- **概要**：标题、会话 ID（可复制）、创建时间、最近活跃、提问/答复条数
- **状态**：机器人在线 / 已转人工、最近意图与置信度、澄清次数
- **引用来源**：聚合整段会话的出处并统计被引用次数，空态有说明
- **处理过程**：最近一轮的工具调用记录（类型 + 参数）
- **操作**：导出 Markdown、结束本次会话（调用 `POST /customer-service/session/{id}/close`）
- 支持 `?panel=info` 深链直接打开

## 主题三段式

从"深/浅二态"改成 **跟随系统 / 浅色 / 深色**：

- `composables/useTheme.js`：`mode` 持久化到 `ai-agent-theme-mode`，兼容旧 key `ai-agent-theme` 并自动迁移；`system` 模式监听 `matchMedia('(prefers-color-scheme: dark)')` 实时跟随；同步 `data-theme`、`color-scheme`、`meta[theme-color]`。
- `ThemeToggle.vue`：三段式分段控件（显示器 / 太阳 / 月亮图标 + 文案）。
- `index.html`：内联脚本在 CSS 生效前先定主题，避免首屏闪烁。

## 数据前提：管理员账号

后端鉴权已改为真实鉴权（`/admin/**`、`/analytics/**` 需要 `ROLE_ADMIN`）。历史种子数据里所有账号的 `role` 都是 `user`，需要手动提升：

```sql
UPDATE users SET role = 'admin' WHERE username = 'admin';
```

改完重新登录（token 里会带 `role=admin`）才能进 FAQ 管理与对话分析。


## 分析页的数据链路与刷新

**写入链路**（每轮对话结束都会执行，实测可用）：

```
POST /customer-service/chat
  -> CustomerServiceAgent 完成本轮
  -> onCompleteHook -> persistDialogData()
       1) conversations 不存在则创建（标题取用户第一句话，压缩空白并截断 20 字）
       2) conversation_messages 追加 user / assistant 两条
       3) dialog_analytics 按 conversation_id upsert（轮次、意图分布、转人工、澄清数、平均置信度）
```

**读取链路**：

```
GET /analytics/overview            -> KPI + 意图分布 + 转人工/澄清数
GET /analytics/intent-distribution -> 意图分布（图表）
GET /analytics/sessions            -> 会话列表（分页/排序/筛选）与趋势图数据源
```

响应头已经是 `Cache-Control: no-cache, no-store`，不存在浏览器缓存导致的"数据不更新"。

**之前看起来"不更新"的真实原因有两个**：

1. 所有账号的 `role` 都是 `user`，`/analytics/**` 需要 `ROLE_ADMIN`，请求全部 403 -> 页面只显示默认 0 和"数据加载失败"。
   已执行 `UPDATE users SET role='admin' WHERE username='admin';` 并在上文记录。
2. 分析页只在 `onMounted` 取一次数。页面开着就不会再拉，聊天产生的新数据要重新进入页面才看得到。

**现在的刷新策略**：

- 顶部有手动刷新按钮（转圈动画），旁边显示"更新于 HH:MM:SS"
- 每 30 秒自动静默刷新（不闪整页 loading）；标签页不可见或正在看会话回放弹窗时跳过
- 切回标签页（`visibilitychange` -> visible）立即刷新一次
- 组件卸载时清理定时器与事件监听


## 引用来源（sources）链路

之前会话信息抽屉里的"引用来源"一直是 0，原因不是检索没命中，而是**出处这条数据从来没被传出去**。现在补齐为三段：

```
检索工具返回 JSON（含 results[].source）
  -> CustomerServiceAgent.captureSources() 正则提取 source 去重
       - SSE 推 sources 事件（前端即时显示出处 chip）
       - 缓存到 currentSources
  -> 本轮结束 persistAssistantTurn() 把 {"items":[...]} 写入 conversation_messages.sources（jsonb）
  -> /conversations/{id} 读出后，抽屉与气泡上的出处 chip 都能显示
```

顺带修掉两个隐藏很深的问题：

1. **jsonb 写入失败**：MyBatis-Plus 的 `JacksonTypeHandler` 用 `setString` 传 varchar，写 jsonb 列直接报错
   （`字段 "sources" 的类型为 jsonb, 但表达式的类型为 character varying`）。
   新增 `config/JsonbTypeHandler`，改用 `setObject(i, json, Types.OTHER)`；`slots` 与 `sources` 都换成它。
2. **jsonb 读取为 null**：`ConversationService.getMessages` 走的是手写 `@Select`，不会应用实体上的 TypeHandler，
   改成 `selectList` 后才会走 MP 生成的 resultMap。

## 两段式落库

原来只在"本轮结束"时一次性写用户消息 + 助手消息 + 分析聚合，用户点"停止生成"或 SSE 断开时整轮都会丢。现在拆成两段：

| 时机 | 动作 |
| --- | --- |
| 请求进入（`runStream` 之前） | `persistUserTurn()`：建会话（标题取用户首句、绑定当前用户）+ 写 user 消息 |
| 本轮结束（完成回调） | `persistAssistantTurn()`：写 assistant 消息（意图/槽位/置信度/转人工/澄清/出处）+ upsert 分析聚合 |

这样即使中途断开，用户的问题也已经落库，分析页至少能看到这一轮。

## 演示账号与权限矩阵

| 账号 | 密码 | 角色 | 对话 | 会话信息抽屉 | 对话分析 | FAQ 管理 |
| --- | --- | --- | --- | --- | --- | --- |
| `admin_demo` | `Admin@2026` | admin | ✅ | ✅ | ✅ | ✅ |
| `user_demo` | `User@2026` | user | ✅ | ✅ | ⛔ 403（页面显示无权提示） | ⛔ 403 |

普通账号登录后侧边栏会隐藏"对话分析 / FAQ 管理"；直接访问 URL 时，分析页会显示
"当前账号没有分析权限（需要管理员角色）"并提供"重新登录"按钮，而不是笼统的"数据加载失败"。

## 已知限制

1. 图表目前是纯 CSS/HTML 实现，没有引入图表库；数据量大时建议换 ECharts 并复用同一套语义色。
2. 衬线字体依赖系统字体（Songti SC / Source Han Serif / SimSun），Windows 与 macOS 上字形略有差异。
3. 移动端只做了基础适配（侧边栏抽屉 + 单列布局），未做平板断点。
