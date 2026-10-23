# 英语家教系统 — 腾讯云 Docker 化部署手册

> 适用场景：服务器上已有 MySQL（数据存在）+ 手动 `java -jar` 运行，现在改为 Docker 容器部署
> 目标：日常更新 = `git pull` + `docker compose up -d --build` 一条命令

---

## 部署架构

```
腾讯云 Ubuntu 服务器
├── MySQL（宿主机直装，数据不动） ←── host.docker.internal:3306
├── Docker
│   ├── english-tutor-backend  容器  8081:8081（Spring Boot）
│   └── english-tutor-nginx    容器  80:80   （前端静态 + /api/ 反代 backend:8081）
└── 旧进程（java -jar / 宿主机 nginx）部署完成后停用
```

---

## 第 0 步：部署前确认（5 分钟）

SSH 登录服务器，确认四件事：

```bash
# 1. MySQL root 实际密码（prod 配置里写的是 1234，确认是否改过）
mysql -uroot -p -e "SELECT 1"

# 2. 现在跑着的后端进程（Docker 化后要停掉）
ps aux | grep java

# 3. 宿主机 nginx 是否只服务本项目（Docker 化后要停掉）
#    当前项目前端在 /var/www/html，确认这个站点之外没有别的业务站点
nginx -T 2>/dev/null | grep -E "server_name|root|listen"
ls /etc/nginx/sites-enabled/        # 默认站点文件名通常是 default

# 4. 3306 端口是否被防火墙限制（改 bind-address 后确保不暴露公网）
sudo ufw status        # 若开启，确保 3306 未放行
```

---

## 第 1 步：检查 Docker 环境

服务器上已有 Docker（且有几个旧的 `et-*` 容器，见下），只需确认 compose 插件：

```bash
docker compose version    # 有输出则 OK；报错则: sudo apt install -y docker-compose-v2
```

**清理旧容器（重要）**：服务器上有 3 周前停用的微服务版遗留容器 `et-nginx`/`et-rabbitmq`/`et-redis`/`et-mysql`，生产数据在宿主机 MySQL，不在这些容器里。它们若设了 `restart: always`，服务器重启时会自动启动并**抢 80/3306 端口**，导致 compose 起不来。确认废弃后删除：

```bash
docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
docker rm et-nginx et-rabbitmq et-redis et-mysql
# 保守方案（不删容器，只禁止自启）:
# docker update --restart no et-nginx et-rabbitmq et-redis et-mysql
```

若拉取镜像慢，配置腾讯云内网加速器：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "registry-mirrors": ["https://mirror.ccs.tencentyun.com"]
}
EOF
sudo systemctl restart docker
```

> 备选加速器：`https://docker.m.daocloud.io`

---

## 第 2 步：建 Git 仓库并推送代码（本地 Windows 操作）

