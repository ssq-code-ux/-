智乐学 · 项目简介
一句话：面向中国初高中英语的在线学习 + 家教撮合平台，覆盖「学生 / 老师 / 家长 / 管理」四种角色，支持 Web 端与安卓 App。

技术栈
层	选型
后端	Spring Boot 3.3.4 · Java 17 · MyBatis-Plus 3.5.7 · MySQL（库 english_tutor_system）· Lombok · JWT + Refresh Token
Web 前端	Vue 3 · Vite · Vue Router · Pinia · Element Plus · ECharts · UnoCSS（PWA，可安装到桌面/手机）
移动端	uni-app3（安卓 App，支持 wgt 热更新）
部署	Docker Compose（backend + nginx 前端）、nginx-cloud.conf、.env / .env.docker
外部服务	阿里云百炼/通义千问（AI，默认 qwen-flash）、阿里云 OSS（文件/图片）、短信、PushPlus/Server酱（微信推送）
四种角色
学生：学习看板、我的作业、词汇学习、记忆卡片、学习资料
老师：首页（新）、学生管理、作业管理、词汇管理、背单词、学习资料、接单广场
家长：孩子的学习情况、学习报告、找家教
管理：数据看板、用户/家教信息/同行合作单/家长需求/退费工单/缴费流水/科目模板/接单配置/App 版本
核心业务模块（后端 24 个 Controller）
教学闭环：作业布置 → 提交 → 批改 → 重做/逾期提醒；AI 答题卡出卷与判分
词汇与背单词：多词库、记忆卡片、看词选意/听写、错题三本状态机（不熟/复习/已掌握）、记忆计划
AI：AI 智能扩展词汇、AI 学习助手对话
资料库：多格式文档（Word/PDF/PPT/Excel/图片）上传、在线预览、教师间共享复制
家教撮合：家长发布需求 → 教师投递 → 试课 → 缴费解锁 → 退费工单 → 同行合作单
其它：站内通知 + 微信推送、学习目标、个人档案、App 版本发布/热更新
目录结构
服务器版/
├─ English-learner/   后端（Spring Boot，24 个 Controller）
├─ frontend/          Web 前端（Vue3，34 条路由）
├─ app-android/       移动端（uni-app）
├─ sql/               数据库迁移脚本（10~26 号）
├─ scripts/           运维/辅助脚本
├─ docker-compose.yml 部署编排
├─ nginx-cloud.conf   反向代理配置
└─ *.md               HTTPS / 宝塔·腾讯云 Docker 部署文档
运行 / 部署
本地：后端连本机 MySQL；前端 cd frontend && npm run dev
线上：docker compose up -d --build（backend + nginx），nginx 把 /api 代理到后端
近期工作（配色与 UI）
定了设计令牌 + Element Plus 全局皮肤；四端统一为浅蓝「智慧校园」风（白卡 + 圆角图标 KPI + 柔阴影）
新增 components/ui/（PageHeader / StatusTag / EmptyState / Section / KpiCard）与教师首页
登录页保留深色影院感（canvas 粒子星野 + 浮动信息卡）
修了几个历史遗留的展示 bug（3D 翻牌被覆盖、plain 按钮蓝底蓝字、教师首页空白）
