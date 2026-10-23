<template>
  <div class="login-page" ref="pageEl">
    <!-- 程序化粒子星野（纯 canvas，无 3D 资产） -->
    <canvas ref="field" class="field" aria-hidden="true"></canvas>

    <div class="login-content">
      <!-- 左：影院感 hero -->
      <section class="login-banner">
        <div class="login-banner__brand">
          <span class="brand-dot"></span>
          <span class="brand-name">智乐学</span>
        </div>

        <div class="hero-copy">
          <h2 class="hero-title">让英语进步<br />看得见</h2>
          <p class="hero-sub">
            词汇记忆 · 记忆卡片 · 错题本 · AI 学习助手，<br />按你的节奏，一步一步推进。
          </p>
          <button type="button" class="hero-cta" @click="focusForm">
            <span class="hero-cta__mark"></span>
            开始使用
          </button>
        </div>

        <!-- 浮动信息卡（描述真实能力，不编造数据） -->
        <div class="float-card float-card--a">
          <span class="fc-label">核心能力</span>
          <span class="fc-title">记忆卡片</span>
          <span class="fc-sub">认识 / 不熟，自动归入错题本</span>
        </div>
        <div class="float-card float-card--b">
          <span class="fc-label">AI 助手</span>
          <span class="fc-title">随问随答</span>
          <span class="fc-sub">按你的进度给学习建议</span>
        </div>

        <div class="banner-foot">
          <span>词汇记忆</span>
          <span>记忆卡片</span>
          <span>AI 学习助手</span>
          <span>学习看板</span>
        </div>
      </section>

      <!-- 右：表单区 -->
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo">智</div>
          <h1>智能学习助手</h1>
          <p>个性化学习辅助平台</p>
        </div>

        <!-- Tab 切换 -->
        <el-tabs v-model="activeTab" class="auth-tabs">
          <el-tab-pane label="登 录" name="login" />
          <el-tab-pane label="注 册" name="register" />
        </el-tabs>

        <!-- 登录表单 -->
        <el-form v-if="activeTab === 'login'" :model="loginForm" size="large">
          <el-form-item>
            <el-input ref="userInput" v-model="loginForm.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-btn" @click="doLogin" :loading="loading">
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 注册表单 -->
        <el-form v-else :model="regForm" size="large" label-position="top">
          <el-form-item label="身份">
            <div class="role-grid">
              <div class="role-card" :class="{ 'role-card--active': regForm.role === 'student' }" @click="regForm.role = 'student'">
                <span class="role-name">我是学生</span>
              </div>
              <div class="role-card" :class="{ 'role-card--active': regForm.role === 'teacher' }" @click="regForm.role = 'teacher'">
                <span class="role-name">我是老师</span>
              </div>
              <div class="role-card" :class="{ 'role-card--active': regForm.role === 'parent' }" @click="regForm.role = 'parent'">
                <span class="role-name">我是家长</span>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="用户名">
            <el-input v-model="regForm.username" placeholder="请输入用户名" />
          </el-form-item>

          <el-form-item label="密码">
            <el-input v-model="regForm.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>

          <el-form-item label="真实姓名">
            <el-input v-model="regForm.realName" placeholder="请输入真实姓名" />
          </el-form-item>

          <el-form-item label="手机号">
            <el-input v-model="regForm.phone" placeholder="请输入手机号（可选）" />
          </el-form-item>

          <el-form-item v-if="regForm.role === 'student'" label="选择老师（可选）">
            <el-select v-model="regForm.teacherId" placeholder="暂不选择" clearable class="w-full">
              <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id" />
            </el-select>
          </el-form-item>

          <!-- 家长注册不再要求填孩子账号，注册后到「家长端」自行绑定 -->

          <!-- 老师分享订单时可携带邀请码，注册后双方建立邀请关系 -->
          <el-form-item label="邀请码">
            <el-input v-model="regForm.inviteCode" placeholder="选填，由邀请您的老师提供" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" class="login-btn" @click="doRegister" :loading="regLoading">
              {{ regLoading ? '注册中...' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- App 下载入口 -->
        <div v-if="apkUrl" class="app-download">
          <a class="app-download__btn" :href="apkDownloadUrl" rel="noopener">
            <svg class="app-download__icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path fill="currentColor" d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85-.29-.15-.65-.06-.83.22l-1.88 3.24a11.43 11.43 0 0 0-8.94 0L5.65 5.67c-.19-.29-.58-.38-.87-.2-.28.18-.37.54-.22.83L6.4 9.48A10.81 10.81 0 0 0 1 18h22a10.81 10.81 0 0 0-5.4-8.52zM7 15.25a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5zm10 0a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5z"/>
            </svg>
            <span class="app-download__label">下载安卓 App</span>
            <span v-if="apkVersion" class="app-download__ver">{{ apkVersion }}</span>
          </a>
          <p class="app-download__tip">安装时如提示“未知来源”，请允许后继续</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { login, register, teachers, getAppDownload } from '../api';
import { useUserStore } from '../stores/user';
import { ElMessage } from 'element-plus';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const activeTab = ref('login');
const loading = ref(false);
const regLoading = ref(false);
const teacherList = ref([]);
const apkUrl = ref('');
const apkVersion = ref('');
const apkDownloadUrl = '/api/app/apk?platform=android';

const userInput = ref(null);
const pageEl = ref(null);
const field = ref(null);

function focusForm() { userInput.value?.focus?.(); }

const loginForm = reactive({ username: '', password: '' });
const regForm = reactive({
  username: '',
  password: '',
  realName: '',
  role: 'student',
  phone: '',
  teacherId: null,
  childUsername: '',
  inviteCode: ''
});

onMounted(async () => {
  const inv = route.query.invite || route.query.inviteCode;
  if (inv) {
    regForm.inviteCode = String(inv).trim().toUpperCase();
    regForm.role = 'teacher';
    activeTab.value = 'register';
  }
  try { teacherList.value = await teachers(); } catch (e) { /* ignore */ }
  try {
    const d = await getAppDownload('android');
    if (d && d.hasApk && d.apkUrl) {
      apkUrl.value = d.apkUrl;
      apkVersion.value = d.versionName ? `v${d.versionName}` : '';
    }
  } catch (e) { /* ignore */ }
});

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入账号和密码');
    return;
  }
  loading.value = true;
  try {
    const user = await login(loginForm);
    userStore.login(user);
    ElMessage.success('登录成功');
    router.push(user.role === 'teacher' ? '/teacher' : user.role === 'parent' ? '/parent' : user.role === 'admin' ? '/admin' : '/student');
  } catch (e) { /* handled by interceptor */ }
  finally { loading.value = false; }
}

