# 云 RAG 接入故障原因与修复记录（阿里云百炼 · 恋爱大师）

记录在接入阿里云百炼云知识库「恋爱大师」（ID：jew5jcwf1s，业务空间ID：llm-zcec55uenauhjlaa）时遇到的故障现象、根因分析、修复方案与验证结果，便于后续团队排查与复用。

## 1. 背景与目标

- 目标方法：`LoveApp#doChatWithCloudRag`
- 顾问链配置类：`LoveAppRagCloudAdvisorConfig`
- 云端资源：
  - 业务空间ID：`llm-zcec55uenauhjlaa`
  - 知识库（索引）名称：`恋爱大师`
  - 知识库（索引）ID：`jew5jcwf1s`
- 运行环境：Spring Boot 3.5.x + Spring AI Alibaba DashScope SDK

期望效果：调用云 RAG 时，回答必须体现「恋爱大师」知识库的内容特征（如案例“老陈/老张/老王/老李/老孙”与课程链接）。

## 2. 故障现象

- 单测 `CloudRagVerificationTest` 运行初期报错：
  - `403 Forbidden Workspace.AccessDenied: "The workspace does not exist."`
- 后续修复过程中出现过的构建期异常：
  - `withWorkspaceId/withSpaceId` 方法不存在（SDK 版本不支持）
  - `withTopK/withMinScore/withPipelineName` 方法不存在（SDK 版本不支持）
  - `DashScopeApi` 构造期 `UnsupportedOperationException`（与 defaultHeaders 传递相关）

## 3. 日志特征

- 403 报错堆栈关键路径：
  ```
  com.alibaba.cloud.ai.dashscope.api.DashScopeApi.getPipelineIdByName
  com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever.retrieve
  org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.getDocumentsForQuery
  ```
  说明 SDK 内部会根据“管道/索引名称”解析 ID 并在指定工作空间检索。

- 构造期异常关键路径：
  ```
  java.lang.UnsupportedOperationException
  at org.springframework.util.MultiValueMapAdapter.addAll
  at org.springframework.web.reactive.function.client.DefaultWebClientBuilder.defaultHeaders
  at com.alibaba.cloud.ai.dashscope.api.DashScopeApi.<init>
  ```
  说明我们手动传递的 `defaultHeaders` 与 WebClient 默认头组合存在不可变集合 `addAll` 的兼容问题。

## 4. 根因分析

本次问题是多因素叠加造成，核心包含以下几类：

1) 工作空间授权维度不清晰 → 403
- 阿里云百炼对「业务空间」有明确的授权边界。若请求未携带空间所需的授权要素（例如平台侧要求在空间内绑定的 `API Key` 或配套的 `agentKey`），则解析管道/索引时会返回 `Workspace.AccessDenied`。
- 起初仅通过在代码里自定义各种 Workspace Header 名称进行尝试，由于服务端校验点并非简单 header 名称匹配或 SDK 会在内部处理授权，导致无效。

2) 手动拼装 `DashScopeApi` 引发的默认头兼容问题 → 运行期异常
- 我们自定义传入 `defaultHeaders`、`RestClient`、`WebClient` 等，在 SDK 构造过程中与其内部默认处理发生冲突（`MultiValueMapAdapter.addAll`），触发 `UnsupportedOperationException`。
- 这与 Spring/WebClient 某些版本在 `defaultHeaders` 合并时的不可变集合实现有关。

3) SDK 版本能力差异 → 编译失败
- `DashScopeDocumentRetrieverOptions` 在不同版本暴露的方法不同：
  - 无 `withWorkspaceId/withSpaceId`
  - 无 `withTopK/withMinScore/withScoreThreshold`
  - 无 `withPipelineName`
- 因此将工作空间、召回条数、阈值等“强注入”方案不可行，必须遵循当前 SDK 的最小能力。

4) 名称 vs ID 的索引解析策略
- SDK 内部会通过名称解析管道 ID（见 `getPipelineIdByName` 路径）。若控制台中的显示名与代码不一致或包含特殊字符，名称解析可能失败。
- 使用稳定的「索引ID」可避免命名变动带来的影响。

5) “别人代码很简洁”的原因
- 对方示例使用 `DashScopeApi.builder().apiKey(...).build()` 的最小实现，让 SDK 内部按规范去处理工作空间与授权校验（前提：控制台已将 API Key 与业务空间正确绑定）。
- 我们最初走了“手工注入 header/拦截器/自定义 RestClient/WebClient”的路径，在 SDK 版本与默认行为未知的情况下，反而增加了耦合与出错点。

