#!/bin/sh
# ============================================
# Nginx 双模式启动脚本
# 检测到证书（/etc/nginx/cert/server.crt + server.key）→ 双监听模式（80 明文 + 443 TLS 同时对外服务）
# 未检测到证书 → HTTP 模式（直接 80 提供服务）
# 注意：候选配置放在 /etc/nginx/modes/，该目录不会被 nginx 自动加载
# ============================================
if [ -f /etc/nginx/cert/server.crt ] && [ -f /etc/nginx/cert/server.key ]; then
    echo "[entrypoint] 检测到证书，启用 HTTP + HTTPS 双监听（80 不跳转，微信内可直接打开）"
    cp /etc/nginx/modes/https.conf /etc/nginx/conf.d/default.conf
else
    echo "[entrypoint] 未检测到证书，使用 HTTP 模式"
    cp /etc/nginx/modes/http.conf /etc/nginx/conf.d/default.conf
fi
exec nginx -g 'daemon off;'