async function doRegister() {
  if (!regForm.username || !regForm.password || !regForm.realName) {
    ElMessage.warning('请填写用户名、密码和姓名');
    return;
  }
  if (regForm.password.length < 4) {
    ElMessage.warning('密码至少4位');
    return;
  }
  regLoading.value = true;
  try {
    await register({
      username: regForm.username,
      password: regForm.password,
      realName: regForm.realName,
      role: regForm.role,
      phone: regForm.phone,
      teacherId: regForm.teacherId || null,
      childUsername: null,
      inviteCode: regForm.inviteCode.trim() || null
    });
    ElMessage.success('注册成功！请登录');

    loginForm.username = regForm.username;
    loginForm.password = regForm.password;
    activeTab.value = 'login';
  } catch (e) { /* handled by interceptor */ }
  finally { regLoading.value = false; }
}

/* ═══════════════ 程序化粒子星野 ═══════════════
   纯 canvas：无 3D 模型、无第三方库。远/中/近三层 + 鼠标视差 + 呼吸闪烁。
   深色底与暗角用 canvas 自身的径向渐变绘制，避免 CSS 渐变。 */
let raf = 0;
let ctx = null;
let W = 0, H = 0, DPR = 1;
let bgGrad = null;
let glowGrad = null;
let particles = [];
const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const ACCENT = '184, 51, 106';

function countFor(w, h) {
  const n = Math.round((w * h) / 14000);
  return Math.max(60, Math.min(220, n));
}

function spawn() {
  const n = countFor(W, H);
  particles = [];
  for (let i = 0; i < n; i++) {
    const z = Math.random();               // 深度 0(远)~1(近)
    particles.push({
      x: Math.random() * W,
      y: Math.random() * H,
      z,
      r: 0.4 + z * 1.5,
      a: 0.18 + z * 0.55,
      vx: (Math.random() - 0.5) * 0.06,
      vy: -(0.03 + z * 0.10),
      ph: Math.random() * Math.PI * 2,
      accent: Math.random() < 0.10
    });
  }
}

function resize() {
  const c = field.value;
  if (!c) return;
  DPR = Math.min(2, window.devicePixelRatio || 1);
  W = c.clientWidth;
  H = c.clientHeight;
  c.width = Math.floor(W * DPR);
  c.height = Math.floor(H * DPR);
  ctx = c.getContext('2d');
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  bgGrad = ctx.createRadialGradient(W * 0.42, H * 0.36, 40, W * 0.5, H * 0.5, Math.max(W, H) * 0.8);
  bgGrad.addColorStop(0, '#141821');
  bgGrad.addColorStop(0.55, '#0d1016');
  bgGrad.addColorStop(1, '#070809');
  // 表单区背后一层很淡的品牌光，避免纯黑"发死"
  glowGrad = ctx.createRadialGradient(W * 0.78, H * 0.42, 20, W * 0.78, H * 0.42, Math.max(W, H) * 0.55);
  glowGrad.addColorStop(0, 'rgba(184, 51, 106, 0.16)');
  glowGrad.addColorStop(1, 'rgba(184, 51, 106, 0)');
  spawn();
}

