<template>
  <div class="parent-page">
    <header class="page-head">
      <div>
        <h1>孩子的学习</h1>
        <p class="page-sub">作业提交、成绩与错题汇总</p>
      </div>
    </header>

    <!-- 顶部：孩子选择 + 操作 -->
    <el-card shadow="never" class="section-card toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">我的孩子</span>
          <el-radio-group v-if="children.length" v-model="activeChildId" @change="onChildChange">
            <el-radio-button v-for="c in children" :key="c.id" :value="c.id">
              {{ c.realName || c.username }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-right">
          <el-button plain round @click="bindVisible = true">绑定孩子</el-button>
          <el-button plain round @click="openWeeklyReport" :disabled="!activeChildId">学习周报</el-button>
          <el-button plain round :loading="loading" @click="onRefresh">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 未绑定孩子的空状态 -->
    <el-card v-if="!children.length && !loading" shadow="never" class="section-card">
      <el-empty>
        <template #description>
          <p style="color:var(--muted);margin:0 0 4px">还没有绑定孩子</p>
          <p style="color:var(--control);font-size:13px;margin:0">点击右上角「绑定孩子」，输入孩子的学生登录账号即可</p>
        </template>
        <el-button type="primary" round @click="bindVisible = true">立即绑定</el-button>
      </el-empty>
    </el-card>

    <template v-if="activeChildId">
      <!-- KPI 卡片 -->
      <div class="kpi-grid">
        <KpiCard label="作业提交率" :value="analysis?.submitRate?.toFixed(0) ?? 0" unit="%" tone="blue">
          <template #icon><el-icon><Document /></el-icon></template>
        </KpiCard>
        <KpiCard label="作业平均分" :value="analysis?.averageScore?.toFixed(0) ?? 0" unit="分" tone="amber">
          <template #icon><el-icon><Star /></el-icon></template>
        </KpiCard>
        <KpiCard label="测验错题" :value="errorTotal" unit="个" tone="red">
          <template #icon><el-icon><Warning /></el-icon></template>
        </KpiCard>
      </div>

      <!-- 逾期警示 -->
      <el-alert
        v-if="overdueCount > 0"
        type="error"
        :closable="false"
        show-icon
        class="section-card"
        :title="`有 ${overdueCount} 项作业已逾期未提交，请督促孩子尽快完成`"
      />

      <el-row :gutter="16">
        <!-- 作业列表 -->
        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header-flex">
                <span class="card-title">作业完成情况（最近 10 项）</span>
                <el-radio-group v-model="statusFilter" size="small">
                  <el-radio-button value="all">全部</el-radio-button>
                  <el-radio-button value="overdue">逾期</el-radio-button>
                  <el-radio-button value="已批改">已批改</el-radio-button>
                  <el-radio-button value="已提交">已提交</el-radio-button>
                  <el-radio-button value="未提交">未提交</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <el-table :data="filteredHomeworks" stripe size="small" empty-text="暂无作业记录">
              <el-table-column prop="title" label="作业" min-width="140" show-overflow-tooltip />
              <el-table-column label="截止时间" width="150">
                <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.overdue" type="danger" size="small">已逾期</el-tag>
                  <el-tag v-else-if="row.status === '已批改'" type="success" size="small">已批改</el-tag>
                  <el-tag v-else-if="row.status === '已提交'" type="primary" size="small">已提交</el-tag>
                  <el-tag v-else-if="row.status === '需重做'" type="warning" size="small">需重做</el-tag>
                  <el-tag v-else type="info" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="openHwDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <!-- 成绩趋势 -->
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="section-card">
            <template #header><span class="card-title">最近已批改作业成绩趋势</span></template>
            <div ref="chartRef" style="width:100%;height:260px" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 学习建议（后端每天预生成，登录直接读缓存） -->
      <el-card shadow="never" class="section-card ai-card">
        <template #header>
          <div class="card-header-flex">
            <span class="card-title ai-title">
              <img class="ai-title-logo" :src="aiLogo" alt="" />
              学习建议
              <el-tag size="small" type="primary" effect="plain" round style="margin-left:6px">每日更新</el-tag>
            </span>
            <el-button
              size="small" round
              :class="aiAdvice ? 'ai-regen-btn' : ''"
              :type="aiAdvice ? 'default' : 'primary'"
              :loading="aiLoading"
              @click="generateAiAdvice"
            >
              {{ aiAdvice ? '重新生成' : '立即生成' }}
            </el-button>
          </div>
        </template>

        <!-- AI 生成中（仅手动重新生成时会看到） -->
        <div v-if="aiLoading" class="ai-thinking">
          <div class="ai-dots"><span /><span /><span /></div>
          AI 正在结合 {{ childName }} 的最新学习数据重新生成，请稍候（约 5~15 秒）…
        </div>

        <!-- 当日建议（每天早上自动预生成） -->
        <template v-else-if="aiAdvice">
          <div class="ai-advice" v-html="renderAdvice(aiAdvice)"></div>
          <div v-if="aiAdviceGenTime" class="ai-gen-time">更新于 {{ formatGenTime(aiAdviceGenTime) }} · 每天早上自动更新</div>
        </template>

        <!-- 缓存还没有：降级展示后端基础建议 -->
        <template v-else-if="analysis?.suggestions?.length">
          <p class="ai-fallback-tip">今日 AI 建议还没有生成，以下为系统基础建议；也可点击右上角「立即生成」马上补一份</p>
          <ul class="suggestion-list">
            <li v-for="(s, i) in analysis.suggestions" :key="i">{{ s }}</li>
          </ul>
        </template>

        <el-empty
          v-else
          description="学习建议每天早上自动更新；今天还没生成时，可点击右上角「立即生成」"
          :image-size="60"
        />
      </el-card>
    </template>

    <!-- 作业详情弹窗 -->
    <el-dialog v-model="hwDetailVisible" title="作业详情" width="640px" destroy-on-close top="6vh">
      <div v-loading="hwDetailLoading" class="hw-detail-body">
        <template v-if="hwDetail">
          <div class="hw-detail-title">{{ hwDetail.title }}</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="作业类型">{{ hwDetail.type || '—' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag v-if="hwDetail.overdue" type="danger" size="small">已逾期</el-tag>
              <el-tag v-else-if="hwDetail.status === '已批改'" type="success" size="small">已批改</el-tag>
              <el-tag v-else-if="hwDetail.status === '已提交'" type="primary" size="small">已提交</el-tag>
              <el-tag v-else-if="hwDetail.status === '需重做'" type="warning" size="small">需重做</el-tag>
              <el-tag v-else type="info" size="small">{{ hwDetail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="截止时间">{{ formatTime(hwDetail.deadline) }}</el-descriptions-item>
            <el-descriptions-item label="布置时间">{{ formatTime(hwDetail.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 作业内容 -->
          <div class="detail-section">
            <div class="detail-section-title">作业内容</div>
            <div class="detail-text pre-line">{{ hwDetail.content || '（教师未填写作业说明）' }}</div>
            <!-- 教师附件：图片给缩略图、点击全屏预览（与教师端作业详情一致）；
                 多个附件是逗号拼接的，逐个拆开展示，不能把整串当单个 URL 打开 -->
            <div v-if="attachImages.length" class="file-thumbs">
              <el-image
                v-for="(u, i) in attachImages"
                :key="u"
                :src="u"
                :preview-src-list="attachImages"
                :initial-index="i"
                fit="cover"
                class="file-thumb"
                preview-teleported
                hide-on-click-modal
              />
            </div>
            <div v-for="u in attachOtherFiles" :key="u">
              <a class="file-link" @click="openFile(mediaUrl(u))">教师附件：{{ fileNameFromUrl(u) }}（点击查看/下载）</a>
            </div>
          </div>

          <!-- 孩子的提交 -->
          <div class="detail-section">
            <div class="detail-section-title">孩子的提交</div>
            <template v-if="hwDetail.submit">
              <div class="detail-text">提交时间：{{ formatTime(hwDetail.submit.submitTime) }}</div>
              <div v-if="hwDetail.submit.textAnswer" class="detail-text pre-line answer-box">
                {{ hwDetail.submit.textAnswer }}
              </div>
              <div v-if="submitImages.length" class="file-thumbs">
                <el-image
                  v-for="(u, i) in submitImages"
                  :key="u"
                  :src="u"
                  :preview-src-list="submitImages"
                  :initial-index="i"
                  fit="cover"
                  class="file-thumb"
                  preview-teleported
                  hide-on-click-modal
                />
              </div>
              <div v-for="u in submitOtherFiles" :key="u">
                <a class="file-link" @click="openFile(mediaUrl(u))">{{ fileNameFromUrl(u) }}（点击查看/下载）</a>
              </div>
            </template>
            <el-empty v-else description="孩子还没有提交这份作业" :image-size="60" />
          </div>

          <!-- 教师批改 -->
          <div v-if="hwDetail.review" class="detail-section">
            <div class="detail-section-title">教师批改</div>
            <div class="detail-text">
              得分：<b class="score-num">{{ hwDetail.review.score ?? '—' }}</b> 分
              <span class="review-time">· 批改于 {{ formatTime(hwDetail.review.reviewTime) }}</span>
              <el-tag
                v-if="String(hwDetail.review.needRedo) === 'true' || hwDetail.review.needRedo === '是'"
                type="warning" size="small" style="margin-left:8px"
              >教师要求重做</el-tag>
            </div>
            <div v-if="hwDetail.review.comment" class="detail-text pre-line comment-box">
              {{ hwDetail.review.comment }}
            </div>
            <!-- 语音评语：家长可以直接听老师的口述点评 -->
            <div v-if="hwDetail.review.voiceUrl" class="voice-box">
              <span class="voice-label">老师语音评语</span>
              <audio :src="mediaUrl(hwDetail.review.voiceUrl)" controls class="voice-audio" />
            </div>
          </div>
          <div v-else-if="hwDetail.submit" class="detail-section">
            <div class="detail-text" style="color:var(--muted)">已提交，等待教师批改</div>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 学习周报弹窗 -->
    <el-dialog v-model="reportVisible" title="本周学习周报" width="620px" destroy-on-close top="6vh">
      <div v-loading="reportLoading" class="report-body">
        <template v-if="report">
          <p class="report-range">{{ report.rangeStart }} ~ {{ report.rangeEnd }}（最近 7 天）</p>
          <div class="report-grid">
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.newHomeworks }}</span>
              <span class="rc-label">新收到作业</span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.submitCount }}</span>
              <span class="rc-label">提交次数</span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.reviewedCount }}</span>
              <span class="rc-label">已获批改</span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.avgScore }}<small>分</small></span>
              <span class="rc-label">本周平均分</span>
              <span v-if="report.avgScoreLast" class="rc-delta" :class="reportScoreDelta >= 0 ? 'up' : 'down'">
                {{ reportScoreDelta >= 0 ? '↑' : '↓' }} {{ Math.abs(reportScoreDelta) }} vs 上周
              </span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.activeDays }}<small>/7天</small></span>
              <span class="rc-label">学习活跃天数</span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.newWordErrors }}</span>
              <span class="rc-label">测验新增错词</span>
            </div>
            <div class="report-cell">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.masteredWords }}</span>
              <span class="rc-label">掌握错词</span>
            </div>
            <div class="report-cell" :class="{ 'rc-warn': report.overdueHomeworks > 0 }">
              <span class="rc-icon"></span>
              <span class="rc-value">{{ report.overdueHomeworks }}</span>
              <span class="rc-label">逾期未提交</span>
            </div>
          </div>

          <!-- 本周总结（后端每周一预生成） -->
          <div class="report-ai">
            <div class="report-ai-head">
              <span>本周总结</span>
              <el-button
                size="small" round
                :type="reportAiSummary ? 'default' : 'primary'"
                :loading="reportAiLoading"
                @click="generateReportSummary"
              >
                {{ reportAiSummary ? '重新生成' : '立即生成' }}
              </el-button>
            </div>
            <div v-if="reportAiLoading" class="ai-thinking">
              <div class="ai-dots"><span /><span /><span /></div>
              AI 正在为 {{ childName }} 重新生成本周总结…
            </div>
            <div v-else-if="reportAiSummary" class="ai-advice" v-html="renderAdvice(reportAiSummary)"></div>
            <p v-else class="report-ai-tip">本周总结每周一自动更新；本周还没生成时，可点击右侧「立即生成」</p>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button round @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" round @click="copyReport">复制周报</el-button>
        <el-button type="success" round @click="buildShareCard">生成分享卡片</el-button>
      </template>
    </el-dialog>

    <!-- 分享卡片预览（纯前端 canvas 绘制，长按保存/转发） -->
    <el-dialog v-model="cardVisible" title="分享卡片" width="400px" append-to-body>
      <img v-if="cardUrl" :src="cardUrl" class="share-card-img" alt="学习周报分享卡片" />
      <p class="share-card-tip">
        手机上长按图片即可保存，或直接转发到微信<br>
        电脑上点击下方按钮下载
      </p>
      <template #footer>
        <el-button round @click="cardVisible = false">关闭</el-button>
        <el-button type="primary" round @click="downloadCard">下载图片</el-button>
      </template>
    </el-dialog>

    <!-- 绑定孩子弹窗 -->
    <el-dialog v-model="bindVisible" title="绑定孩子" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="孩子的学生登录账号">
          <el-input v-model="bindUsername" placeholder="请输入孩子登录系统用的账号" />
        </el-form-item>
        <p style="color:var(--muted);font-size:13px;margin:0">
          绑定后可查看孩子的作业、测验与学习周报；孩子作业逾期未交时，系统会通过右上角 通知提醒您。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindLoading" @click="onBind">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
