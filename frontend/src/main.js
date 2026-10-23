import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import 'virtual:uno.css';
import './styles/global.css';
import { createPinia } from 'pinia';
import router from './router';
import App from './App.vue';

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app');

// PWA：生产环境注册 Service Worker（支持安装为桌面/手机应用 + 离线缓存）
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.register('/sw.js');
      // 新版 SW 安装完成后立即接管（页面刷新由 version.json 轮询机制负责）
      reg.addEventListener('updatefound', () => {
        const newWorker = reg.installing;
        if (!newWorker) return;
        newWorker.addEventListener('statechange', () => {
          if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
            newWorker.postMessage('SKIP_WAITING');
          }
        });
      });
    } catch (e) { /* SW 注册失败不影响正常使用 */ }
  });
}