function frame(t) {
  if (!ctx) return;
  pointer.x += (pointer.tx - pointer.x) * 0.05;
  pointer.y += (pointer.ty - pointer.y) * 0.05;

  ctx.fillStyle = bgGrad;
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = glowGrad;
  ctx.fillRect(0, 0, W, H);

  for (let i = 0; i < particles.length; i++) {
    const p = particles[i];
    p.x += p.vx;
    p.y += p.vy;
    if (p.y < -4) { p.y = H + 4; p.x = Math.random() * W; }
    if (p.x < -4) p.x = W + 4;
    if (p.x > W + 4) p.x = -4;

    const px = p.x - pointer.x * p.z * 26;
    const py = p.y - pointer.y * p.z * 26;
    const tw = 0.55 + 0.45 * Math.sin(t * 0.0013 + p.ph);

    ctx.beginPath();
    ctx.arc(px, py, p.r, 0, Math.PI * 2);
    ctx.fillStyle = p.accent ? `rgba(${ACCENT}, ${p.a * tw})` : `rgba(255, 255, 255, ${p.a * tw})`;
    ctx.fill();
  }
  raf = requestAnimationFrame(frame);
}

function onMove(e) {
  pointer.tx = (e.clientX / window.innerWidth) * 2 - 1;
  pointer.ty = (e.clientY / window.innerHeight) * 2 - 1;
  if (pageEl.value) {
    pageEl.value.style.setProperty('--mx', pointer.tx.toFixed(3));
    pageEl.value.style.setProperty('--my', pointer.ty.toFixed(3));
  }
}

onMounted(() => {
  resize();
  if (reduce) { frame(0); cancelAnimationFrame(raf); raf = 0; }
  else raf = requestAnimationFrame(frame);
  window.addEventListener('resize', resize);
  window.addEventListener('pointermove', onMove, { passive: true });
});

onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf);
  window.removeEventListener('resize', resize);
  window.removeEventListener('pointermove', onMove);
});
</script>

<style scoped>
/* ═══════════════ 影院感 hero（A 版：纯代码粒子） ═══════════════
   深色底 + canvas 粒子 + 微透明浮动卡；品牌色只上主操作与少量粒子。 */

.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  overflow: hidden;
  background: #070809;
  --mx: 0;
  --my: 0;
  /* 深色电影感页面：主色单独定回品牌色（应用主体已改用墨色主操作） */
  --accent: #d4507f;
  --accent-soft: rgba(184, 51, 106, 0.18);
  --accent-ink: #f2aac8;
}

.field {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  z-index: 0;
}

.login-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: stretch;
  width: 1040px;
  max-width: 100%;
  height: 640px;
  max-height: calc(100vh - 48px);
  border: 1px solid rgba(255, 255, 255, 0.10);
  border-radius: 18px;
  overflow: hidden;
  background: rgba(11, 13, 18, 0.62);
}

/* ── 左：hero ── */
.login-banner {
  flex: 1;
  min-width: 0;
  position: relative;
  padding: 30px 34px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.login-banner__brand {
  position: absolute;
  top: 28px;
  left: 34px;
  display: flex;
  align-items: center;
  gap: 9px;
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.02em;
}
.brand-dot {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--accent);
  display: inline-block;
}
.hero-copy { max-width: 380px; }
.hero-title {
  margin: 0 0 18px;
  font-size: clamp(34px, 4vmax, 54px);
  line-height: 1.08;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: #fff;
}
.hero-sub {
  margin: 0 0 26px;
  font-size: 14px;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.62);
}
.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 11px 20px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 180ms ease, border-color 180ms ease;
}
.hero-cta:hover { background: rgba(255, 255, 255, 0.12); border-color: rgba(255, 255, 255, 0.28); }
.hero-cta__mark {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.5);
  position: relative;
  flex: none;
}
.hero-cta__mark::after {
  content: '';
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  background: var(--accent);
}

/* ── 浮动信息卡 ── */
.float-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 16px;
  width: 180px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
  will-change: transform;
}
.fc-label {
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.42);
}
.fc-title { font-size: 15px; font-weight: 600; }
.fc-sub { font-size: 11px; line-height: 1.5; color: rgba(255, 255, 255, 0.55); }
.float-card--a {
  top: 21%;
  right: 8%;
  transform: translate3d(calc(var(--mx) * 16px), calc(var(--my) * 16px), 0);
}
.float-card--b {
  bottom: 20%;
  right: 16%;
  transform: translate3d(calc(var(--mx) * -12px), calc(var(--my) * -12px), 0);
}

