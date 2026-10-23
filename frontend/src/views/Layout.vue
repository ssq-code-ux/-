<template>
  <el-container class="min-h-screen relative" :class="themeClass">
    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && !sidebarCollapsed"
      class="fixed inset-0 bg-black/40 z-998"
      @click="sidebarCollapsed = true"
    />

    <!-- 侧边栏 — Glass 深色半透明 -->
    <el-aside
      :width="sidebarCollapsed ? '0px' : '220px'"
      class="sidebar-glass"
      :class="{ 'sidebar-mobile': isMobile, 'sidebar-open': !sidebarCollapsed }"
    >
      <div class="sidebar-logo" @click="isMobile || (sidebarCollapsed = !sidebarCollapsed)">
        <span class="brand-mark">智</span>
        <span v-if="!sidebarCollapsed" class="brand-name">智乐学</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isMobile ? false : sidebarCollapsed"
        @select="handleMenuSelect"
        background-color="transparent"
        text-color="#52525b"
        active-text-color="#b8336a"
        class="sidebar-menu"
      >
        <template v-if="role === 'teacher'">
          <el-menu-item index="/teacher/home">
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <el-menu-item index="/teacher/students">
            <el-icon><User /></el-icon>
            <span>学生管理</span>
          </el-menu-item>
          <el-menu-item index="/teacher/homework">
            <el-icon><Document /></el-icon>
            <span>作业管理</span>
          </el-menu-item>
          <el-menu-item index="/teacher/vocabulary">
            <el-icon><Collection /></el-icon>
            <span>词汇管理</span>
          </el-menu-item>
          <el-menu-item index="/teacher/words">
            <el-icon><Reading /></el-icon>
            <span>背单词</span>
          </el-menu-item>
          <el-menu-item index="/teacher/resources">
            <el-icon><FolderOpened /></el-icon>
            <span>学习资料</span>
          </el-menu-item>
          <el-menu-item index="/teacher/jobs">
            <el-icon><Briefcase /></el-icon>
            <span>接单广场</span>
          </el-menu-item>
        </template>

        <template v-else-if="role === 'admin'">
          <el-menu-item index="/admin/dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>数据看板</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/jobs">
            <el-icon><Briefcase /></el-icon>
            <span>家教信息</span>
          </el-menu-item>
          <el-menu-item index="/admin/partner">
            <el-icon><Share /></el-icon>
            <span>同行合作单</span>
          </el-menu-item>
          <el-menu-item index="/admin/demands">
            <el-icon><Promotion /></el-icon>
            <span>家长需求</span>
          </el-menu-item>
          <el-menu-item index="/admin/refunds">
            <el-icon><RefreshLeft /></el-icon>
            <span>退费工单</span>
          </el-menu-item>
          <el-menu-item index="/admin/payments">
            <el-icon><CreditCard /></el-icon>
            <span>缴费流水</span>
          </el-menu-item>
          <el-menu-item index="/admin/templates">
            <el-icon><Files /></el-icon>
            <span>科目模板</span>
          </el-menu-item>
          <el-menu-item index="/admin/config">
            <el-icon><Setting /></el-icon>
            <span>接单配置</span>
          </el-menu-item>
        </template>

        <template v-else-if="role === 'parent'">
          <el-menu-item index="/parent">
            <el-icon><User /></el-icon>
            <span>孩子的学习情况</span>
          </el-menu-item>
          <el-menu-item index="/parent/tutor">
            <el-icon><Search /></el-icon>
            <span>找家教</span>
          </el-menu-item>
        </template>

        <template v-else>
          <el-menu-item index="/student/dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>学习看板</span>
          </el-menu-item>
          <el-menu-item index="/student/homework">
            <el-icon><Document /></el-icon>
            <span>我的作业</span>
          </el-menu-item>
          <el-menu-item index="/student/vocabulary">
            <el-icon><Collection /></el-icon>
            <span>词汇学习</span>
          </el-menu-item>
          <el-menu-item index="/student/flashcards">
            <el-icon><Postcard /></el-icon>
            <span>记忆卡片</span>
          </el-menu-item>
          <el-menu-item index="/student/resources">
            <el-icon><FolderOpened /></el-icon>
            <span>学习资料</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="shell-body">
      <!-- 顶部栏 -->
      <el-header class="header-glass">
        <div class="header-left">
          <span class="hamburger-btn" @click="sidebarCollapsed = !sidebarCollapsed">
            <span class="hamburger-icon">{{ sidebarCollapsed ? '≡' : '×' }}</span>
          </span>
          <span class="welcome-text">{{ user?.realName }} · {{ role === 'teacher' ? '老师' : role === 'parent' ? '家长' : role === 'admin' ? '管理员' : '学生' }}</span>
          <span v-if="role === 'student' && user?.teacherName" class="teacher-badge">
            我的老师：{{ user.teacherName }}
            <span class="teacher-phone" v-if="user.teacherPhone">（{{ user.teacherPhone }}）</span>
          </span>
          <span v-else-if="role === 'student' && !user?.teacherName" class="teacher-badge teacher-badge-warn">
            暂未绑定老师
          </span>
        </div>
        <div class="header-right">
          <!-- 站内通知铃铛（三端通用：作业布置/批改/逾期提醒都走这里） -->
          <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadNotifications">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99" class="notify-badge">
                <span class="notify-bell">通知</span>
              </el-badge>
            </template>
            <div class="notify-panel">
              <div class="notify-header">
                <span style="font-weight:600">消息通知</span>
                <span>
                  <el-link type="primary" :underline="false" @click="openPushDialog">微信推送</el-link>
                  <el-link v-if="unreadCount" type="primary" :underline="false" style="margin-left:10px" @click="onMarkAllRead">全部已读</el-link>
                </span>
              </div>
              <div v-if="!notifyList.length" style="text-align:center;color:var(--muted);padding:24px 0;font-size:13px">暂无通知</div>
              <div v-else class="notify-list">
                <div
                  v-for="n in notifyList"
                  :key="n.id"
                  class="notify-item"
                  :class="{ unread: !n.isRead }"
                  @click="onClickNotify(n)"
                >
                  <div class="notify-title">
                    {{ n.title }}
                    <el-tag v-if="!n.isRead" type="danger" size="small">未读</el-tag>
                  </div>
                  <div class="notify-content">{{ n.content }}</div>
                  <div class="notify-time">{{ formatTime(n.createTime) }}</div>
                </div>
              </div>
            </div>
          </el-popover>
          <el-tag :type="role === 'teacher' ? 'warning' : role === 'parent' ? 'primary' : role === 'admin' ? 'danger' : 'success'" size="small">
            {{ role === 'teacher' ? '教师端' : role === 'parent' ? '家长端' : role === 'admin' ? '管理端' : '学生端' }}
          </el-tag>
          <el-button type="primary" text @click="openPwdDialog">修改密码</el-button>
          <el-button type="danger" text @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main-area">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 全局 AI 助手 -->
    <AIChatFloat />

    <!-- 微信推送设置：令牌由用户自己去 PushPlus / Server酱 扫码获取，服务端不存任何密钥 -->
    <el-dialog v-model="pushDialog" title="微信推送设置" :width="isMobile ? '92%' : '520px'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        <template #title>开启后，作业布置、批改结果、截止与逾期提醒会直接推到你的微信</template>
      </el-alert>
      <ol class="push-steps">
        <li>手机微信扫码登录 <el-link type="primary" href="https://www.pushplus.plus/" target="_blank">pushplus.plus</el-link>（或 <el-link type="primary" href="https://sct.ftqq.com/" target="_blank">Server酱</el-link>）</li>
        <li>复制页面上的 token / SendKey</li>
        <li>粘贴到下面保存，再点「发送测试」确认能收到</li>
      </ol>
      <div v-if="pushSetting.configured" class="push-current">
        当前已配置：<b>{{ pushSetting.channel }}</b> · {{ pushSetting.maskedToken }}
      </div>
      <el-input v-model="pushToken" placeholder="粘贴 token / SendKey，留空并保存即关闭推送" clearable />
      <!-- 订阅偏好：勾选即时保存，不想要的那类消息不推微信（站内铃铛不受影响） -->
      <div class="push-prefs">
        <div class="push-prefs-label">订阅哪些提醒（改动即时生效）</div>
        <el-checkbox v-for="p in pushPrefItems" :key="p.key" v-model="pushPrefs[p.key]"
          @change="onPrefChange">{{ p.label }}</el-checkbox>
      </div>
      <el-alert v-if="pushSetting.serviceEnabled === false" type="warning" :closable="false"
        title="服务端已停用微信推送，保存后暂不会实际发送" style="margin-top:12px" />
      <template #footer>
        <el-button @click="pushDialog = false">取消</el-button>
        <el-button :loading="pushTesting" :disabled="!pushSetting.configured" @click="onTestPush">发送测试</el-button>
        <el-button type="primary" :loading="pushSaving" @click="onSavePushToken">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码（教师/学生/家长/管理员通用） -->
    <el-dialog v-model="pwdDialog" title="修改密码" :width="isMobile ? '92%' : '420px'">
      <el-form :model="pwdForm" label-position="top">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前登录密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="onChangePassword">确定修改</el-button>
      </template>
    </el-dialog>

    <!-- 移动端底部 TabBar（学生/老师），体验更接近原生 App -->
    <nav v-if="isMobile && role !== 'parent'" class="mobile-tabbar">
      <div
        v-for="tab in mobileTabs"
        :key="tab.path"
        class="tabbar-item"
        :class="{ active: route.path === tab.path }"
        @click="router.push(tab.path)"
      >
        <span class="tabbar-icon">{{ tab.icon }}</span>
        <span class="tabbar-label">{{ tab.label }}</span>
      </div>
    </nav>
  </el-container>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '../stores/user';