// 按需引入 echarts（只用本页用到的折线图），替代整包 1MB 引入
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);
import { ElMessage } from 'element-plus';
import { Document, Star, Warning } from '@element-plus/icons-vue';
import KpiCard from '../components/ui/KpiCard.vue';
import {
  parentChildren, parentBindChild, parentChildAnalysis,
  parentChildHomeworks, parentChildHomeworkDetail, parentChildWeeklyReport,
  parentChildAiContent, parentChildAiRefresh
} from '../api';
import { fileNameFromUrl } from '../utils/docFile';
import { drawReportCard } from '../utils/reportCard';
import { copyToClipboard } from '../utils/clipboard';
import aiLogo from '../assets/ai-logo.png';

const loading = ref(false);
const children = ref([]);
const activeChildId = ref(null);

const analysis = ref(null);
const homeworks = ref([]);
const statusFilter = ref('all');

const bindVisible = ref(false);
const bindLoading = ref(false);
const bindUsername = ref('');

const hwDetailVisible = ref(false);
const hwDetailLoading = ref(false);
const hwDetail = ref(null);

// 学习建议（后端每天定时预生成，登录后直接读缓存；手动「重新生成」会现场再生成一份）
const aiLoading = ref(false);
const aiAdvice = ref('');
const aiAdviceGenTime = ref(null);

