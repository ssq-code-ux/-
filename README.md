# 智乐学 · English Tutor
访问地址：https://ssq.ink
> 面向中国初高中英语的**在线学习 + 家教撮合**平台。覆盖「学生 / 老师 / 家长 / 管理员」四种角色，提供 Web 端与安卓 App。

## 功能特性

### 学生端
- **学习看板**：待完成作业、待批改、错题、今日复习一览
- <img width="2530" height="1216" alt="image" src="https://github.com/user-attachments/assets/5c8c60e6-d017-4269-a00b-d4a853d61aec" />

- **我的作业**：查看/提交作业，逾期提醒，批改结果与老师评语
- <img width="2534" height="1198" alt="image" src="https://github.com/user-attachments/assets/00f750d8-c11b-4a5f-aca9-d84d9bd31c2e" />

- **词汇学习**：多词库、看词选意、听写、**错题三本状态机**（不熟 / 复习 / 已掌握）
- <img width="2532" height="1212" alt="image" src="https://github.com/user-attachments/assets/3be70b7a-4af4-44f6-8f75-722f3d3c3336" />

- **记忆卡片**：3D 翻卡，认识 / 不熟自动进错题本
- <img width="2546" height="1204" alt="image" src="https://github.com/user-attachments/assets/c76170a9-ea24-49ca-8d1a-80fbdc5759b5" />

- **学习资料**：多格式文档在线预览
- <img width="2530" height="1205" alt="image" src="https://github.com/user-attachments/assets/6989dce5-796a-4c1b-921c-5d80ed7b4628" />


### 老师端
<img width="2542" height="1207" alt="image" src="https://github.com/user-attachments/assets/5e852a93-b3f7-438d-9c0f-a90199c7b056" />

- **首页**：KPI 概览 + 快捷入口 + 最近布置的作业
- <img width="2532" height="1207" alt="image" src="https://github.com/user-attachments/assets/17c08a09-8825-47c4-890c-c0d39c699012" />

- **学生管理 / 作业管理**：布置 → 提交 → 批改（红笔/板绿状态）→ 重做
- <img width="2548" height="1202" alt="image" src="https://github.com/user-attachments/assets/709ff580-aaee-441c-930d-d95eef333845" />
<img width="2544" height="1265" alt="image" src="https://github.com/user-attachments/assets/20509268-5ebe-4841-9bd5-3901b735eb17" />


- **词汇管理**：词库维护 + AI 智能扩展词汇
- <img width="2542" height="1214" alt="image" src="https://github.com/user-attachments/assets/3f7e2593-74fe-4bf9-9940-82fa7f56d76f" />

- **背单词**：记忆卡片 / 看词选意 / 错题本三种模式
- <img width="2541" height="1216" alt="image" src="https://github.com/user-attachments/assets/4f106497-a24d-4652-a74e-35da8aff5beb" />

- **接单广场**：家教单筛选、投递、缴费解锁、邀请同行
- <img width="2544" height="1214" alt="image" src="https://github.com/user-attachments/assets/40469bd6-e830-4700-bc7a-5184d65a6331" />


### 家长端
- 孩子的学习情况（提交率 / 平均分 / 错题）、学习周报、AI 点评
- <img width="2537" height="1200" alt="image" src="https://github.com/user-attachments/assets/b2732a77-3144-4126-a348-9884b38e3f28" />
<img width="2542" height="1219" alt="image" src="https://github.com/user-attachments/assets/aec024fd-86a6-4ea4-9c4f-681f9717a889" />

- 找家教：发布需求、查看投递

### 管理端
数据看板、用户管理、家教信息、同行合作单、家长需求、退费工单、缴费流水、科目模板、接单配置、App 版本发布
<img width="2532" height="1207" alt="image" src="https://github.com/user-attachments/assets/651be955-51f5-431e-922d-4ff16aff2221" />


### 平台能力
AI 答题卡出卷与自动判分 · 站内通知 + 微信推送（PushPlus / Server酱）· 学习目标 · PWA 可安装 · App 热更新（wgt）

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.3 · Java 17 · MyBatis-Plus 3.5 · MySQL · Lombok · JWT + Refresh Token |
| Web 前端 | Vue 3 · Vite · Vue Router · Pinia · Element Plus · ECharts · UnoCSS |
| 移动端 | uni-app 3（安卓 App，支持 wgt 热更新） |
| 部署 | Docker Compose（backend + nginx）· Nginx 反代 |
| 外部服务 | 阿里云 OSS（文件）· 阿里云百炼/通义千问（AI）· 阿里云短信 · PushPlus / Server酱 |

## 目录结构

```
.
├─ English-learner/   # 后端（Spring Boot，24 个 Controller）
├─ frontend/          # Web 前端（Vue 3）
├─ app-android/       # 移动端（uni-app）
├─ sql/               # 数据库迁移脚本（10 ~ 26 号）
├─ scripts/           # 词库种子 / 音标补充等辅助脚本
├─ docker-compose.yml # 部署编排（backend + nginx）
└─ nginx-cloud.conf   # 反向代理配置示例
```

## 快速开始

### 1. 准备数据库
创建数据库 `english_tutor_system`，然后按序号执行迁移脚本：

```bash
mysql -u root -p english_tutor_system < sql/10_migrate_push_and_voice.sql
# ... 依次执行 sql/ 目录下的脚本（11 ~ 26）
```

> 部分新表结构见 `English-learner/src/main/resources/schema_new_tables.sql`、`schema_quiz_error.sql`。
> **基础表（users / homeworks 等）暂未包含在仓库中**，建议从已有环境导出后补一个 `sql/00_schema.sql`。

### 2. 启动后端
> 需要本地 JDK 17+ 与 Maven 3.9+（仓库未包含 mvnw）

```bash
cd English-learner
# 通过环境变量注入密钥（见下方「环境变量」）
mvn spring-boot:run
# 或：mvn clean package -DskipTests && java -jar target/*.jar
```
默认端口 `8081`。

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev        # 开发（默认 http://localhost:5173）
npm run build      # 生产构建，产物在 dist/
```

### 4. 运行移动端
用 **HBuilderX** 打开 `app-android`，运行到浏览器/手机，或「发行 → 原生 App 云打包」。

## 环境变量

生产/部署请**全部用环境变量注入**，不要把密钥写进配置文件。

| 变量 | 说明 |
| --- | --- |
| `MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | JWT 签名密钥（足够长的随机串） |
| `OSS_ENDPOINT` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET_NAME` / `OSS_REGION` | 阿里云 OSS |
| `AI_API_KEY` / `AI_BASE_URL` / `AI_MODEL` / `AI_VISION_MODEL` | 通义千问（默认 `qwen-flash` / `qwen-vl-plus`） |
| `ALIYUN_SMS_ACCESS_KEY_ID` / `ALIYUN_SMS_ACCESS_KEY_SECRET` / `ALIYUN_SMS_SIGN_NAME` / `ALIYUN_SMS_TEMPLATE_CODE` | 短信（可选，缺失则降级为仅站内通知） |

## 部署（Docker Compose）

```bash
cp .env.docker .env      # 填入真实密钥
docker compose up -d --build
```
- `backend` 容器监听 `8081`（仅本机），`nginx` 容器监听 `80/443` 并反代 `/api` 到后端
- 反向代理与 HTTPS 参考 `nginx-cloud.conf` 与仓库根目录的部署文档
