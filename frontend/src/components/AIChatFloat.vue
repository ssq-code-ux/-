<template>
  <div class="ai-chat-float" :style="fabStyle">
    <!-- 悬浮按钮（可拖动，防止遮挡操作区域） -->
    <div v-if="!open" class="ai-fab" :class="{ dragging }" @pointerdown="onDragStart" @click="onFabClick" title="AI 学习助手（可拖动）">
      <img class="ai-fab-logo" :src="aiLogo" alt="AI 学习助手" draggable="false" />
      <span class="ai-fab-status" title="助手在线"></span>
    </div>

    <!-- 聊天面板（跟随悬浮球位置弹出，JS 计算位置保证始终完整可见） -->
    <transition name="slide-up">
      <div v-if="open" class="ai-panel" :style="panelStyle">
        <div class="ai-panel-header">
          <span class="ai-header-title">
            <img class="ai-header-logo" :src="aiLogo" alt="" />
            AI 学习助手
          </span>
          <button class="ai-close-btn" type="button" title="关闭 (Esc)" @click.stop="closePanel">×</button>
        </div>

        <!-- 消息列表 -->
        <div class="ai-messages" ref="msgList">
          <div v-for="(msg, i) in messages" :key="i" :class="['ai-msg', msg.role]">
            <div class="ai-msg-bubble">
              <span v-html="renderMarkdown(msg.content)"></span>
              <div v-if="msg.action" class="ai-action-badge">
                已执行：{{ actionLabel(msg.action) }}
              </div>
            </div>
          </div>
          <div v-if="loading" class="ai-msg assistant">
            <div class="ai-msg-bubble typing">思考中...</div>
          </div>
        </div>

        <!-- 快捷按钮（按角色区分） -->
        <div class="ai-quick-actions">
          <template v-if="role === 'teacher'">
            <el-button size="small" text @click="sendQuick('查看我的学生列表')">学生列表</el-button>
            <el-button size="small" text @click="sendQuick('打开词汇管理')">词汇管理</el-button>
          </template>
          <template v-else-if="role === 'parent'">
            <el-button size="small" text @click="sendQuick('孩子最近作业完成得怎么样？')">作业情况</el-button>
            <el-button size="small" text @click="sendQuick('怎样帮助孩子学英语？')">辅导建议</el-button>
          </template>
          <template v-else>
            <el-button size="small" text @click="sendQuick('我还有几份作业没交？')">作业进度</el-button>
            <el-button size="small" text @click="sendQuick('我要开始词汇测验')">词汇测验</el-button>
            <el-button size="small" text @click="sendQuick('我要听写单词')">听写模式</el-button>
            <el-button size="small" text @click="sendQuick('怎样高效背单词？')">学习技巧</el-button>
          </template>
        </div>

        <!-- 输入区 -->
        <div class="ai-input-area">
          <el-input v-model="input" placeholder="说你想做的，我直接帮你搞定..."
            @keyup.enter="onEnter" :disabled="loading" />
          <el-button type="primary" size="small" @click="send" :loading="loading">发送</el-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '../stores/user';
import { aiChat } from '../api';
import { ElMessage } from 'element-plus';
import aiLogo from '../assets/ai-logo.png';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const open = ref(false);
const input = ref('');
const loading = ref(false);
const messages = ref([]);
const msgList = ref(null);
const history = ref([]);
const role = computed(() => userStore.user?.role || 'student');

// ==================== 悬浮球拖动（防遮挡） ====================
const pos = ref(null); // {x, y} 左上角坐标；null = 默认右下角
const dragging = ref(false);
let dragStartX = 0, dragStartY = 0, startPosX = 0, startPosY = 0, moved = false;
const FAB_SIZE = 58;
const POS_KEY = 'ai-fab-pos';

const fabStyle = computed(() => {
  if (!pos.value) return {};
  return { left: pos.value.x + 'px', top: pos.value.y + 'px', right: 'auto', bottom: 'auto' };
});

// ==================== 面板定位（始终完整可见） ====================
const PANEL_W = 400;
const PANEL_H = 560;
const panelStyle = ref({});

/**
 * 计算面板位置：跟随悬浮球所在半边弹出（下半屏向上弹、上半屏向下弹），
 * 并把宽高约束在视口内 —— 否则悬浮球拖到屏幕上方时，
 * 面板头部连同关闭按钮会被顶出屏幕，导致面板无法关闭
 */