// 学习周报（最近 7 天聚合 + AI 总结 + 复制分享）
const reportVisible = ref(false);
const reportLoading = ref(false);
const report = ref(null);
const reportAiLoading = ref(false);
const reportAiSummary = ref('');

// 分享卡片（纯前端 canvas 绘制，谁看图谁出算力，服务器零成本）
const cardVisible = ref(false);
const cardUrl = ref('');
const reportScoreDelta = computed(() => {
  if (!report.value) return 0;
  return Math.round(((report.value.avgScore || 0) - (report.value.avgScoreLast || 0)) * 10) / 10;
});

const chartRef = ref(null);
let chart = null;

// 当前选中孩子的名字（提示文案用）
const childName = computed(() => {
  const c = children.value.find(x => x.id === activeChildId.value);
  return c?.realName || c?.username || '孩子';
});

// 错题总数（按错误类型汇总）
const errorTotal = computed(() => {
  const m = analysis.value?.errorTypeCount || {};
  return Object.values(m).reduce((a, b) => a + b, 0);
});
const overdueCount = computed(() => homeworks.value.filter(h => h.overdue).length);

// 作业列表按状态筛选
const filteredHomeworks = computed(() => {
  if (statusFilter.value === 'all') return homeworks.value;
  if (statusFilter.value === 'overdue') return homeworks.value.filter(h => h.overdue);
  return homeworks.value.filter(h => h.status === statusFilter.value);
});

