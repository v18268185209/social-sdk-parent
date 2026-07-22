# ============================================================================
# Dockerfile — 闲鱼管理器
#
# 支持三种构建模式（通过 --build-arg DB_MODE=）：
#   sqlite (默认) — 单容器，内嵌 SQLite
#   mysql         — 外接 MySQL 8
#   postgres      — 外接 PostgreSQL
#
# 用法:
#   docker build --build-arg DB_MODE=sqlite -t xianyu-manager:sqlite .
#   docker build --build-arg DB_MODE=mysql -t xianyu-manager:mysql .
#   docker build --build-arg DB_MODE=postgres -t xianyu-manager:postgres .
# ============================================================================

# ── Stage 1: Maven 构建 ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 复制项目文件
COPY pom.xml .
COPY social-sdk-core ./social-sdk-core
COPY social-sdk-xianyu ./social-sdk-xianyu
COPY social-sdk-chrome ./social-sdk-chrome
COPY social-sdk-proxys ./social-sdk-proxys
COPY social-sdk-cdp-auth ./social-sdk-cdp-auth
COPY social-sdk-xianyu-manager ./social-sdk-xianyu-manager
COPY social-sdk-spring-boot-starter ./social-sdk-spring-boot-starter

# 复制前端源码（用于 Maven 插件构建）
COPY social-sdk-xianyu-manager-web ./social-sdk-xianyu-manager-web

# 构建 JAR（含前端）
RUN mvn clean package -DskipTests -pl social-sdk-xianyu-manager -am \
    && mv social-sdk-xianyu-manager/target/*.jar /app/app.jar

# ── Stage 2: 运行时镜像 ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

# 安装生产环境依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    fonts-liberation \
    fonts-noto-cjk \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户
RUN groupadd -r xianyu && useradd -r -g xianyu -d /app -s /sbin/nologin xianyu

WORKDIR /app

# 复制 JAR
COPY --from=builder /app/app.jar server.jar

# 创建数据目录（持久化用）
RUN mkdir -p /app/data /app/data/openlist /app/chrome-profiles /app/logs /app/config \
    && chown -R xianyu:xianyu /app

# 数据库镜像变体：sqlite / mysql / postgres
ARG DB_MODE=sqlite

# 环境变量
ENV CHROME_BIN=/usr/bin/chromium \
    CHROME_HEADLESS=true \
    CHROME_HEADLESS_MODE=new \
    SPRING_PROFILES_ACTIVE=${DB_MODE} \
    JAVA_OPTS="-Xmx512m -Xms256m" \
    SERVER_PORT=8080 \
    DB_PATH=/app/data/xianyu-manager.db \
    TZ=Asia/Shanghai

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://127.0.0.1:${SERVER_PORT}/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 数据卷
VOLUME ["/app/data", "/app/chrome-profiles", "/app/logs", "/app/config"]

# 使用非 root 用户运行
USER xianyu

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar /app/server.jar"]
