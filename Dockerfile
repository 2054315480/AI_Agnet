# ============ 后端多阶段构建 ============

# 阶段 1: Maven 构建
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
# 先下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -q || true
COPY src src/

RUN mvn clean package -Dmaven.test.skip=true -q

# 阶段 2: 运行时
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/*.jar app.jar

# 复制知识库数据和 FAQ
COPY data/data/knowledge_base/ ./data/data/knowledge_base/
COPY data/faq/ ./data/faq/
COPY src/main/resources/prompts/ ./prompts/

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=local,docker"]