function updatePanelStyle() {
  // 手机走 CSS 媒体查询布局，不用 JS 定位
  if (window.innerWidth <= 480) { panelStyle.value = {}; return; }
  const p = currentFabPos();
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const gap = 12;
  const h = Math.min(PANEL_H, vh - gap * 2);
  let top;
  if (p.y + FAB_SIZE / 2 >= vh / 2) {
    // 悬浮球在下半屏：面板从悬浮球上方弹出
    top = p.y - gap - h;
  } else {
    // 悬浮球在上半屏：面板改到悬浮球下方弹出，保证头部（含关闭按钮）可见
    top = p.y + FAB_SIZE + gap;
  }
  let left = p.x + FAB_SIZE - PANEL_W; // 默认与悬浮球右对齐
  // 最终兑底：完整约束在视口内
  left = Math.min(Math.max(gap, left), Math.max(gap, vw - PANEL_W - gap));
  top = Math.min(Math.max(gap, top), Math.max(gap, vh - h - gap));
  panelStyle.value = { left: left + 'px', top: top + 'px', width: PANEL_W + 'px', height: h + 'px' };
}

function currentFabPos() {
  if (pos.value) return { ...pos.value };
  return { x: window.innerWidth - FAB_SIZE - 24, y: window.innerHeight - FAB_SIZE - 24 };
}

function clampPos(x, y) {
  return {
    x: Math.min(Math.max(8, x), window.innerWidth - FAB_SIZE - 8),
    y: Math.min(Math.max(8, y), window.innerHeight - FAB_SIZE - 8)
  };
}

function onDragStart(e) {
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  const p = currentFabPos();
  dragStartX = e.clientX; dragStartY = e.clientY;
  startPosX = p.x; startPosY = p.y;
  moved = false;
  dragging.value = true;
  window.addEventListener('pointermove', onDragMove);
  window.addEventListener('pointerup', onDragEnd);
}

function onDragMove(e) {
  const dx = e.clientX - dragStartX;
  const dy = e.clientY - dragStartY;
  if (Math.abs(dx) + Math.abs(dy) > 5) moved = true;
  if (!moved) return;
  pos.value = clampPos(startPosX + dx, startPosY + dy);
}

function onDragEnd() {
  window.removeEventListener('pointermove', onDragMove);
  window.removeEventListener('pointerup', onDragEnd);
  dragging.value = false;
  if (moved && pos.value) {
    try { localStorage.setItem(POS_KEY, JSON.stringify(pos.value)); } catch (e) { /* 忽略存储失败 */ }
  }
}

function closePanel() {
  open.value = false;
}

// 拖动后不触发点击打开面板，只有纯点击才打开
function onFabClick() {
  if (moved) { moved = false; return; }
  updatePanelStyle();
  open.value = true;
  // 首次打开：按角色显示欢迎语，避免空白面板
  if (!messages.value.length) {
    const name = userStore.user?.realName;
    const hints = {
      teacher: '可以让我帮你查学生情况、布置作业、添加单词和资料，直接说需求就行～',
      parent: '可以问我孩子的作业、测验情况，或者聊聊怎么辅导孩子学英语～',
      student: '可以让我帮你查作业、搜单词、开始测验，也可以问我英语问题～'
    };
    messages.value.push({
      role: 'assistant',
      content: `你好${name ? '，' + name : ''}！我是 AI 学习助手 \n${hints[role.value] || hints.student}`
    });
  }
}

function onResize() {
  if (pos.value) pos.value = clampPos(pos.value.x, pos.value.y);
  // 面板开着时窗口尺寸变化，重新约束位置，防止头部被顶出屏幕
  if (open.value) updatePanelStyle();
}

// Esc 键关闭面板（备用关闭途径）
function onKeydown(e) {
  if (e.key === 'Escape' && open.value) closePanel();
}

onMounted(() => {
  try {
    const saved = localStorage.getItem(POS_KEY);
    if (saved) {
      const p = JSON.parse(saved);
      if (typeof p.x === 'number' && typeof p.y === 'number') pos.value = clampPos(p.x, p.y);
    }
  } catch (e) { /* 忽略脏数据 */ }
  window.addEventListener('resize', onResize);
  window.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  window.removeEventListener('keydown', onKeydown);
  window.removeEventListener('pointermove', onDragMove);
  window.removeEventListener('pointerup', onDragEnd);
});

function getContext() {
  return {
    role: userStore.user?.role || 'student',
    page: route.meta?.tab || route.path,
    userName: userStore.user?.realName || '',
    userId: String(userStore.user?.id || '')
  };
}

function actionLabel(action) {
  if (!action) return '';
  const map = {
    navigate: '页面跳转',
    openDialog: '打开对话框',
    search: '搜索'
  };
  return map[action.type] || action.type;
}

async function sendQuick(text) {
  input.value = text;
  await send();
}

// 中文输入法选词确认时的回车不发送，避免误发半截文本
function onEnter(e) {
  if (e.isComposing || e.keyCode === 229) return;
  send();
}