import { ElMessage, ElMessageBox } from 'element-plus';
import AIChatFloat from '../components/AIChatFloat.vue';
import {
  User, Document, Collection, Reading, FolderOpened, Briefcase, House,
  Share, Promotion, RefreshLeft, CreditCard, Files, Setting, DataBoard, Search, Postcard
} from '@element-plus/icons-vue';
import {
  myNotifications, notifyUnreadCount, notifyMarkRead, notifyMarkAllRead,
  getPushSetting, savePushToken, testPush, saveNotifyPrefs, changeMyPassword
} from '../api';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const user = computed(() => userStore.user);
const role = computed(() => userStore.role);

// 四端专属背景主题：学生蓝紫活力 / 教师蓝绿专业 / 家长暖橙温馨 / 管理端冷静灰蓝
const themeClass = computed(() => ({
  'theme-bg-student': role.value === 'student',
  'theme-bg-teacher': role.value === 'teacher',
  'theme-bg-parent': role.value === 'parent',
  'theme-bg-admin': role.value === 'admin'
}));

const isMobile = ref(false);
const sidebarCollapsed = ref(false);

function checkMobile() {
  isMobile.value = window.innerWidth < 768;
  sidebarCollapsed.value = isMobile.value;
}

let resizeTimer;
function onResize() {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(checkMobile, 100);
}