// 作业详情里的提交附件拆分（图片缩略图预览，其余文件给下载链接）
const splitFiles = (s) => (s ? String(s).split(',').map(x => x.trim()).filter(Boolean) : []);
const isImageUrl = (u) => /\.(png|jpe?g|gif|webp|bmp)(\?.*)?$/i.test(u);
// 旧数据可能存的是相对路径，OSS 上传后是完整 URL，这里统一补前缀
const apiBase = import.meta.env.VITE_API_BASE_URL || '';
const mediaUrl = (u) => (u && u.startsWith('http')) ? u : (apiBase + u);
const submitFiles = computed(() => splitFiles(hwDetail.value?.submit?.fileUrl));
// 图片统一补前缀后再给 el-image：旧数据存的是相对路径，直接绑会裂图
const submitImages = computed(() => submitFiles.value.filter(isImageUrl).map(mediaUrl));
const submitOtherFiles = computed(() => submitFiles.value.filter(u => !isImageUrl(u)));
// 教师附件同样拆分：图片缩略图全屏预览，其余文件逐个给下载链接
const attachFiles = computed(() => splitFiles(hwDetail.value?.attachmentUrl));
const attachImages = computed(() => attachFiles.value.filter(isImageUrl).map(mediaUrl));
const attachOtherFiles = computed(() => attachFiles.value.filter(u => !isImageUrl(u)));