async function send() {
  const text = input.value.trim();
  if (!text || loading.value) return;
  input.value = '';

  messages.value.push({ role: 'user', content: text });
  history.value.push({ role: 'user', content: text });
  loading.value = true;
  await scrollBottom();

  try {
    const ctx = getContext();
    const { reply, action } = await aiChat(text, history.value, ctx);
    messages.value.push({ role: 'assistant', content: reply, action });
    history.value.push({ role: 'assistant', content: reply });
    // 限制历史长度，避免长时间聊天后请求体越来越大
    if (history.value.length > 20) history.value = history.value.slice(-20);

    // 执行操作！（家长端没有对应的教师/学生页面，忽略跳转指令）
    if (action && ctx.role !== 'parent') {
      await executeAction(action);
    }
  } catch (err) {
    messages.value.push({ role: 'assistant', content: '抱歉，网络出问题了，请稍后重试 ' });
  } finally {
    loading.value = false;
    await scrollBottom();
  }
}

// 各角色允许跳转的页面白名单（AI 可能编造路径，跳转前必须校验）
const VALID_PATHS = {
  teacher: ['/teacher/students', '/teacher/homework', '/teacher/vocabulary', '/teacher/words', '/teacher/resources'],
  student: ['/student/dashboard', '/student/homework', '/student/vocabulary', '/student/flashcards', '/student/resources']
};

/**
 * 校验并修正 AI 返回的路径：
 * 1. 在当前角色白名单内 → 直接用
 * 2. AI 给错角色前缀（如学生收到 /teacher/vocabulary）→ 映射到本角色同名页面
 * 3. 都不行 → 返回 null，调用方放弃跳转（避免 push 未知路径白屏）
 */
function resolvePath(rawPath, userRole) {
  const list = VALID_PATHS[userRole] || [];
  if (list.includes(rawPath)) return rawPath;
  const page = String(rawPath || '').split('/').filter(Boolean).pop();
  return list.find(p => p.endsWith('/' + page)) || null;
}

/**
 * 执行 AI 返回的操作指令
 */
async function executeAction(action) {
  if (action.type !== 'navigate') return;

  const targetPath = resolvePath(action.path, role.value);
  if (!targetPath) {
    ElMessage.warning('AI 返回的页面无法识别，已忽略跳转');
    return;
  }

  const query = {};
  if (action.studentName) query.aiStudent = action.studentName;
  if (action.openProfile) query.aiOpenProfile = 'true';
  if (action.openDialog) query.aiDialog = action.openDialog;
  if (action.prefillWord) query.aiPrefill = action.prefillWord;
  if (action.searchKeyword) query.aiSearch = action.searchKeyword;
  // 时间戳保证 query 必变：同页面重复发相同指令时，目标页 watch(route.query) 也能触发
  query._ai = String(Date.now());

  // 如果有预填数据（资料内容、作业内容等），存入 sessionStorage
  if (action.prefill) {
    sessionStorage.setItem('aiPrefill', JSON.stringify(action.prefill));
  }

  try {
    if (route.path === targetPath) {
      await router.replace({ path: targetPath, query });
    } else {
      await router.push({ path: targetPath, query });
    }
    ElMessage.success(`已跳转到${getPageName(targetPath)}`);
  } catch (e) {
    ElMessage.warning(`页面跳转失败，请手动前往“${getPageName(targetPath)}”`);
  }
}

function getPageName(path) {
  const map = {
    '/teacher/students': '学生管理',
    '/teacher/homework': '作业管理',
    '/teacher/vocabulary': '词汇管理',
    '/teacher/words': '背单词',
    '/teacher/resources': '学习资料',
    '/student/dashboard': '学习看板',
    '/student/homework': '我的作业',
    '/student/vocabulary': '词汇学习',
    '/student/flashcards': '记忆卡片',
    '/student/resources': '学习资料'
  };
  return map[path] || path;
}

async function scrollBottom() {
  await nextTick();
  if (msgList.value) {
    msgList.value.scrollTop = msgList.value.scrollHeight;
  }
}

function renderMarkdown(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>');
}
</script>

