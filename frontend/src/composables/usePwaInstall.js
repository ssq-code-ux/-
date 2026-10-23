/**
 * PWA 安装能力（全局单例）
 *
 * 背景知识：
 * - Chrome/Edge/安卓 WebView 满足条件（HTTPS + manifest + 已注册 SW）时才会派发
 *   beforeinstallprompt 事件，且用户拒绝过一次后 Chrome 会记住、短期不再派发
 * - iOS Safari 完全不派发该事件，只能引导用户「分享 → 添加到主屏幕」
 * - 开发模式（npm run dev）或 HTTP 环境下 SW 不会注册，事件永远不会来
 *
 * 因此页面里的自动横幅 + 顶栏手动按钮共用这一份状态与逻辑。
 */
import { ref, computed } from 'vue';

const deferredPrompt = ref(null);
const installedAt = ref(null); // 'banner' | null，仅用于横幅隐藏判断

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault(); // 拦截浏览器默认弹窗，展示时机由页面控制
    deferredPrompt.value = e;
  });
  window.addEventListener('appinstalled', () => {
    deferredPrompt.value = null;
    installedAt.value = 'banner';
  });
}

/** 是否已处于安装后的独立窗口模式（含 iOS Safari 的 navigator.standalone） */
export function isStandalone() {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(display-mode: standalone)').matches
    || window.navigator.standalone === true;
}

/** iOS / iPadOS 检测（iPadOS 13+ 伪装成 Mac，需用触点数区分） */
export function isIOS() {
  if (typeof navigator === 'undefined') return false;
  return /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

/**
 * 主动触发安装：
 * - standalone：已装过，无需处理
 * - accepted：用户在系统弹窗里点了安装
 * - dismissed：用户在系统弹窗里取消
 * - unsupported：当前浏览器不派发安装事件（iOS / 国产浏览器 / 未注册 SW 等），需走引导
 */
export async function promptInstall() {
  if (isStandalone()) return { status: 'standalone' };
  if (!deferredPrompt.value) return { status: 'unsupported' };
  const prompt = deferredPrompt.value;
  deferredPrompt.value = null;
  try {
    prompt.prompt();
    const { outcome } = await prompt.userChoice;
    return { status: outcome === 'accepted' ? 'accepted' : 'dismissed' };
  } catch (e) {
    return { status: 'unsupported' };
  }
}

/** 浏览器当前是否支持一键安装（决定按钮形态：直接装 / 显示引导） */
export function usePwaInstall() {
  const canInstall = computed(() => !!deferredPrompt.value);
  return { canInstall, promptInstall, isStandalone, isIOS };
}
