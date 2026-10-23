<template>
  <transition name="install-fade">
    <!-- Chrome/Edge/安卓：能拿到原生安装事件，直接一键安装 -->
    <div v-if="nativeVisible" class="install-banner">
      <span class="install-icon"></span>
      <div class="install-text">
        <b>把「智乐学」安装到设备</b>
        <span>像 App 一样独立窗口使用，无需浏览器</span>
      </div>
      <el-button size="small" type="primary" round @click="install">立即安装</el-button>
      <span class="install-done" @click="confirmInstalled">已下载，不再提示</span>
      <span class="install-close" title="关闭（下次还会提醒）" @click="dismiss">×</span>
    </div>

    <!-- iOS Safari：不支持安装事件，只能引导「分享 → 添加到主屏幕」 -->
    <div v-else-if="iosGuideVisible" class="install-banner install-guide">
      <span class="install-icon"></span>
      <div class="install-text">
        <b>把「智乐学」装到 iPhone 桌面</b>
        <span>点 Safari 底部「分享」<b class="guide-key"></b> → 下滑选「添加到主屏幕」</span>
      </div>
      <span class="install-done" @click="confirmInstalled">已下载，不再提示</span>
      <span class="install-close" title="关闭（下次还会提醒）" @click="dismiss">×</span>
    </div>

    <!-- 安卓国产浏览器兜底：不派发安装事件，引导走浏览器菜单 -->
    <div v-else-if="androidGuideVisible" class="install-banner install-guide">
      <span class="install-icon"></span>
      <div class="install-text">
        <b>把「智乐学」装到桌面</b>
        <span>点浏览器右上角 ⋮ 菜单 → 选「安装应用」或「添加到主屏幕」（推荐用 Chrome/Edge 浏览器）</span>
      </div>
      <span class="install-done" @click="confirmInstalled">已下载，不再提示</span>
      <span class="install-close" title="关闭（下次还会提醒）" @click="dismiss">×</span>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted, watchEffect, onBeforeUnmount } from 'vue';
import { usePwaInstall, isIOS } from '../composables/usePwaInstall';

const { canInstall, promptInstall } = usePwaInstall();

// 【临时关闭】应用审核期间暂停 PWA 安装引导弹窗，全部逻辑代码保留未删；
// 审核通过后把这里改回 true 即可恢复（onMounted / onCanInstall 两处入口都已接开关）
const INSTALL_PROMPT_ENABLED = false;

const dismissedKey = 'pwa-install-dismissed';
const nativeVisible = ref(false);
const iosGuideVisible = ref(false);
const androidGuideVisible = ref(false);

function isDismissed() {
  return localStorage.getItem(dismissedKey) === '1';
}

// 拿到原生安装事件 → 显示一键安装横幅（iOS 永远走不到这里）
function onCanInstall() {
  if (!INSTALL_PROMPT_ENABLED) return; // 审核期间：不弹原生安装横幅
  iosGuideVisible.value = false;
  androidGuideVisible.value = false;
  if (!isDismissed()) nativeVisible.value = true;
}

onMounted(() => {
  if (!INSTALL_PROMPT_ENABLED) return; // 审核期间：iOS/安卓引导横幅也全部不弹
  // 已在安装后的应用里（独立窗口），永远不再提示
  if (useStandaloneOnce()) return;
  // iOS：不派发安装事件，直接给「分享 → 添加到主屏幕」引导
  if (isIOS()) {
    if (!isDismissed()) iosGuideVisible.value = true;
    return;
  }
  // 安卓：部分国产浏览器从不派发 beforeinstallprompt，
  // 10 秒内没等到事件就显示菜单安装引导（装到桌面的效果一致）
  if (/android/i.test(navigator.userAgent)) {
    setTimeout(() => {
      if (!canInstall.value && !isDismissed()) androidGuideVisible.value = true;
    }, 10000);
  }
  // 桌面 Chrome/Edge：事件晚于挂载到达时也能弹出
  if (canInstall.value) onCanInstall();
});

// composable 的 canInstall 是响应式的，事件可能晚于组件挂载才触发
const stop = watchEffect(() => {
  if (canInstall.value) onCanInstall();
});
onBeforeUnmount(stop);

function useStandaloneOnce() {
  if (window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true) {
    localStorage.setItem(dismissedKey, '1');
    return true;
  }
  return false;
}

async function install() {
  const result = await promptInstall();
  nativeVisible.value = false;
  if (result.status === 'accepted') {
    localStorage.setItem(dismissedKey, '1');
  } else if (result.status === 'dismissed') {
    // 用户在系统弹窗里取消：降级为菜单引导，下次访问还能再点
    if (/android/i.test(navigator.userAgent)) androidGuideVisible.value = true;
  } else {
    // 安装事件已失效（典型场景：跳去开权限后回来），降级引导
    if (isIOS()) iosGuideVisible.value = true;
    else androidGuideVisible.value = true;
  }
}

// × 只关闭本次（防止手滑），下次访问还会提醒；只有安装成功或点「已下载」才永久关闭
function dismiss() {
  nativeVisible.value = false;
  iosGuideVisible.value = false;
  androidGuideVisible.value = false;
}

// 用户确认已下载/已添加桌面 → 永久不再提示
function confirmInstalled() {
  dismiss();
  localStorage.setItem(dismissedKey, '1');
}
</script>

<style scoped>
.install-banner {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(25, 35, 60, 0.92);
  color: #fff;
  border-radius: 14px;
  max-width: calc(100vw - 32px);
}
.install-icon {
  font-size: 26px;
}
.install-text {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}
.install-text b {
  font-size: 14px;
}
.install-text span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}
.install-text .guide-key {
  font-size: 12px;
  color: #fff;
}
.install-close {
  cursor: pointer;
  color: rgba(255,255,255,0.5);
  font-size: 14px;
  padding: 4px;
}
.install-close:hover {
  color: #fff;
}
.install-done {
  cursor: pointer;
  font-size: 12px;
  color: rgba(255,255,255,0.65);
  white-space: nowrap;
  text-decoration: underline;
  padding: 4px 2px;
}
.install-done:hover {
  color: #fff;
}
.install-fade-enter-active,
.install-fade-leave-active {
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
}
.install-fade-enter-from,
.install-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 20px);
}

@media (max-width: 600px) {
  .install-banner {
    width: calc(100vw - 32px);
    bottom: 76px; /* 避开移动端底部 TabBar */
  }
  .install-text span {
    display: none;
  }
  /* 兜底引导的说明文字在手机上必须保留，否则用户看不到操作步骤 */
  .install-guide .install-text span {
    display: block;
    font-size: 11px;
  }
}
</style>