<style scoped>
.ai-chat-float { position: fixed; right: 24px; bottom: 24px; z-index: 9999; }
.ai-fab {
  position: relative;
  width: 58px; height: 58px; border-radius: 50%;
  background: var(--accent);
  display: flex; align-items: center; justify-content: center;
  color: #fff; cursor: pointer; user-select: none;
  touch-action: none; /* 拖动时不触发页面滚动 */
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.25s ease;
}
/* 拖动中：取消悬浮动效，避免跟手延迟 */
.ai-fab.dragging {
  transition: none;
  transform: scale(1.08);
}
.ai-fab-logo {
  width: 30px; height: 30px; border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.95);
  object-fit: cover;
}
/* 呼吸光环 */
.ai-fab::before {
  content: ''; position: absolute; inset: -5px; border-radius: 50%;
  border: 2px solid rgba(118, 75, 162, 0.5);
  animation: ai-halo 2.6s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  pointer-events: none;
}
@keyframes ai-halo {
  0% { transform: scale(0.85); opacity: 0.9; }
  70%, 100% { transform: scale(1.3); opacity: 0; }
}
/* 玻璃高光 */
.ai-fab::after {
  content: ''; position: absolute; inset: 0; border-radius: 50%;
  
  pointer-events: none;
}
/* 在线状态点 */
.ai-fab-status {
  position: absolute; right: 3px; bottom: 3px; width: 13px; height: 13px;
  border-radius: 50%; background: #4ade80; border: 2.5px solid #fff;
  animation: ai-status 2.2s ease-out infinite;
}
@keyframes ai-status {
  0% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0.6); }
  70% { box-shadow: 0 0 0 7px rgba(74, 222, 128, 0); }
  100% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0); }
}
.ai-fab:hover { transform: translateY(-3px) scale(1.05); box-shadow: 0 10px 30px rgba(102, 126, 234, 0.6); }
.ai-fab:active { transform: scale(0.95); box-shadow: 0 4px 14px rgba(102, 126, 234, 0.4); }

/* fixed + JS 计算位置（panelStyle）：面板始终完整出现在视口内，
   头部关闭按钮不会被顶出屏幕外 */
.ai-panel {
  position: fixed; width: 400px; height: 560px;
  background: var(--surface); border-radius: 16px; box-shadow: 0 8px 40px rgba(0,0,0,0.15);
  display: flex; flex-direction: column; overflow: hidden;
}
/* 关闭按钮：原生 button，命中区域足够大，不依赖组件库 */
.ai-close-btn {
  width: 30px; height: 30px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 50%;
  background: rgba(255, 255, 255, 0.2); color: #fff;
  font-size: 15px; line-height: 1; cursor: pointer;
  transition: background 0.2s, transform 0.1s;
}
.ai-close-btn:hover { background: rgba(255, 255, 255, 0.35); }
.ai-close-btn:active { transform: scale(0.92); }
.ai-panel-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; background: var(--accent);
  color: #fff; font-weight: 600; font-size: 15px;
}
.ai-header-title { display: flex; align-items: center; gap: 8px; }
.ai-header-logo {
  width: 20px; height: 20px; flex-shrink: 0;
  border-radius: 50%; border: 1.5px solid rgba(255, 255, 255, 0.9);
  object-fit: cover;
}
.ai-messages { flex: 1; overflow-y: auto; padding: 14px; background: var(--raised); }
.ai-msg { margin-bottom: 12px; display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-msg-bubble {
  max-width: 85%; padding: 10px 14px; border-radius: 14px;
  font-size: 14px; line-height: 1.6; word-break: break-word;
}
.ai-msg.user .ai-msg-bubble { background: var(--accent); color: #fff; border-bottom-right-radius: 4px; }
.ai-msg.assistant .ai-msg-bubble {
  background: var(--surface); color: var(--text); border: 1px solid var(--border);
  border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.ai-action-badge {
  margin-top: 8px; padding: 4px 10px; background: var(--accent-soft);
  border-radius: 6px; font-size: 12px; color: var(--accent);
}
.typing { color: var(--muted); font-style: italic; }
.ai-quick-actions {
  display: flex; gap: 4px; padding: 6px 14px;
  border-top: 1px solid var(--border); overflow-x: auto; flex-shrink: 0;
}
.ai-quick-actions .el-button { white-space: nowrap; font-size: 12px; }
.ai-input-area {
  display: flex; gap: 8px; align-items: center;
  padding: 10px 14px; border-top: 1px solid var(--border);
}
.slide-up-enter-active, .slide-up-leave-active { transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease; }
.slide-up-enter-from, .slide-up-leave-to { opacity: 0; transform: translateY(20px); }

@media (max-width: 480px) {
  /* 手机上聊天面板固定占满屏幕下部，不再跟随悬浮球位置；
     高度用 dvh 自适应软键盘，bottom 避开底部 TabBar */
  .ai-panel {
    position: fixed; left: 10px; right: 10px; width: auto; top: auto;
    height: min(480px, calc(100dvh - 170px));
    bottom: calc(76px + env(safe-area-inset-bottom));
  }
  .ai-fab { width: 50px; height: 50px; }
  .ai-fab-logo { width: 25px; height: 25px; }
  .ai-fab-status { width: 11px; height: 11px; right: 2px; bottom: 2px; }
}

@media (max-width: 768px) {
  /* 默认位置抬高，避开底部 TabBar（悬浮球仍可拖动） */
  .ai-chat-float { bottom: calc(84px + env(safe-area-inset-bottom)); }
}
</style>
