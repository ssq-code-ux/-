# ============================================
# 英语家教系统 - 后端 Spring Boot Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行，最终镜像 ~200MB
# ============================================

# ===== 阶段 1：Maven 编译 =====
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 构建期限制 Maven/JVM 内存，避免在 2G 小机器上编译时 OOM（仅影响构建阶段，不影响运行）
ENV MAVEN_OPTS="-Xmx512m"

# 阿里云 Maven 镜像（国内构建必备，否则下载依赖会超时）
COPY maven-settings.xml /root/.m2/settings.xml

# 先复制 pom.xml 单独下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# 复制源码并编译
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ===== 阶段 2：JRE 运行 =====
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# 安全：用非 root 用户运行
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

# 复制编译好的 JAR
COPY --from=builder /build/target/*.jar app.jar

# 创建上传临时目录（虽然生产用 OSS，但 Spring Multipart 需要临时目录）
RUN mkdir -p /tmp/uploads && chown -R appuser:appgroup /tmp

ENV TZ=Asia/Shanghai

USER appuser

EXPOSE 8081

# JVM 参数优化（针对 2G 小内存服务器）：
# - 显式固定堆大小：不用 MaxRAMPercentage 按宿主机比例算（无 cgroup 限制时会算到 1.4G，逼出 swap）
# - 堆 512M 对单体 Spring Boot 应用足够；元空间/线程栈也一并压小
# - SerialGC 在小内存、少核场景比 G1 更省内存与开销
# - OOM 时直接退出（配合 docker restart: unless-stopped 自动重启，避免拖垮整机）
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-Xms256m", \
    "-Xmx512m", \
    "-XX:MaxMetaspaceSize=192m", \
    "-XX:+UseSerialGC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Duser.timezone=Asia/Shanghai", \
    "-jar", "app.jar"]

# 默认使用 docker profile
CMD ["--spring.profiles.active=docker"]