.banner-foot {
  position: absolute;
  left: 34px;
  right: 34px;
  bottom: 28px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
}
.banner-foot span { position: relative; padding-left: 12px; }
.banner-foot span::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 4px;
  height: 4px;
  margin-top: -2px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}

/* ── 右：表单卡 ── */
.login-card {
  position: relative;
  width: 430px;
  flex-shrink: 0;
  padding: 34px 32px 24px;
  overflow-y: auto;
  /* 比内容底亮一档 → 形成一个「面」，输入框不再浮在纯黑上 */
  background: #16191f;
  border-left: 1px solid rgba(255, 255, 255, 0.08);
}
.login-header { text-align: center; margin-bottom: 8px; }
.login-logo {
  width: 54px;
  height: 54px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(184, 51, 106, 0.18);
  border: 1px solid rgba(184, 51, 106, 0.36);
  color: #f2c6da;
  font-size: 25px;
  font-weight: 700;
}
.login-header h1 { margin: 0 0 4px; font-size: 21px; font-weight: 600; color: #ecedf1; }
.login-header p { margin: 0; color: rgba(255, 255, 255, 0.5); font-size: 13px; }

/* Tab */
.auth-tabs { margin-top: 10px; }
.auth-tabs :deep(.el-tabs__nav) { width: 100%; display: flex; }
.auth-tabs :deep(.el-tabs__item) { flex: 1; text-align: center; font-size: 15px; color: rgba(255, 255, 255, 0.5); }
.auth-tabs :deep(.el-tabs__item.is-active) { font-weight: 600; color: #fff; }
.auth-tabs :deep(.el-tabs__active-bar) { height: 2px; border-radius: 0; background: var(--accent); }
.auth-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: rgba(255, 255, 255, 0.10); }

/* ── 深色表单控件：填充式字段，不用 Element 那条亮边 ── */
.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.05);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.10);
  border-radius: 10px;
  transition: border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}
.login-card :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.2);
}
.login-card :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent);
  background: rgba(255, 255, 255, 0.07);
  box-shadow: 0 0 0 3px rgba(184, 51, 106, 0.18);
}
.login-card :deep(.el-input__inner) { color: #ecedf1; }
.login-card :deep(.el-input__inner::placeholder) { color: rgba(255, 255, 255, 0.34); }
.login-card :deep(.el-input__password),
.login-card :deep(.el-input__suffix) { color: rgba(255, 255, 255, 0.45); }
.login-card :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.05);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.10);
  border-radius: 10px;
}
.login-card :deep(.el-select__wrapper.is-focused) {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(184, 51, 106, 0.18);
}
.login-card :deep(.el-select__placeholder) { color: rgba(255, 255, 255, 0.34); }
.login-card :deep(.el-select__selected-item) { color: #ecedf1; }
.login-card :deep(.el-form-item__label) { color: rgba(255, 255, 255, 0.66); }

/* 浏览器自动填充：把 Chrome 强刷的白/黄底压回深色 */
.login-card :deep(input:-webkit-autofill),
.login-card :deep(input:-webkit-autofill:hover),
.login-card :deep(input:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #1b1f27 inset;
  -webkit-text-fill-color: #ecedf1;
  caret-color: #ecedf1;
  transition: background-color 9999s ease-out 0s;
}

/* 主操作 */
.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 9999px;
  font-size: 15px;
  font-weight: 600;
}

/* 身份选择 */
.role-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; }
.role-card {
  padding: 12px 8px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  cursor: pointer;
  text-align: center;
  transition: border-color 180ms ease, background-color 180ms ease;
}
.role-card:hover { border-color: rgba(255, 255, 255, 0.28); }
.role-card--active { border-color: var(--accent); background: rgba(184, 51, 106, 0.18); }
.role-name { font-size: 13px; font-weight: 600; color: rgba(255, 255, 255, 0.72); }
.role-card--active .role-name { color: #fff; }

/* App 下载 */
.app-download { margin-top: 18px; padding-top: 16px; border-top: 1px solid rgba(255, 255, 255, 0.10); text-align: center; }
.app-download__btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 9999px;
  text-decoration: none;
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.app-download__btn:hover { background: var(--accent-ink); }
.app-download__icon { display: block; flex: none; }
.app-download__ver {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.app-download__tip { margin-top: 8px; font-size: 11px; color: rgba(255, 255, 255, 0.45); }

/* 响应式 */
@media (max-width: 900px) {
  .login-page { overflow-y: auto; align-items: flex-start; }
  .login-content { width: auto; height: auto; max-height: none; }
  .login-banner { display: none; }
  .login-card { width: auto; max-width: 100%; border-left: none; }
}
@media (max-width: 480px) {
  .login-page { padding: 12px; }
  .login-card { padding: 24px 18px 18px; }
  .login-header h1 { font-size: 19px; }
}
</style>