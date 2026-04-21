# ========== Stage 1: Build image-search-mcp-server ==========
FROM maven:3.9-amazoncorretto-21 AS mcp-builder
WORKDIR /build
COPY image-search-mcp-server/pom.xml .
COPY image-search-mcp-server/src ./src
RUN mvn clean package -DskipTests -q

# ========== Stage 2: Build main project ==========
FROM maven:3.9-amazoncorretto-21 AS app-builder
WORKDIR /build
COPY pom.xml .
# 先下载依赖（利用 Docker 层缓存）
RUN mvn dependency:go-offline -q
COPY src ./src
# Windows 本地用 npx.cmd，Linux 容器改成 npx
RUN sed -i 's/npx\.cmd/npx/g' src/main/resources/MCP-servers.json
RUN mvn clean package -DskipTests -q

# ========== Stage 3: Runtime ==========
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache nodejs npm
WORKDIR /app

# 复制主应用 JAR
COPY --from=app-builder /build/target/AI_Agent-0.0.1-SNAPSHOT.jar app.jar

# 复制 MCP server JAR（保持 MCP-servers.json 中定义的相对路径）
COPY --from=mcp-builder /build/target/image-search-mcp-server-0.0.1-SNAPSHOT.jar \
     ./image-search-mcp-server/target/image-search-mcp-server-0.0.1-SNAPSHOT.jar

# 创建持久化目录
RUN mkdir -p /app/chat-memory /app/tmp/file /app/tmp/Download /app/tmp/PDF

EXPOSE 8123
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
