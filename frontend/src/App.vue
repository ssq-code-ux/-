<template>
  <router-view />
  <!-- PWA 安装提示横幅（仅浏览器支持安装时出现） -->
  <InstallPrompt />
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue';
import InstallPrompt from './components/InstallPrompt.vue';

let versionTimer = null;

onMounted(() => {
  // ═══════════════ 滚动解锁修复 ═══════════════
  const unlockScroll = () => {
    if (document.body.style.overflow === 'hidden') {
      const viewer = document.querySelector('.el-image-viewer__wrapper');
      if (!viewer || viewer.style.display === 'none') {
        document.body.style.overflow = '';
      }
    }
  };
  const observer = new MutationObserver(() => unlockScroll());
  observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
  document.addEventListener('touchend', unlockScroll, { passive: true });
  document.addEventListener('click', unlockScroll);

  // ═══════════════ 自动检测新版本 ═══════════════
  // 页面加载时立刻检测，之后每 30 秒检测，发现新版本自动刷新
  const currentTag = __BUILD_TAG__;
  async function checkVersion() {
    try {
      const res = await fetch('/version.json?t=' + Date.now());
      const data = await res.json();
      if (data.tag && data.tag !== currentTag) {
        console.log('%c检测到新版本，自动刷新...', 'color:var(--positive);font-size:14px');
        window.location.reload();
      }
    } catch (e) { /* 忽略 */ }
  }
  checkVersion();                              // 立刻检测
  versionTimer = setInterval(checkVersion, 30000);  // 每30秒

  console.log('%c学习助手 %c' + __BUILD_TIME__, 'font-size:16px;color:var(--accent)', 'color:var(--muted)');
});

onBeforeUnmount(() => {
  if (versionTimer) clearInterval(versionTimer);
});
</script>