onMounted(() => {
  checkMobile();
  window.addEventListener('resize', onResize);
  // 未读数 + 每 60 秒轮询一次新通知（三端均开启）
  refreshUnread();
  notifyTimer = setInterval(refreshUnread, 60000);
  // 版本标记：F12 控制台查看，确认加载的是最新版本
  console.log('智乐学 构建时间: ' + __BUILD_TIME__);
  document.title = '智乐学 (' + __BUILD_TIME__.slice(5, 10).replace('/', '-') + ')';
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  clearTimeout(resizeTimer);
  if (notifyTimer) clearInterval(notifyTimer);
});

const activeMenu = computed(() => route.path);

// 移动端底部 TabBar 项（按角色）
const mobileTabs = computed(() => {
  if (role.value === 'admin') {
    return [
      { path: '/admin/users', icon: '', label: '用户' },
      { path: '/admin/jobs', icon: '', label: '家教' },
      { path: '/admin/demands', icon: '', label: '需求' },
      { path: '/admin/refunds', icon: '', label: '退费' },
      { path: '/admin/dashboard', icon: '', label: '看板' }
    ];
  }
  if (role.value === 'teacher') {
    return [
      { path: '/teacher/students', icon: '', label: '学生' },
      { path: '/teacher/homework', icon: '', label: '作业' },
      { path: '/teacher/words', icon: '', label: '背单词' },
      { path: '/teacher/jobs', icon: '', label: '接单' },
      { path: '/teacher/resources', icon: '', label: '资料' }
    ];
  }
  return [
    { path: '/student/dashboard', icon: '', label: '看板' },
    { path: '/student/homework', icon: '', label: '作业' },
    { path: '/student/vocabulary', icon: '', label: '词汇' },
    { path: '/student/flashcards', icon: '', label: '卡片' }
  ];
});

