/* 智乐学 PWA Service Worker
 * 缓存策略：
 * - /api、/uploads、/version.json：直连，永不缓存（保证数据实时、版本检测有效）
 * - 页面导航：网络优先，离线时回退到缓存的首页（SPA 由前端路由接管），
 *   首页也没缓存时兜底到 offline.html 离线页
 * - 静态资源（js/css/图片）：有缓存先用缓存，后台静默更新（stale-while-revalidate）
 * - 支持 SKIP_WAITING 消息：新版本安装完成后由页面通知立即接管
 */
const CACHE_NAME = 'engtutor-v6';
const APP_SHELL = [
  '/',
  '/offline.html',
  '/manifest.webmanifest',
  '/icons/icon-ai-192.png',
  '/icons/icon-ai-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// 页面可主动通知新 SW 立即接管（配合 main.js 的 updatefound 逻辑）
self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  // 跨域请求（OSS 文件等）不拦截
  if (url.origin !== location.origin) return;
  // 接口 / 上传 / 版本检测 / SW 自身：直连不缓存
  if (url.pathname.startsWith('/api/') ||
      url.pathname.startsWith('/uploads/') ||
      url.pathname === '/version.json' ||
      url.pathname === '/sw.js') return;

  if (req.mode === 'navigate') {
    // 页面导航：网络优先 → 缓存首页 → offline.html 兜底
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put('/', copy));
          return res;
        })
        .catch(() =>
          caches.match('/').then((hit) => hit || caches.match('/offline.html'))
        )
    );
    return;
  }

  // 静态资源：缓存优先 + 后台更新
  event.respondWith(
    caches.match(req).then((hit) => {
      const fetching = fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then((c) => c.put(req, copy));
          }
          return res;
        })
        .catch(() => hit);
      return hit || fetching;
    })
  );
});