1. 打开 [gitee.com](https://gitee.com)，新建**私有**仓库 `english-tutor`（不要勾选任何初始化选项）
2. 本地推送（`.gitignore` 已排除密钥和构建产物）：

```powershell
cd C:\Users\DELL\Desktop\服务器版
git init
git add .
git commit -m "初始化：Docker 化部署版本"
git branch -M main
git remote add origin https://gitee.com/你的用户名/english-tutor.git
git push -u origin main
```

3. 服务器上拉取：

```bash
sudo apt install -y git
cd /opt
sudo git clone https://gitee.com/你的用户名/english-tutor.git
sudo chown -R $USER:$USER /opt/english-tutor
```

---

## 第 3 步：改造宿主机 MySQL（关键！容器才能连上）

Ubuntu 默认 MySQL 只监听 `127.0.0.1`，且 root 账号不允许从容器（来源 IP 是 172.x）连接。执行：

```bash
# 3.1 允许 MySQL 监听所有网卡（默认只监听本机）
sudo sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
grep bind-address /etc/mysql/mysql.conf.d/mysqld.cnf    # 确认输出 0.0.0.0

# 3.2 让 root 接受容器来源的连接（root 密码是 1234 时照抄；改过则换实际密码）
mysql -uroot -p1234 -e "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '1234'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"

# 3.2' 可选（更安全）：不暴露 root，改用专用账号（只授一个库的权限）
#      mysql -uroot -p1234 -e "CREATE USER IF NOT EXISTS 'tutor'@'%' IDENTIFIED BY '强密码'; GRANT ALL PRIVILEGES ON english_tutor_system.* TO 'tutor'@'%'; FLUSH PRIVILEGES;"
#      并把 .env 的 MYSQL_USER/MYSQL_PASSWORD 改成 tutor/强密码

# 3.3 重启 MySQL 并验证监听地址
sudo systemctl restart mysql
ss -tlnp | grep 3306    # 应显示 0.0.0.0:3306（而不是 127.0.0.1）

# 3.4 模拟容器来源连接验证（走网卡 IP，不用回环）
mysql -h$(hostname -I | awk '{print $1}') -uroot -p1234 -e "SELECT '容器模拟连接OK';"
```

> ⚠️ 安全：改完 bind-address 后 MySQL 会监听公网网卡，务必确认腾讯云控制台**安全组未放行 3306**（只放 22/80），否则数据库裸奔公网。

---

## 第 4 步：配置 .env 并启动

```bash
cd /opt/english-tutor
cp .env.docker .env
vim .env      # 填入: MYSQL_PASSWORD(第 3 步的密码)、OSS 密钥、AI_API_KEY
```

停掉旧进程（**重要**，8081 和 80 端口被占用容器起不来）：

```bash
# 停旧后端
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs -r kill

# 停宿主机 nginx（它托管着 80 端口）
sudo systemctl stop nginx
sudo systemctl disable nginx   # 防止开机自启抢 80 端口
```

启动（首次构建需要几分钟，后续更新很快）：

```bash
docker compose up -d --build
```

查看日志确认启动成功：

```bash
docker compose ps                     # 两个容器都是 Up
docker compose logs -f backend        # 看到 "Started ... in x seconds" 即成功
```

---

## 第 5 步：验证

```bash
# 前端页面
curl -s http://localhost | head

# 后端链路（返回 JSON 即可——即使是 401 未登录提示也说明链路通了）
# 502 说明后端没起来；连接失败说明 nginx 容器有问题
curl -s http://localhost/api/subjects
curl -s http://localhost/api/vocabularies
```

浏览器打开 `http://你的公网IP`，登录系统跑一遍核心功能（登录、词汇、上传文件）。

---

## 日常更新流程（以后每次就这三条）

**本地开发**：
```powershell
cd C:\Users\DELL\Desktop\服务器版
git add . && git commit -m "修改内容"
git push
```

**服务器部署**：
```bash
cd /opt/english-tutor
git pull
docker compose up -d --build
```

> 只改了后端（Java）就只重建后端：`docker compose up -d --build backend`
> 只改了前端：`docker compose up -d --build frontend`

---

## 回退方案（出问题随时退回手动部署）

```bash
# 停容器
cd /opt/english-tutor && docker compose down

# 恢复宿主机 nginx
sudo systemctl enable --now nginx

# 恢复旧后端（需要原来的 jar）
# java -jar app.jar --spring.profiles.active=prod &
```

---

## 常见问题

| 症状 | 原因 | 解决 |
|------|------|------|
| backend 日志报 `Communications link failure` | MySQL bind-address 没改或账号没授权 | 重做第 3 步 |
| backend 日志报 `Access denied for user` | .env 密码不对或账号 host 不对 | 确认账号是 `tutor@'%'` |
| `ports are not available: 80` | 宿主机 nginx 没停 | `systemctl stop nginx` |
| 构建卡在 Maven/npm 下载 | 镜像加速没生效 | 检查 Dockerfile 已含阿里云/npmmirror 配置（已内置） |
| 更新后页面还是旧版 | 浏览器缓存 | 强制刷新（Ctrl+F5） |