function handleMenuSelect(index) {
  router.push(index);
  if (isMobile.value) {
    sidebarCollapsed.value = true;
  }
}

// ==================== 修改密码（三端通用） ====================
const pwdDialog = ref(false);
const pwdSaving = ref(false);
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

function openPwdDialog() {
  pwdForm.oldPassword = '';
  pwdForm.newPassword = '';
  pwdForm.confirmPassword = '';
  pwdDialog.value = true;
}

async function onChangePassword() {
  if (!pwdForm.oldPassword) { ElMessage.warning('请输入原密码'); return; }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) { ElMessage.warning('新密码至少 6 位'); return; }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) { ElMessage.warning('两次输入的新密码不一致'); return; }
  pwdSaving.value = true;
  try {
    await changeMyPassword(pwdForm.oldPassword, pwdForm.newPassword);
    ElMessage.success('密码修改成功，请牢记新密码');
    pwdDialog.value = false;
  } catch (e) { /* 错误信息由拦截器提示 */ } finally {
    pwdSaving.value = false;
  }
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout();
  }).catch(() => {});
}

// ==================== 站内通知（三端通用） ====================
const unreadCount = ref(0);
const notifyList = ref([]);
let notifyTimer = null;

async function refreshUnread() {
  try { unreadCount.value = await notifyUnreadCount(); } catch (e) { /* 静默失败 */ }
}

async function loadNotifications() {
  try { notifyList.value = await myNotifications(); } catch (e) { notifyList.value = []; }
}

async function onClickNotify(n) {
  if (n.isRead) return;
  try {
    await notifyMarkRead(n.id);
    n.isRead = 1;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  } catch (e) { /* 错误信息由拦截器提示 */ }
}

async function onMarkAllRead() {
  try {
    await notifyMarkAllRead();
    notifyList.value.forEach(n => (n.isRead = 1));
    unreadCount.value = 0;
  } catch (e) { /* 错误信息由拦截器提示 */ }
}

// ==================== 微信推送设置 ====================
const pushDialog = ref(false);
const pushSetting = ref({ serviceEnabled: true, configured: false, maskedToken: '', channel: '' });
const pushToken = ref('');
const pushSaving = ref(false);
const pushTesting = ref(false);

/** 订阅项清单（key 与后端 PREF_ITEMS 对齐，新增通知类型时两边同步加；周报仅家长可订阅） */
const pushPrefItems = computed(() => {
  const items = [
    { key: 'homework_assigned', label: '新作业布置' },
    { key: 'homework_reviewed', label: '批改结果' },
    { key: 'homework_deadline', label: '截止前提醒' },
    { key: 'homework_overdue', label: '逾期未交提醒' }
  ];
  if (role.value === 'parent') items.push({ key: 'weekly_report', label: '每周学习周报' });
  return items;
});
const pushPrefs = reactive({});