## 5. 修复方案

遵循“最小可用原则”，将接入改为 SDK 官方推荐的最小实现：

- 顾问链配置改造（最终版）
  ```java
  // LoveAppRagCloudAdvisorConfig.java（已上线）
  DashScopeApi dashScopeApi = DashScopeApi.builder()
      .apiKey(dashScopeApiKey)
      .build();

  DocumentRetriever retriever = new DashScopeDocumentRetriever(
      dashScopeApi,
      DashScopeDocumentRetrieverOptions.builder()
          // 推荐使用稳定的索引ID，避免名称变更
          // .withIndexName("jew5jcwf1s")
          // 也可使用显示名「恋爱大师」
          .withIndexName("恋爱大师")
          .build()
  );

  return RetrievalAugmentationAdvisor.builder()
      .documentRetriever(retriever)
      .build();
  ```

- 配置项
  - `spring.ai.dashscope.api-key`：控制台确保该 Key 已绑定到业务空间 `llm-zcec55uenauhjlaa`。
  - 可选 `ai.dashscope.agent-key`：若平台侧要求空间级 `agentKey` 才能访问资源，请在空间内生成并与 `api-key` 绑定（我们已在 `application-local.yml` 写入，SDK 最小实现无需手动头注入）。

## 6. 验证结果

- 执行单测：`mvn -q -Dtest=CloudRagVerificationTest test`
- 现象：
  - 日志中 `My_loggerAdvisor` 打印的上下文包含知识库案例（如“老陈/老王/老张/老孙”等）与课程链接（gitee.com）。
  - `CloudRagVerificationTest` 中“是否包含知识库特征内容: true”。
  - `doChatWithCloudRag` 返回内容结构化、带案例与课程链接，符合系统提示词要求。
- 结论：云 RAG 已命中「恋爱大师」知识库。

## 7. 常见坑与规避

- 403 Workspace.AccessDenied：
  - 确认 `API Key` 是否在控制台绑定到目标空间（而非全局或其它空间）；
  - 若平台要求 `agentKey`，需在**同一空间**下生成并与 `API Key` 绑定；
  - 避免在代码里随意组合 Workspace Header 名称，交由 SDK 处理。

- `DashScopeDocumentRetrieverOptions` 方法缺失：
  - 不同版本支持度不同，避免依赖不可用的 builder 方法；
  - 可在 IDE 通过代码补全确认 API；若需要自定义 `topK/阈值`，升级 SDK 后再行配置。

- 名称解析失败：
  - 推荐使用索引ID（如 `jew5jcwf1s`）而非中文显示名，避免后期更名影响。

- 手工拼装 `DashScopeApi`：
  - 非必要不要自定义 `defaultHeaders`、`WebClient`、`RestClient`，以免与 SDK 内部默认行为冲突。

## 8. 最终配置清单（局部）

- `src/main/resources/application-local.yml`
  ```yaml
  spring:
    ai:
      dashscope:
        api-key: <在控制台已绑定到 llm-zcec55uenauhjlaa 的 API Key>
  ai:
    dashscope:
      agent-key: <如平台要求空间 agentKey，这里配置；否则可留空>
  ```

- `LoveAppRagCloudAdvisorConfig`（最小实现，已生效）

## 9. 运维与回归

- 回归用例：`CloudRagVerificationTest`（包含三类：基础可用、特征命中、云/本地对比）
- 回归标准：
  - 云 RAG 回答非空；
  - 回答中包含知识库特征内容（案例/课程链接/“根据专业建议”等标识）；
  - 日志中出现召回内容片段。
- 常用指令：
  - 编译：`mvn -q -DskipTests compile`
  - 单测：`mvn -q -Dtest=CloudRagVerificationTest test`

## 10. 为什么最终能打通？

- 使用 SDK 最小构造（`DashScopeApi.builder()`）→ 避免手工 header/拦截器与内部默认行为冲突；
- 在控制台侧将 `API Key` 与业务空间绑定（必要时配置 `agentKey`）→ 服务端能够识别工作空间上下文；
- 索引采用稳定 ID 或与控制台一致的名称 → `getPipelineIdByName` 能正确解析管道 ID；
- 因此云检索成功命中知识库，回答呈现知识库特征，满足业务需求。

