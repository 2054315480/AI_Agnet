# 图片搜索 MCP 服务器

基于 Spring AI MCP 的图片搜索服务，使用 Pexels API 提供图片搜索功能。

## 功能特性

- 📷 根据关键词搜索高清图片
- 🎨 支持中文搜索（Pexels API 自动翻译）
- 📊 返回图片详细信息（尺寸、摄影师、多种分辨率链接）
- 🔌 支持 MCP STDIO 模式（标准输入输出）

## 快速开始

### 1. 构建 JAR 包

```bash
cd image-search-mcp-server
mvn clean package
```

### 2. 配置 Claude Desktop

编辑 Claude Desktop 配置文件：

**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

添加以下配置：

```json
{
  "mcpServers": {
    "image-search": {
      "command": "java",
      "args": [
        "-jar",
        "D:\\AI_Agent\\image-search-mcp-server\\target\\image-search-mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### 3. 重启 Claude Desktop

重启 Claude Desktop 后，图片搜索工具将自动可用。

## 配置说明

### Pexels API Key

在 `application.yml` 中配置你的 Pexels API Key：

```yaml
pexels:
  api-key: YOUR_PEXELS_API_KEY
```

**获取 Pexels API Key：**
1. 访问 [Pexels API](https://www.pexels.com/api/)
2. 注册账号并申请 API Key
3. 将 Key 配置到 `application.yml` 中

## MCP 工具说明

### searchImage

根据关键词搜索图片。

**参数：**
- `query`（必需）：搜索关键词，例如：nature、cat、city
- `perPage`（可选）：返回图片数量，默认5张，最大80张

**返回示例：**

```
共找到 1250 张与 "nature" 相关的图片，展示前 5 张：

图片1:
  描述: 美丽的山脉风景
  摄影师: John Doe
  尺寸: 5472x3648
  主色调: #5D7A5D
  原图: https://images.pexels.com/photos/...
  大图: https://images.pexels.com/photos/...
  中图: https://images.pexels.com/photos/...
  缩略图: https://images.pexels.com/photos/...
  详情页: https://www.pexels.com/photo/...
```

## 使用示例

在 Claude Desktop 中可以直接使用：

```
帮我搜索一些关于"日落"的图片
```

```
找5张猫咪的图片
```

```
搜索现代建筑相关的照片
```

```
给我找一些海滩的风景图片
```

## 开发说明

### 运行测试

```bash
mvn test
```

### 本地测试服务

虽然此服务设计为通过 STDIO 与 Claude Desktop 通信，但你也可以直接运行来测试：

```bash
mvn spring-boot:run
```

或

```bash
java -jar target/image-search-mcp-server-0.0.1-SNAPSHOT.jar
```

### 添加新工具

1. 创建新的工具类，添加 `@Tool` 注解
2. 在 `ImageSearchMcpServerApplication` 中注册工具
3. 重新构建 JAR 包

### 日志配置

在 `application-sse.yml` 中调整日志级别：

```yaml
logging:
  level:
    org.springframework.ai.mcp: DEBUG
    com.qh.imagesearchmcpserver: DEBUG
```

## 传输模式说明

### STDIO 模式（推荐）

- 通过标准输入/输出进行通信
- MCP 协议的标准传输方式
- 稳定可靠，广泛支持
- 当前默认模式

### SSE 模式

- 通过 HTTP SSE 进行通信
- 需要额外的配置和依赖
- 适用于需要网络访问的场景
- 需要设置 `stdio: false` 并配置 web 端点

## 常见问题

### Claude Desktop 无法连接

请检查：
1. JAR 文件路径是否正确
2. Java 版本是否为 21 或更高
3. API Key 是否已配置

### API Key 无效

请检查：
1. API Key 是否正确复制
2. API Key 是否已激活
3. 是否超出 API 配额限制

### 搜索无结果

请检查：
1. 搜索关键词是否正确
2. 网络连接是否正常
3. Pexels API 服务是否可用

## 技术栈

- Java 21
- Spring Boot 3.5.13
- Spring AI 1.1.2
- Hutool 5.8.38
- MCP SDK 0.17.0

## 许可证

MIT License
