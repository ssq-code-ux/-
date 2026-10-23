<template>
  <div class="teacher-home">
    <!-- 问候横幅 -->
    <section class="hero">
      <div class="hero__text">
        <h1>{{ greeting }}，{{ user?.realName || '老师' }}</h1>
        <p>
          共 {{ counts.students }} 名学生，{{ counts.homeworks }} 份作业，{{ counts.vocab }} 个词汇。
        </p>
      </div>
      <el-button class="hero__btn" @click="$router.push('/teacher/homework')">布置作业</el-button>
    </section>

    <!-- KPI 卡片 -->
    <div class="kpi-grid">
      <KpiCard label="我的学生" :value="counts.students" unit="名" note="已绑定" tone="blue">
        <template #icon><el-icon><User /></el-icon></template>
      </KpiCard>
      <KpiCard label="已布置作业" :value="counts.homeworks" unit="份" note="全部学科" tone="amber">
        <template #icon><el-icon><Document /></el-icon></template>
      </KpiCard>
      <KpiCard label="词汇总量" :value="counts.vocab" unit="个" note="当前词库" tone="green">
        <template #icon><el-icon><Collection /></el-icon></template>
      </KpiCard>
      <KpiCard label="学习资料" :value="counts.resources" unit="份" note="我的资料库" tone="blue">
        <template #icon><el-icon><FolderOpened /></el-icon></template>
      </KpiCard>
    </div>

    <div class="two-col">
      <!-- 快捷入口 -->
      <section class="panel">
        <h2 class="panel-title">快捷入口</h2>
        <div class="quick-grid">
          <button class="quick" @click="$router.push('/teacher/students')">
            <el-icon><User /></el-icon><span>学生管理</span>
          </button>
          <button class="quick" @click="$router.push('/teacher/homework')">
            <el-icon><Document /></el-icon><span>作业管理</span>
          </button>
          <button class="quick" @click="$router.push('/teacher/vocabulary')">
            <el-icon><Collection /></el-icon><span>词汇管理</span>
          </button>
          <button class="quick" @click="$router.push('/teacher/words')">
            <el-icon><Reading /></el-icon><span>背单词</span>
          </button>
          <button class="quick" @click="$router.push('/teacher/resources')">
            <el-icon><FolderOpened /></el-icon><span>学习资料</span>
          </button>
          <button class="quick" @click="$router.push('/teacher/jobs')">
            <el-icon><Briefcase /></el-icon><span>接单广场</span>
          </button>
        </div>
      </section>

      <!-- 最近布置的作业 -->
      <section class="panel">
        <h2 class="panel-title">最近布置的作业</h2>
        <ul v-if="recent.length" class="recent">
          <li v-for="h in recent" :key="h.id" class="recent-row" @click="$router.push('/teacher/homework')">
            <div class="recent-main">
              <span class="recent-title">{{ h.title }}</span>
              <span class="recent-meta">
                {{ h.type === 'answer_sheet' ? '答题卡' : (h.type || '作业') }}
                <template v-if="h.subject">· {{ h.subject }}</template>
              </span>
            </div>
            <span class="recent-deadline">{{ h.deadline || '无截止时间' }}</span>
          </li>
        </ul>
        <EmptyState v-else text="还没有布置作业。从「作业管理」开始吧。">
          <el-button type="primary" @click="$router.push('/teacher/homework')">去布置作业</el-button>
        </EmptyState>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useUserStore } from '../stores/user';
import { students, homeworkList, vocabList, resourceList } from '../api';
import {
  User, Document, Collection, FolderOpened, Reading, Briefcase
} from '@element-plus/icons-vue';
import KpiCard from '../components/ui/KpiCard.vue';
import EmptyState from '../components/ui/EmptyState.vue';

const userStore = useUserStore();
const user = computed(() => userStore.user);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const counts = reactive({ students: 0, homeworks: 0, vocab: 0, resources: 0 });
const homeworks = ref([]);
const recent = computed(() => homeworks.value.slice(0, 5));

onMounted(async () => {
  const [s, hw, vc, rs] = await Promise.allSettled([
    students(),
    homeworkList(),
    vocabList({ page: 1, size: 1 }),
    resourceList({ page: 1, size: 1 })
  ]);
  if (s.status === 'fulfilled' && Array.isArray(s.value)) counts.students = s.value.length;
  if (hw.status === 'fulfilled' && Array.isArray(hw.value)) {
    homeworks.value = hw.value;
    counts.homeworks = hw.value.length;
  }
  if (vc.status === 'fulfilled' && vc.value) counts.vocab = vc.value.total ?? 0;
  if (rs.status === 'fulfilled' && rs.value) counts.resources = rs.value.total ?? 0;
});
</script>

<style scoped>
.teacher-home { max-width: 1180px; }

/* 问候横幅 */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 24px 28px;
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, #3d97f2 0%, #2f8ff0 55%, #24b6a8 100%); /* taste-skill-ignore（参考图风格横幅） */
  color: #fff;
  box-shadow: 0 8px 20px rgba(47, 143, 240, 0.28); /* taste-skill-ignore */
}
.hero__text h1 { margin: 0 0 6px; font-size: 24px; font-weight: 700; letter-spacing: -0.01em; }
.hero__text p { margin: 0; font-size: 14px; color: rgba(255, 255, 255, 0.9); }
.hero__btn {
  background: rgba(255, 255, 255, 0.95);
  border: none;
  color: var(--accent-ink);
  font-weight: 600;
}
.hero__btn:hover { background: #fff; color: var(--accent-ink); }

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 16px;
  align-items: start;
}
.panel {
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.panel-title { margin: 0 0 14px; font-size: var(--fs-md); font-weight: 600; color: var(--text); }

.quick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.quick {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--raised);
  font: inherit;
  font-size: var(--fs-base);
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;
}
.quick .el-icon { font-size: 18px; color: var(--accent); }
.quick:hover { border-color: var(--accent); background: var(--accent-soft); color: var(--accent-ink); }

.recent { list-style: none; margin: 0; padding: 0; }
.recent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}
.recent-row:last-child { border-bottom: none; }
.recent-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.recent-title { font-size: var(--fs-base); color: var(--text); font-weight: 500; }
.recent-row:hover .recent-title { color: var(--accent); }
.recent-meta { font-size: var(--fs-xs); color: var(--muted); }
.recent-deadline { font-size: var(--fs-xs); color: var(--muted); white-space: nowrap; }

@media (max-width: 900px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .two-col { grid-template-columns: 1fr; }
}
</style>