async function openPushDialog() {
  pushDialog.value = true;
  pushToken.value = '';
  try {
    pushSetting.value = await getPushSetting();
    // 用服务端返回的偏好初始化勾选；没返回的 key 落在默认值 true（与后端「没存过=全订」一致）
    pushPrefItems.value.forEach(p => { pushPrefs[p.key] = pushSetting.value.prefs ? pushSetting.value.prefs[p.key] !== false : true; });
  } catch (e) { /* 错误信息由拦截器提示 */ }
}

/** 勾选/取消勾选即时保存，不用点「保存」按钮 */
async function onPrefChange() {
  try {
    await saveNotifyPrefs({ ...pushPrefs });
    ElMessage.success('订阅偏好已更新');
  } catch (e) { /* 错误信息由拦截器提示 */ }
}

async function onSavePushToken() {
  pushSaving.value = true;
  try {
    await savePushToken(pushToken.value.trim());
    ElMessage.success(pushToken.value.trim() ? '已保存，建议点「发送测试」确认' : '已关闭微信推送');
    pushToken.value = '';
    pushSetting.value = await getPushSetting();
  } catch (e) { /* 错误信息由拦截器提示 */ } finally {
    pushSaving.value = false;
  }
}

async function onTestPush() {
  pushTesting.value = true;
  try {
    // 失败时后端抛出带具体原因的错误（如「PushPlus 903 令牌无效」），由拦截器提示
    await testPush();
    ElMessage.success('已发送，请查看微信');
  } catch (e) { /* 错误信息由拦截器提示 */ } finally {
    pushTesting.value = false;
  }
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
/* ═══════════════ 三端主题背景（多层渐变+漂移光斑，纯 CSS 无图片依赖） ═══════════════ */
.theme-bg-student {
  background: var(--color-bg);
}
.theme-bg-teacher {
  background: var(--color-bg);
}
.theme-bg-parent {
  background: var(--color-bg);
}
.theme-bg-admin {
  background: var(--color-bg);
}



/* ═══════════════ 侧边栏 — 浅色渐变，随角色换色 ═══════════════ */
.sidebar-glass {
  /* 白色侧栏，与浅蓝底分离 */
  background: var(--surface) !important;
  border-right: 1px solid var(--color-border);
  overflow: hidden;
  transition: width 0.3s var(--ease-out);
  position: relative;
  z-index: 2;
}
.shell-body {
  position: relative;
  z-index: 1;
}
.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--color-text-primary);
  cursor: pointer;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}
/* 产品标记：名字首字的柔和色块（tasteskill：给产品一个属于它的标记） */
.brand-mark {
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 15px; font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(47, 143, 240, 0.3); /* taste-skill-ignore */
}
.brand-name { font-size: 15px; }
.sidebar-glass :deep(.el-menu) {
  border-right: none;
  background: transparent;
}
.sidebar-glass :deep(.el-menu-item) {
  position: relative;
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: var(--radius-md);
  color: var(--color-text-regular);
  font-size: var(--fs-base);
  transition: background-color var(--t-hover) ease, color var(--t-hover) ease;
}
.sidebar-glass :deep(.el-menu-item .el-icon) {
  width: 16px;
  margin-right: 10px;
  font-size: 16px;
}
.sidebar-glass :deep(.el-menu-item:hover) {
  background: var(--color-surface) !important;
  color: var(--color-text-primary);
}
/* 激活项：品牌色浅底 + 左侧色条 */
.sidebar-glass :deep(.el-menu-item.is-active) {
  background: var(--accent-soft) !important;
  color: var(--accent-ink);
  font-weight: 600;
}
.sidebar-glass :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--accent);
}

