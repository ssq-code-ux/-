# ============================================
# 英语家教系统 - 前端 Nginx Dockerfile
# 多阶段构建：Node 编译 Vue → Nginx 提供静态文件 + 反向代理
# 双模式：启动时自动检测证书——有证书走 80+443 双监听（80 不再强跳 443），无证书只走 80
# 保留 80 明文入口的原因：纯 IP 部署时证书域名不匹配，微信内置浏览器会直接失败
# ============================================

# ===== 阶段 1：Node 编译前端 =====
FROM node:20-alpine AS builder
WORKDIR /build

# 构建期限制 Node 内存，避免在 2G 小机器上构建时 OOM（仅影响构建阶段，不影响运行）
ENV NODE_OPTIONS=--max-old-space-size=768

# npm 国内镜像（构建加速，国内服务器必备）
RUN npm config set registry https://registry.npmmirror.com

# 利用 Docker 缓存：先复制依赖文件
COPY package.json package-lock.json* ./
RUN npm ci --silent 2>/dev/null || npm install --silent

# 复制源码并编译
COPY . .
RUN npm run build

# ===== 阶段 2：Nginx 运行 =====
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# 删除 Nginx 默认页面和默认配置
RUN rm -rf ./* /etc/nginx/conf.d/default.conf

# 复制编译好的静态文件
COPY --from=builder /build/dist ./

# 两套候选配置放 modes/ 目录（不在 conf.d/ 下，nginx 不会自动加载，避免指令重复）
# 启动脚本按证书有无把其中一份复制为 conf.d/default.conf
COPY nginx-docker.conf /etc/nginx/modes/http.conf
COPY nginx-https.conf  /etc/nginx/modes/https.conf

# 启动脚本（去除 Windows 换行符，防止 sh 报 \r 错误）
COPY entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh /etc/nginx/modes/http.conf /etc/nginx/modes/https.conf \
    && chmod +x /entrypoint.sh

# 健康检查：nginx 始终监听 80，直接探 127.0.0.1 最稳
# 注意：busybox wget 不支持 --no-check-certificate；且 localhost 可能解析到 ::1 导致误判 unhealthy
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1

EXPOSE 80 443

ENTRYPOINT ["/entrypoint.sh"]
