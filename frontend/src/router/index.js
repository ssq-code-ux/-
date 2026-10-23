import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { tryRefreshToken } from '../utils/request';
import Login from '../views/Login.vue';
import Layout from '../views/Layout.vue';
import Teacher from '../views/Teacher.vue';
import Student from '../views/Student.vue';
import Parent from '../views/Parent.vue';
import ResourceDetail from '../views/ResourceDetail.vue';
// 新页面懒加载，减小首屏包体积
const StudentDashboard = () => import('../views/StudentDashboard.vue');
const FlashCards = () => import('../views/FlashCards.vue');
const TeacherWords = () => import('../views/TeacherWords.vue');
// 家教相关页面体积较大（管理端一个文件就几千行），同样懒加载
const Admin = () => import('../views/Admin.vue');
const TeacherJobs = () => import('../views/TeacherJobs.vue');
const TeacherHome = () => import('../views/TeacherHome.vue');
const ParentTutor = () => import('../views/ParentTutor.vue');

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  {
    path: '/teacher',
    component: Layout,
    meta: { role: 'teacher' },
    children: [
      { path: '', redirect: '/teacher/home' },
      { path: 'home', component: TeacherHome, meta: { tab: 'home' } },
      { path: 'students', component: Teacher, meta: { tab: 'students' } },
      { path: 'homework', component: Teacher, meta: { tab: 'homework' } },
      { path: 'vocabulary', component: Teacher, meta: { tab: 'vocabulary' } },
      { path: 'words', component: TeacherWords },
      { path: 'resources', component: Teacher, meta: { tab: 'resources' } },
      { path: 'jobs', component: TeacherJobs }
    ]
  },
  {
    path: '/admin',
    component: Layout,
    meta: { role: 'admin' },
    children: [
      { path: '', redirect: '/admin/users' },
      { path: 'users', component: Admin, meta: { tab: 'users' } },
      { path: 'jobs', component: Admin, meta: { tab: 'jobs' } },
      { path: 'partner', component: Admin, meta: { tab: 'partner' } },
      { path: 'demands', component: Admin, meta: { tab: 'demands' } },
      { path: 'refunds', component: Admin, meta: { tab: 'refunds' } },
      { path: 'payments', component: Admin, meta: { tab: 'payments' } },
      { path: 'templates', component: Admin, meta: { tab: 'templates' } },
      { path: 'config', component: Admin, meta: { tab: 'config' } },
      { path: 'appversion', component: Admin, meta: { tab: 'appversion' } },
      { path: 'dashboard', component: Admin, meta: { tab: 'dashboard' } }
    ]
  },
  { path: '/resource/:id', component: ResourceDetail },
  {
    path: '/parent',
    component: Layout,
    meta: { role: 'parent' },
    children: [
      { path: '', component: Parent },
      { path: 'tutor', component: ParentTutor }
    ]
  },
  {
    path: '/student',
    component: Layout,
    meta: { role: 'student' },
    children: [
      { path: '', redirect: '/student/dashboard' },
      { path: 'dashboard', component: StudentDashboard },
      { path: 'flashcards', component: FlashCards },
      { path: 'homework', component: Student, meta: { tab: 'homework' } },
      { path: 'vocabulary', component: Student, meta: { tab: 'vocabulary' } },
      { path: 'resources', component: Student, meta: { tab: 'resources' } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 按角色返回登录后的首页（管理端也走同一套跳转）
function homePath(user) {
  if (user.role === 'teacher') return '/teacher';
  if (user.role === 'parent') return '/parent';
  if (user.role === 'admin') return '/admin';
  return '/student';
}

// 检查 JWT Token 是否过期（前端解码 exp 字段）
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true; // 解码失败视为过期
  }
}

// 解析当前登录态：access 有效直接用；access 过期先静默续期；续不上才清掉本地登录态。
// 「登录一次以后不用再登录，除非退出登录」的关键就在这里 —— access 只有 30 分钟，
// 过期不等于未登录，只要 refresh（长期有效、已落库）还在就能换回一对新 Token。
// 刷新复用 request.js 导出的同一个单飞 Promise，原因见该处注释。
async function resolveUser() {
  const user = JSON.parse(localStorage.getItem('user') || 'null');
  if (!user) return null;
  if (user.token && !isTokenExpired(user.token)) return user;
  const refreshed = await tryRefreshToken();
  if (refreshed) return refreshed;
  localStorage.removeItem('user');
  return null;
}

// 路由守卫：未登录（refresh 也续不上）→ 自动跳转登录页
router.beforeEach(async (to, from, next) => {
  // 先记下进来时有没有登录态：只有「本来登录过、现在续不上」才提示，
  // 免得用户主动打开登录页也被弹一句「登录已过期」
  const hadSession = !!JSON.parse(localStorage.getItem('user') || 'null')?.token;
  const user = await resolveUser();

  if (to.path === '/login') {
    // 如果已登录（或刚静默续期成功），直接跳转系统内部，不用重复登录
    if (user) {
      return next(homePath(user));
    }
    return next();
  }
  if (!user) {
    if (hadSession) ElMessage.info('登录已过期，请重新登录');
    return next('/login');
  }
  // 角色限制
  if (to.meta.role && to.meta.role !== user.role) {
    return next(homePath(user));
  }
  next();
});

export default router;
