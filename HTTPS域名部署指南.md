# HTTPS 域名部署指南（PWA 应用安装必备）

> 浏览器规定：PWA 安装（"立即安装"横幅）只在 **HTTPS** 环境下出现。
> 本文档指导你完成：域名 → 解析 → 免费证书 → 服务器配置，全程约 15~30 分钟。

---

## 第 1 步：购买域名（已有域名可跳过）

1. 打开 [腾讯云域名注册](https://dnspod.cloud.tencent.com/)（或任意域名商）
2. 搜一个喜欢的域名，`.cn` / `.com.cn` 最便宜（首年约 20 元以内）
3. 购买后按提示完成**实名认证**（国内域名必须，一般 1 个工作日内通过）

> 假设你买到的域名是 `mytutor.cn`，下文都以此为例。

## 第 2 步：域名解析到服务器

1. 进入腾讯云 **DNS 解析 DNSPod** 控制台
2. 找到你的域名 → **添加记录**，加两条：

| 主机记录 | 记录类型 | 记录值 |
|---|---|---|
| `@` | A | 你的服务器公网 IP |
| `www` | A | 你的服务器公网 IP |

3. 等 1~2 分钟生效，本地验证：`ping mytutor.cn` 能解析出你的 IP 即可

## 第 3 步：申请免费 SSL 证书

1. 腾讯云控制台 → **SSL 证书** → **我的证书** → **申请免费证书**（DV 型，个人可用）
2. 绑定域名填 `mytutor.cn`，验证方式选 **DNS 验证**（域名在腾讯云的话会自动添加验证记录）
3. 等待签发（一般几分钟到几小时）
4. 签发后点 **下载** → 选择 **Nginx** 格式 → 得到压缩包，里面有：
   - `mytutor.cn_bundle.crt`
   - `mytutor.cn.key`
5. **重命名**这两个文件为 `server.crt` 和 `server.key`

> 免费证书有效期较短（90 天/3 个月，政策时有调整），到期后重复本步骤重新申请、
> 重新上传覆盖并重启 nginx 容器即可：`docker compose restart frontend`

## 第 4 步：上传证书到服务器

在服务器上执行：

```bash
cd /opt/english-tutor
mkdir -p nginx-cert
```

然后用 WinSCP / scp 把 `server.crt`、`server.key` 上传到 `/opt/english-tutor/nginx-cert/`：

```powershell
# Windows PowerShell 本地执行（替换为你的服务器 IP）
scp server.crt server.key root@你的服务器IP:/opt/english-tutor/nginx-cert/
```

确认目录结构：

```
/opt/english-tutor/
├── nginx-cert/
│   ├── server.crt
│   └── server.key
├── docker-compose.yml
└── ...
```

## 第 5 步：放行 443 端口（重要，容易漏！）

1. 腾讯云控制台 → **轻量应用服务器 / 云服务器** → **防火墙（安全组）**
2. 添加规则：**TCP 443**，来源 `0.0.0.0/0`（80 之前已放行）

## 第 6 步：拉取最新代码并重启

```bash
cd /opt/english-tutor
git pull
docker compose up -d --build
```

验证两个容器都是 Up：

```bash
docker compose ps
```

如果 nginx 容器启动失败，多半是证书没放对：

```bash
docker compose logs frontend | tail -20
```

## 第 7 步：访问并安装应用

1. 浏览器打开 `https://mytutor.cn`（地址栏显示🔒即成功）
   - 本配置为 **80 + 443 双监听**，`http://` **不再**自动跳 `https://`，两个入口都能正常用
   - 微信里分享链接请用 `http://`，原因见下方「纯 IP 部署 / 微信内打不开」
2. 登录后，页面底部出现 **"把「英语家教」安装到设备"** 横幅 → 点 **立即安装**
   - 电脑端：安装后出现在桌面/开始菜单，独立窗口打开
   - 安卓手机：Chrome 打开网站 → 弹出安装提示 → 图标出现在桌面
   - iPhone：Safari 打开 → 分享 → **添加到主屏幕**（iOS 不支持横幅安装，必须这样操作）
3. 没出现横幅时的排查（Chrome 按 F12 → Console）：
   - 确认地址栏是 `https://` 且证书无警告
   - Console 输入 `navigator.serviceWorker.getRegistrations().then(r=>console.log(r))` 应有 1 条记录

---

## 纯 IP 部署 / 微信内打不开（重要）

如果你**还没买域名**，直接用服务器公网 IP 访问，一定会踩到这个坑：

**现象**：Chrome / Edge 里能打开（先弹证书警告，点「高级 → 继续前往」即可），
但把链接发到微信里点开，显示「非微信官方网页，继续访问将在默认浏览器打开」或直接白屏。

**原因**：免费 SSL 证书是**签给域名**的，用 IP 访问时证书里的域名和地址栏对不上
（`ERR_CERT_COMMON_NAME_INVALID`）。Chrome 允许用户手动忽略，微信内置浏览器
（X5/XWEB 内核）不允许，直接判定失败。**与本项目代码无关**。

**解决**：本项目 nginx 已配成 **80 + 443 双监听**（80 端口提供完整服务、不做强制跳转），
所以纯 IP 部署时：

- **微信里分享用 `http://服务器IP`** → 可直接打开，无任何警告
- 需要 PWA 安装时，用浏览器打开 `https://服务器IP`，手动忽略证书警告后安装

> 把 `frontend/nginx-https.conf` 改回 `return 301 https://...` 强制跳转，会让微信里彻底打不开，别这么干。
> 代码已为 HTTP 降级做好兜底：`main.js` 的 Service Worker 注册包了 try/catch，
> HTTP 下只是静默不注册（没离线缓存、没安装横幅），功能不受影响。

**根治办法**：完成上文第 1~3 步（买域名 + 备案 + 证书签给域名）后，用域名走 https，
微信和浏览器两边都正常，PWA 也能装。注意：域名**未备案**时微信仍可能提示
「非微信官方网页」，那是微信对未备案域名的策略，需在拦截页底部「申请恢复访问」申诉，
同样与代码无关。

---

## 常见问题

| 现象 | 原因与解决 |
|---|---|
| 微信里打不开、浏览器能开 | 纯 IP 走 https 时证书域名不匹配，微信内核不给「继续访问」；微信里改用 `http://IP`，详见上方专节 |
| 浏览器提示证书不受信任 | 证书文件传错/传串了，重新上传 `server.crt` + `server.key` 后 `docker compose restart frontend` |
| https 打不开但 http 能开 | 安全组没放行 443，回第 5 步 |
| 域名解析不到 | 第 2 步记录没生效，等几分钟或 `ping 域名` 验证 |
| nginx 容器反复重启 | `nginx-cert` 目录为空或文件名不是 server.crt/server.key |
| 证书过期网站打不开 | 重新申请免费证书，覆盖上传后重启 frontend 容器 |