onMounted(async () => {
  await loadChildren();
});

onBeforeUnmount(() => {
  chart?.dispose();
  window.removeEventListener('resize', resizeChart);
});

async function loadChildren() {
  loading.value = true;
  try {
    children.value = await parentChildren();
    if (children.value.length) {
      activeChildId.value = activeChildId.value
        && children.value.some(c => c.id === activeChildId.value)
        ? activeChildId.value
        : children.value[0].id;
      await loadChildData();
    }
  } finally { loading.value = false; }
}

async function loadChildData() {
  if (!activeChildId.value) return;
  const id = activeChildId.value;
  const [a, h] = await Promise.all([
    parentChildAnalysis(id).catch(() => null),
    parentChildHomeworks(id).catch(() => [])
  ]);
  analysis.value = a;
  homeworks.value = h;
  aiAdvice.value = '';
  aiAdviceGenTime.value = null;
  await nextTick();
  renderChart();
  // 读当天预生成的学习建议（后端每天定时生成，秒回不用等）
  loadAiAdvice();
}

function onChildChange() { loadChildData(); }
function onRefresh() {
  ElMessage.success('已刷新');
  loadChildData();
}

// ---------- 学习建议（读缓存 / 手动重新生成） ----------
/** 读当天预生成的学习建议缓存（后端每天早上自动生成，家长登录前就有了） */
async function loadAiAdvice() {
  aiAdvice.value = '';
  aiAdviceGenTime.value = null;
  try {
    const res = await parentChildAiContent(activeChildId.value, 'daily_advice');
    aiAdvice.value = res?.content || '';
    aiAdviceGenTime.value = res?.genTime || null;
  } catch (e) {
    // 读缓存失败静默降级到后端基础建议（模板区自动展示）
  }
}

/** 手动重新生成：现场调 AI 结合最新数据重写当天缓存 */
async function generateAiAdvice() {
  if (aiLoading.value) return;
  aiLoading.value = true;
  aiAdvice.value = '';
  try {
    const res = await parentChildAiRefresh(activeChildId.value, 'daily_advice');
    aiAdvice.value = res?.content || '';
    aiAdviceGenTime.value = res?.genTime || null;
    if (!aiAdvice.value) throw new Error('empty reply');
  } catch (e) {
    // AI 服务不可用时静默降级到后端基础建议（模板区自动展示）
    ElMessage.warning('AI 服务暂时不可用，已展示基础建议');
  } finally {
    aiLoading.value = false;
  }
}

