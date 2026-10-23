<template>
  <div class="dashboard">
    <!-- 标题行：屏幕的问句 + 唯一主操作 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">{{ greeting }}，{{ user?.realName || '同学' }}</h1>
        <p class="page-sub">
          {{ todayText }} ·
          <template v-if="pendingHomework">
            今天还有 <b>{{ pendingHomework }}</b> 份作业待完成<template v-if="overdueCount">，其中 <b class="is-overdue">{{ overdueCount }}</b> 份已逾期</template>
          </template>
          <template v-else>作业已全部完成</template>
        </p>
      </div>
      <el-button type="primary" @click="$router.push('/student/homework')">去做作业</el-button>
    </header>

    <!-- KPI 卡片 -->
    <div class="kpi-grid">
      <KpiCard
        label="待完成作业"
        :value="pendingHomework"
        unit="份"
        :note="overdueCount ? overdueCount + ' 份已逾期' : '暂无逾期'"
        tone="blue"
        class="is-clickable"
        @click="$router.push('/student/homework')"
      >
        <template #icon><el-icon><Document /></el-icon></template>
      </KpiCard>
      <KpiCard
        label="已提交待批改"
        :value="submittedCount"
        unit="份"
        note="等待老师批改"
        tone="amber"
        class="is-clickable"
        @click="$router.push('/student/homework')"
      >
        <template #icon><el-icon><Upload /></el-icon></template>
      </KpiCard>
      <KpiCard
        label="错题待掌握"
        :value="errStats.unreviewedCount"
        unit="个"
        note="还没通过复习"
        tone="red"
        class="is-clickable"
        @click="$router.push('/student/vocabulary')"
      >
        <template #icon><el-icon><Collection /></el-icon></template>
      </KpiCard>
      <KpiCard
        label="今日到期复习"
        :value="errStats.dueReviewCount"
        unit="个"
        note="按记忆计划"
        tone="green"
        class="is-clickable"
        @click="$router.push('/student/vocabulary')"
      >
        <template #icon><el-icon><Clock /></el-icon></template>
      </KpiCard>
    </div>

    <!-- 快捷入口 + 最近作业 -->
    <div class="panel-row">
      <section class="panel">
        <h2 class="panel-title">快捷入口</h2>
        <div class="quick-grid">
          <button class="quick-item" @click="$router.push('/student/flashcards')">记忆卡片</button>
          <button class="quick-item" @click="$router.push('/student/vocabulary')">看词选意</button>
          <button class="quick-item" @click="$router.push('/student/resources')">学习资料</button>
          <button class="quick-item" @click="$router.push('/student/homework')">我的作业</button>
        </div>
      </section>

      <section class="panel">
        <h2 class="panel-title">最近作业</h2>
        <ul v-if="recentHomeworks.length" class="recent-list">
          <li
            v-for="h in recentHomeworks"
            :key="h.id"
            class="recent-item"
            @click="$router.push('/student/homework')"
          >
            <span class="recent-title">{{ h.title }}</span>
            <span class="recent-status" :class="'rs-' + statusKey(h.status)">{{ h.status }}</span>
          </li>
        </ul>
        <p v-else class="recent-empty">暂无作业，去自主学习吧</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '../stores/user';
import { homeworkList, errorStats } from '../api';
import { Document, Upload, Collection, Clock } from '@element-plus/icons-vue';
import KpiCard from '../components/ui/KpiCard.vue';

const userStore = useUserStore();
const user = computed(() => userStore.user);

const loading = ref(true);
const errStats = ref({ unreviewedCount: 0, dueReviewCount: 0 });
const homeworks = ref([]);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const todayText = computed(() => {
  const d = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getMonth() + 1}月${d.getDate()}日 星期${week}`;
});

const pendingHomework = computed(
  () => homeworks.value.filter((h) => h.status === '未提交' || h.status === '需重做').length
);
const submittedCount = computed(
  () => homeworks.value.filter((h) => h.status === '已提交').length
);
const overdueCount = computed(
  () =>
    homeworks.value.filter(
      (h) => h.status === '未提交' && h.deadline && new Date(h.deadline.replace(' ', 'T')) < new Date()
    ).length
);
const recentHomeworks = computed(() => {
  const pending = homeworks.value.filter((h) => h.status === '未提交' || h.status === '需重做');
  const rest = homeworks.value.filter((h) => h.status !== '未提交' && h.status !== '需重做');
  return [...pending, ...rest].slice(0, 5);
});

function statusKey(status) {
  return { 未提交: 'pending', 需重做: 'redo', 已提交: 'submitted', 已批改: 'graded' }[status] || 'pending';
}

async function loadAll() {
  loading.value = true;
  const [hwRes, errRes] = await Promise.allSettled([homeworkList(), errorStats()]);
  if (hwRes.status === 'fulfilled' && Array.isArray(hwRes.value)) homeworks.value = hwRes.value;
  if (errRes.status === 'fulfilled' && errRes.value) errStats.value = { ...errStats.value, ...errRes.value };
  loading.value = false;
}

onMounted(loadAll);
</script>

<style scoped>
/* 网格：一条左边线到底；所有卡片共用 14px 间距 */
.dashboard {
  max-width: 1100px;
  margin: 0 auto;
}

/* ═══ 标题行 ═══ */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.page-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  color: var(--text);
}
.page-sub {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}
.page-sub b {
  color: var(--text);
  font-weight: 600;
}
.is-overdue { color: var(--negative); }

/* ═══ KPI 卡片 ═══ */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.is-clickable { cursor: pointer; }

/* ═══ 面板 ═══ */
.panel-row {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 14px;
  align-items: start;
}
.panel {
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.panel-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

/* 快捷入口：中性行，无图标底、无彩色方块 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.quick-item {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  font: inherit;
  font-size: 14px;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  transition: border-color 180ms ease, background 180ms ease;
}
.quick-item:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

/* 最近作业 */
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 11px 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}
.recent-item:last-child { border-bottom: none; }
.recent-item:hover .recent-title { color: var(--accent); }
.recent-title {
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 180ms ease;
}
.recent-status {
  flex-shrink: 0;
  font-size: 12px;
  padding: 2px 9px;
  border-radius: var(--radius-full);
}
.rs-pending { background: var(--raised); color: var(--muted); }
.rs-redo { background: #fdecec; color: var(--negative); }
.rs-submitted { background: #f7efdf; color: var(--warning); }
.rs-graded { background: #e7f2ec; color: var(--positive); }
.recent-empty {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  padding: 14px 0;
}

/* ═══ 移动端 ═══ */
@media (max-width: 768px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  
  
  .panel-row { grid-template-columns: 1fr; }
  .page-title { font-size: 20px; }
}
</style>