/* ═══════════════ 顶部栏 — 平、中立、1px 底边 ═══════════════ */
.header-glass {
  background: var(--surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 60px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  flex: 1 1 auto;
}
.welcome-text {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  color: var(--color-text-primary);
}

.teacher-badge {
  margin-left: 12px;
  padding: 3px 10px;
  background: var(--color-surface);
  border-radius: var(--radius-full);
  font-size: 12px;
  color: var(--color-text-regular);
  white-space: nowrap;
}
.teacher-badge-warn {
  color: var(--color-warning) !important;
  background: rgba(181, 117, 26, 0.1);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 通知铃铛 */
.notify-badge {
  margin-right: 4px;
}
.notify-bell {
  font-size: 20px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
  line-height: 1;
  display: inline-block;
}
.notify-bell:hover {
  background: rgba(0, 0, 0, 0.05);
}

.notify-panel {
  max-height: 420px;
  display: flex;
  flex-direction: column;
}
.notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 4px;
}
.notify-list {
  overflow-y: auto;
  max-height: 360px;
}
.notify-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.notify-item:hover {
  background: var(--raised);
}
.notify-item.unread {
  background: rgba(64, 158, 255, 0.06);
}
.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.notify-content {
  font-size: 13px;
  color: var(--color-text-regular);
  margin-top: 4px;
  line-height: 1.5;
}
.notify-time {
  font-size: 12px;
  color: var(--control);
  margin-top: 4px;
}

/* 微信推送设置弹窗 */
.push-steps {
  margin: 0 0 12px;
  padding-left: 20px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 2;
}
.push-current {
  font-size: 13px;
  color: var(--positive);
  margin-bottom: 8px;
}
/* 订阅偏好勾选区：一行摆得下就横排，窄屏自动换行 */
.push-prefs {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--raised);
  border-radius: 8px;
}
.push-prefs-label {
  font-size: 13px;
  color: var(--color-text-regular);
  margin-bottom: 6px;
}
.push-prefs .el-checkbox {
  margin-right: 16px;
}

/* 汉堡按钮 */
.hamburger-btn {
  display: none;
  cursor: pointer;
  margin-right: 8px;
  padding: 4px 6px;
  border-radius: 6px;
  line-height: 1;
}
.hamburger-btn:hover {
  background: rgba(0,0,0,0.05);
}
.hamburger-icon {
  font-size: 22px;
  color: var(--text);
}

/* 移动端底部 TabBar */
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 997;
  display: flex;
  background: var(--color-bg-card);

  border-top: 1px solid var(--color-border);
  padding-bottom: env(safe-area-inset-bottom);
}
.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 0 8px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color var(--t-hover) ease;
  user-select: none;
}
.tabbar-item.active { color: var(--color-primary); }
.tabbar-icon { font-size: 20px; line-height: 1.2; }
.tabbar-label { font-size: 11px; }
.tabbar-item.active .tabbar-label { font-weight: 600; }

/* 主内容区：直接在纸底上，靠留白与细线分区，不套漂浮卡片 */
.main-area {
  background: transparent !important;
  margin: 0;
  padding: 24px 28px 32px;
  min-height: calc(100vh - 60px);
  border: none;
  border-radius: 0;
}

/* 过渡 */
.fade-enter-active,
.fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

/* ═══════════════ 移动端 ═══════════════ */
@media (max-width: 768px) {
  .hamburger-btn { display: inline-block; }

  /* 移动端关闭漂移光晕动画，节省性能 */
  .min-h-screen::before { animation: none; }

  .sidebar-glass.sidebar-mobile {
    position: fixed;
    left: 0; top: 0; bottom: 0;
    z-index: 999;
    width: 220px !important;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  .sidebar-glass.sidebar-mobile.sidebar-open {
    transform: translateX(0);
    width: 220px !important;
  }
  .sidebar-glass.sidebar-mobile:not(.sidebar-open) { width: 0 !important; }

  .header-glass {
    height: auto; min-height: 60px;
    padding: 8px 12px; gap: 8px;
    flex-wrap: wrap;
  }
  .welcome-text { font-size: 13px; }
  .teacher-badge { margin-left: 6px; font-size: 11px; padding: 2px 6px; }
  .teacher-phone { display: none; }
  .header-right { gap: 6px; }
  .main-area {
    padding: 14px;
    /* 给底部 TabBar 留出空间 */
    padding-bottom: calc(64px + env(safe-area-inset-bottom));
  }
}

@media (max-width: 400px) {
  .teacher-badge { display: none; }
  .welcome-text { font-size: 12px; }
}
</style>