/** 生成时间展示为 MM-DD HH:mm */
function formatGenTime(t) {
  if (!t) return '';
  const d = new Date(t);
  if (isNaN(d.getTime())) return '';
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 轻量 Markdown 渲染（转义 + 加粗 + 换行），与 AI 助手气泡一致 */
function renderAdvice(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>');
}

// ---------- 学习周报 ----------
/** 打开周报弹窗：拉最近 7 天聚合数据 + 本周预生成的 AI 总结（一周一份） */
async function openWeeklyReport() {
  reportVisible.value = true;
  reportAiSummary.value = '';
  reportLoading.value = true;
  try {
    const [r, ai] = await Promise.all([
      parentChildWeeklyReport(activeChildId.value),
      parentChildAiContent(activeChildId.value, 'weekly_summary').catch(() => null)
    ]);
    report.value = r;
    reportAiSummary.value = ai?.content || '';
  } catch (e) { /* 错误信息由拦截器提示 */ }
  finally { reportLoading.value = false; }
}

/** 手动重新生成本周总结：现场调 AI 重写本周缓存 */
async function generateReportSummary() {
  if (reportAiLoading.value) return;
  reportAiLoading.value = true;
  reportAiSummary.value = '';
  try {
    const res = await parentChildAiRefresh(activeChildId.value, 'weekly_summary');
    reportAiSummary.value = res?.content || '';
    if (!reportAiSummary.value) throw new Error('empty reply');
  } catch (e) {
    ElMessage.warning('AI 总结生成失败，可稍后重试');
  } finally {
    reportAiLoading.value = false;
  }
}

/** 复制可分享的周报文本（含 AI 点评） */
async function copyReport() {
  if (!report.value) return;
  const r = report.value;
  const delta = reportScoreDelta.value;
  const lines = [
    `${childName.value} 的学习周报（${r.rangeStart} ~ ${r.rangeEnd}）`,
    `新收到作业：${r.newHomeworks ?? 0} 份，已提交 ${r.submitCount ?? 0} 次`,
    `本周平均分：${r.avgScore ?? 0} 分` + (r.avgScoreLast ? `（上周 ${r.avgScoreLast} 分，${delta >= 0 ? '↑' : '↓'} ${Math.abs(delta)}）` : ''),
    `单词测验新增错词：${r.newWordErrors ?? 0} 个，掌握错词 ${r.masteredWords ?? 0} 个`,
    `学习活跃：${r.activeDays ?? 0}/7 天`
  ];
  if (r.overdueHomeworks > 0) lines.push(`逾期未提交作业：${r.overdueHomeworks} 项`);
  if (reportAiSummary.value) {
    lines.push('', 'AI 点评：', reportAiSummary.value.replace(/\*\*/g, ''));
  }
  // 走公共的复制工具：App 外壳 / 纯 IP 部署都是 http，navigator.clipboard 根本不存在，
  // 直接调会抛 TypeError 让复制永远失败；工具内部会退到 execCommand 兜底
  const ok = await copyToClipboard(lines.join('\n'));
  if (ok) ElMessage.success('周报已复制，可粘贴分享');
  else ElMessage.warning('复制失败，请手动选择文本复制');
}

/** 生成周报分享卡片：数据 + AI 点评排版成图，预览后长按保存/下载 */
function buildShareCard() {
  if (!report.value) return;
  try {
    cardUrl.value = drawReportCard({
      childName: childName.value,
      rangeText: `${report.value.rangeStart} ~ ${report.value.rangeEnd}`,
      report: report.value,
      summary: reportAiSummary.value
    });
    cardVisible.value = true;
  } catch (e) {
    ElMessage.warning('卡片生成失败，请重试');
  }
}

/** 电脑端下载卡片（移动端主推长按保存） */
function downloadCard() {
  if (!cardUrl.value) return;
  const a = document.createElement('a');
  a.href = cardUrl.value;
  a.download = `${childName.value}本周学习周报.png`;
  a.click();
}

// 打开某份作业的详情弹窗
async function openHwDetail(row) {
  hwDetailVisible.value = true;
  hwDetailLoading.value = true;
  hwDetail.value = null;
  try {
    hwDetail.value = await parentChildHomeworkDetail(activeChildId.value, row.id);
  } catch (e) { /* 错误信息由拦截器提示 */ }
  finally { hwDetailLoading.value = false; }
}

function openFile(url) {
  window.open(url, '_blank');
}

function renderChart() {
  if (!chartRef.value) return;
  if (!chart) {
    chart = echarts.init(chartRef.value);
    window.addEventListener('resize', resizeChart);
  }
  const trend = analysis.value?.scoreTrend || [];
  chart.setOption({
    grid: { left: 36, right: 16, top: 24, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: trend.map(p => (p.title || '').slice(0, 8)),
      axisLabel: { fontSize: 11, interval: 0, rotate: trend.length > 4 ? 30 : 0 }
    },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{
      name: '成绩',
      type: 'line',
      smooth: true,
      data: trend.map(p => p.score),
      itemStyle: { color: 'var(--accent)' },
      areaStyle: { opacity: 0.12 }
    }]
  });
}

function resizeChart() { chart?.resize(); }

async function onBind() {
  if (!bindUsername.value.trim()) {
    ElMessage.warning('请输入孩子的学生账号');
    return;
  }
  bindLoading.value = true;
  try {
    await parentBindChild(bindUsername.value.trim());
    ElMessage.success('绑定成功');
    bindVisible.value = false;
    bindUsername.value = '';
    await loadChildren();
  } catch (e) { /* 错误信息由拦截器提示 */ }
  finally { bindLoading.value = false; }
}

function formatTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.parent-page {
  max-width: 1200px;
  margin: 0 auto;
}
.section-card {
  margin-bottom: 16px;
  border-radius: 12px;
  overflow: hidden;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
}
.card-header-flex {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

/* 工具栏 */
.toolbar-card {
  background: var(--surface);
  border: 1px solid var(--border);
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.toolbar-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
}
.toolbar-right {
  display: flex;
  gap: 8px;
}

/* 标题行 */
.page-head {
  margin-bottom: 18px;
}
.page-head h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}
.page-sub {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

/* KPI 卡片 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

/* 分享卡片预览弹窗 */
.share-card-img {
  width: 100%;
  border-radius: 12px;
  display: block;
}
.share-card-tip {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
  margin: 10px 0 0;
}

/* 学习周报弹窗 */
.report-range {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 14px;
}
.report-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.report-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 14px 6px 10px;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.rc-icon { font-size: 20px; }
.rc-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text);
  line-height: 1.2;
}
.rc-value small {
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
  margin-left: 2px;
}
.rc-label { font-size: 12px; color: var(--muted); }
.rc-delta { font-size: 11px; margin-top: 2px; }
.rc-delta.up { color: var(--positive); }
.rc-delta.down { color: var(--negative); }
.rc-warn { background: #fdecec; border-color: #fdecec; }
.rc-warn .rc-value { color: var(--negative); }
.report-ai {
  border-top: 1px dashed var(--border);
  padding-top: 14px;
}
.report-ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
}
.report-ai-tip { font-size: 13px; color: var(--muted); margin: 0; }
@media (max-width: 600px) {
  .report-grid { grid-template-columns: repeat(2, 1fr); }
}
/* 基础建议列表（AI 降级用） */
.suggestion-list {
  margin: 0;
  padding-left: 20px;
  color: var(--color-text-regular);
  font-size: 14px;
  line-height: 2;
}

/* ---------- AI 学习建议卡片 ---------- */
.ai-card {
  border: 1px solid var(--accent-track);
}
.ai-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
}
.ai-title-logo {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px solid var(--accent-track);
  object-fit: cover;
}
.ai-thinking {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  color: var(--muted);
  font-size: 14px;
}
.ai-dots {
  display: inline-flex;
  gap: 5px;
}
.ai-dots span {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: ai-bounce 1.2s infinite ease-in-out;
}
.ai-dots span:nth-child(2) { animation-delay: 0.15s; }
.ai-dots span:nth-child(3) { animation-delay: 0.3s; }
@keyframes ai-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.ai-advice {
  background: var(--raised);
  border-left: 3px solid var(--accent);
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 2;
  color: var(--text);
  word-break: break-word;
}
.ai-advice :deep(strong) { color: #5a4fcf; }
.ai-advice :deep(code) {
  background: var(--accent-soft);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 13px;
}
.ai-fallback-tip {
  color: var(--warning);
  font-size: 13px;
  margin: 0 0 6px;
}
.ai-gen-time {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

/* 作业详情弹窗 */
.hw-detail-body {
  min-height: 120px;
}
.hw-detail-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 12px;
}
.detail-section {
  margin-top: 16px;
  padding: 12px 14px;
  background: var(--raised);
  border-radius: 10px;
}
.detail-section-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text);
  margin-bottom: 8px;
}
.detail-text {
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.7;
  margin-bottom: 6px;
}
.pre-line {
  white-space: pre-line;
}
.answer-box {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
}
.comment-box {
  background: var(--surface);
  border-left: 3px solid var(--positive);
  border-radius: 6px;
  padding: 8px 12px;
}
.voice-box {
  margin-top: 8px;
}
.voice-label {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--muted);
}
.voice-audio {
  width: 100%;
  max-width: 320px;
  height: 34px;
}
.score-num {
  color: var(--accent);
  font-size: 20px;
}
.review-time {
  color: var(--muted);
  font-size: 13px;
}
.file-link {
  color: var(--accent);
  cursor: pointer;
  font-size: 13px;
  display: inline-block;
  margin-top: 6px;
}
.file-link:hover {
  text-decoration: underline;
}
.file-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.file-thumb {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  border: 1px solid var(--border);
  cursor: pointer;
}

@media (max-width: 768px) {
  .kpi-grid { grid-template-columns: 1fr; }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .toolbar-left { gap: 10px; }
  .toolbar-title { font-size: 15px; }
  .section-card { margin-bottom: 12px; }
}
</style>
