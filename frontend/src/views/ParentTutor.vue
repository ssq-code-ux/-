<template>
  <div class="pt-page">
    <!-- ═══════════ 顶部：提交找家教需求 ═══════════ -->
    <div class="card head-card">
      <div class="head-left">
        <h1>找家教</h1>
        <p>只需留下手机号（微信号选填），管理员审核后会发布到教师接单广场并电话与您确认详情；老师投递的简历会实时展示在这里。</p>
      </div>
      <el-button type="primary" round @click="openDemandForm">发布找家教需求</el-button>
    </div>

    <!-- ═══════════ 我的需求列表 ═══════════ -->
    <div v-loading="loading" class="demand-list">
      <div v-if="!loading && !demands.length" class="empty-tip card">
        还没有提交过需求，点击右上角"发布找家教需求"开始吧～
      </div>

      <div v-for="d in demands" :key="d.id" class="demand-card card">
        <div class="d-head">
          <div class="d-title">
            <span class="d-subject">{{ d.subject || '学科待确认' }}</span>
            <span class="d-loc">{{ d.grade || '年级未填' }}<span v-if="d.studentGender"> · {{ d.studentGender }}</span></span>
            <span v-if="d.demandNo" class="d-no">编号 {{ d.demandNo }}</span>
          </div>
          <span v-if="d.salary" class="d-salary">{{ d.salary }}元/小时</span>
        </div>
        <div class="d-tags">
          <el-tag size="small" :type="d.status === 1 ? 'success' : 'warning'" effect="plain">
            {{ d.status === 1 ? '已发布' : '待管理员发布' }}
          </el-tag>
          <el-tag v-if="d.jobStatus" size="small" :type="d.jobStatus === '开放中' ? 'primary' : 'info'" effect="plain">
            家教单{{ d.jobStatus }}
          </el-tag>
          <el-tag v-if="d.status === 1" size="small" type="info" effect="plain">{{ d.applyCount || 0 }} 位老师已投递</el-tag>
          <el-tag v-if="d.deleteRequested === 1" size="small" type="danger" effect="plain">已申请删除，待管理员确认</el-tag>
        </div>
        <div v-if="d.studentLevel" class="d-line">学生程度：{{ d.studentLevel }}</div>
        <div v-if="d.otherRequire" class="d-line">对老师的要求：{{ d.otherRequire }}</div>
        <div class="d-foot">
          <span class="d-time">{{ fmtDate(d.createTime) }} 提交</span>
          <el-button v-if="d.status === 1" size="small" type="primary" round @click="openApplications(d)">
            查看老师简历（{{ d.applyCount || 0 }}）
          </el-button>
          <el-button v-if="d.deleteRequested !== 1" size="small" type="danger" plain round @click="onRequestDelete(d)">
            申请删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- ═══════════ 发布需求表单弹窗 ═══════════ -->
    <el-dialog v-model="formVisible" title="找家教需求" :width="isMobile ? '94%' : '620px'" top="4vh">
      <el-form :model="form" label-width="120px" label-position="left" class="demand-form">
        <!-- 只有手机号必填，微信号选填；其余字段收进“补充信息”，不展开也能直接提交 -->
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" placeholder="请填写手机号（老师确定后由管理员联系您）" maxlength="20" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="form.wechat" placeholder="选填" maxlength="50" />
        </el-form-item>

        <el-collapse v-model="moreOpen" class="more-collapse">
          <el-collapse-item name="more">
            <template #title>
              <span class="more-title">补充孩子情况（全部选填，管理员会电话跟您确认）</span>
            </template>
            <el-form-item label="您的称呼">
              <el-radio-group v-model="form.contactName">
                <el-radio value="先生">先生</el-radio>
                <el-radio value="女士">女士</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="辅导学科">
              <el-input v-model="form.subject" placeholder="想找什么学科的老师，如：数学、英语" maxlength="50" />
            </el-form-item>
            <el-form-item label="年级">
              <el-input v-model="form.grade" placeholder="如：初二、高三" maxlength="50" />
            </el-form-item>
            <el-form-item label="学生程度">
              <el-input v-model="form.studentLevel" placeholder="如：基础较弱，需要从基础补起 / 成绩中上，拔高冲刺" maxlength="200" />
            </el-form-item>
            <el-form-item label="学生性别">
              <el-radio-group v-model="form.studentGender">
                <el-radio value="男生">男生</el-radio>
                <el-radio value="女生">女生</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="老师性别要求">
              <el-radio-group v-model="form.expectGender">
                <el-radio value="不限">不限</el-radio>
                <el-radio value="男">男老师</el-radio>
                <el-radio value="女">女老师</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="其他要求">
              <el-input v-model="form.otherRequire" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="对老师的其他要求，如：有耐心、能长期带、师范生优先等" />
            </el-form-item>
            <el-form-item label="可提供时薪">
              <el-input v-model="form.salary" placeholder="元/小时，如：80" maxlength="10" style="width: 200px">
                <template #append>元/小时</template>
              </el-input>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDemand">提交需求</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 老师投递的简历抽屉 ═══════════ -->
    <el-drawer v-model="appsVisible" :size="isMobile ? '92%' : '640px'">
      <template #header>
        <div>
          <b>收到的老师简历</b>
          <div class="drawer-sub">{{ currentDemand?.subject || '学科待确认' }} · {{ currentDemand?.grade || '年级未填' }}</div>
        </div>
      </template>
      <div v-loading="appsLoading">
        <div v-if="!appsLoading && !apps.length" class="empty-tip">还没有老师投递，请耐心等待</div>

        <div v-for="a in apps" :key="a.id" class="app-card">
          <div class="app-head">
            <img v-if="a.profile?.photoUrl" :src="a.profile.photoUrl" class="app-photo" @click="previewImg(a.profile.photoUrl)" />
            <div v-else class="app-photo app-photo-empty">照片</div>
            <div class="app-head-info">
              <div class="app-name">
                <b>{{ a.profile?.name || a.teacherName || '老师' }}</b>
                <el-tag :type="appStatusTag(a.status)" size="small">{{ appStatusText(a.status) }}</el-tag>
              </div>
              <div class="app-brief">
                <span v-if="a.profile?.gender">{{ a.profile.gender }}</span>
                <span v-if="a.profile?.age"> · {{ a.profile.age }}岁</span>
                <span v-if="a.profile?.school"> · {{ a.profile.school }}</span>
                <span v-if="a.profile?.major"> · {{ a.profile.major }}</span>
              </div>
              <div v-if="a.profile?.gradeYear || a.profile?.teachYears" class="app-brief">
                <span v-if="a.profile?.gradeYear">{{ a.profile.gradeYear }}</span>
                <span v-if="a.profile?.teachYears"> · 教龄{{ a.profile.teachYears }}</span>
                <span v-if="a.profile?.hometown"> · {{ a.profile.hometown }}</span>
              </div>
            </div>
          </div>

          <div class="app-grid">
            <div v-if="a.profile?.subjects" class="app-cell"><span>接单科目</span>{{ a.profile.subjects }}</div>
            <div v-if="a.profile?.availableTime" class="app-cell"><span>可上课时间</span>{{ a.profile.availableTime }}</div>
            <div v-if="a.profile?.longTerm" class="app-cell"><span>长期授课</span>{{ a.profile.longTerm }}</div>
            <div v-if="a.profile?.textbookVer" class="app-cell"><span>可教教材版本</span>{{ a.profile.textbookVer }}</div>
            <div v-if="a.profile?.teachScope" class="app-cell"><span>可辅导年级及科目</span>{{ a.profile.teachScope }}</div>
            <div v-if="a.profile?.hasMaterial" class="app-cell"><span>备有辅导教材</span>{{ a.profile.hasMaterial }}</div>
          </div>
          <div v-if="a.profile?.teachAdvantage" class="app-block"><span>辅导优势：</span>{{ a.profile.teachAdvantage }}</div>
          <div v-if="a.profile?.otherAdvantage" class="app-block"><span>其他优势：</span>{{ a.profile.otherAdvantage }}</div>
          <div v-if="a.profile?.awardImgs" class="app-block">
            <span>奖项证明：</span>
            <div class="award-thumbs">
              <img v-for="(img, i) in String(a.profile.awardImgs).split(',').filter(Boolean)" :key="i"
                :src="img" class="award-thumb" @click="previewImg(img)" />
            </div>
          </div>
          <div v-if="a.message" class="app-msg">附言：{{ a.message }}</div>
          <div class="app-time">{{ fmtDate(a.createTime) }} 投递</div>

          <div v-if="a.status === 0" class="app-ops">
            <el-button size="small" @click="review(a, false)">暂不合适</el-button>
            <el-button size="small" type="primary" @click="review(a, true)">通过这份简历</el-button>
          </div>
          <el-alert v-else-if="a.status === 1" type="success" :closable="false" show-icon
            title="已通过：请通过管理员电话/微信联系后续对接事宜" style="margin-top: 8px" />
        </div>
      </div>
    </el-drawer>

    <!-- ═══════════ 通过后：管理端联系方式弹窗 ═══════════ -->
    <el-dialog v-model="contactVisible" title="简历已通过" :width="isMobile ? '92%' : '460px'">
      <div class="contact-body">
        <p>这位老师的简历已通过，家教单已关闭。后续对接请<strong>联系管理员</strong>：</p>
        <div class="contact-item" v-if="contactInfo.adminPhone">电话：<b>{{ contactInfo.adminPhone }}</b></div>
        <div class="contact-item" v-if="contactInfo.adminWechat">微信：<b>{{ contactInfo.adminWechat }}</b></div>
        <el-alert v-if="!contactInfo.adminPhone && !contactInfo.adminWechat" type="warning" :closable="false"
          title="管理员暂未配置联系方式，请通过其他渠道联系管理员" />
      </div>
      <template #footer>
        <el-button type="primary" @click="contactVisible = false">我知道了</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 图片预览 ═══════════ -->
    <el-dialog v-model="imgPreviewVisible" width="fit-content" class="img-preview-dialog">
      <img :src="imgPreviewUrl" style="max-width: 82vw; max-height: 74vh; display: block" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  submitParentDemand, myParentDemands, parentDemandApplications, parentReviewApplication,
  parentRequestDeleteDemand,
} from '../api';

const isMobile = computed(() => window.innerWidth < 768);

// ==================== 我的需求列表 ====================
const loading = ref(false);
const demands = ref([]);

async function loadDemands() {
  loading.value = true;
  try {
    const data = await myParentDemands({ page: 1, size: 50 });
    demands.value = data.records || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false;
  }
}

// 申请删除需求单（管理端确认后才会真正删除，连带删除关联家教单）
async function onRequestDelete(d) {
  try {
    await ElMessageBox.confirm(
      `确定申请删除这条找家教需求（${d.subject || '学科待确认'}）吗？提交后需管理员确认，确认通过后需求${d.jobId ? '及关联家教单' : ''}将被删除。`,
      '申请删除需求',
      { confirmButtonText: '提交申请', cancelButtonText: '取消', type: 'warning' });
    await parentRequestDeleteDemand(d.id);
    ElMessage.success('删除申请已提交，请等待管理员确认');
    loadDemands();
  } catch (e) { /* 取消或拦截器已提示 */ }
}

// ==================== 发布需求表单 ====================
const formVisible = ref(false);
const submitting = ref(false);
// “补充孩子情况”默认折叠：只填手机号就能提交，不想填的家长不用面对一屏表单
const moreOpen = ref('');
const EMPTY_FORM = {
  contactName: '', phone: '', wechat: '',
  studentLevel: '', grade: '', studentGender: '', subject: '',
  expectGender: '', otherRequire: '', salary: '',
};
const form = reactive({ ...EMPTY_FORM });

function openDemandForm() {
  Object.assign(form, EMPTY_FORM);
  moreOpen.value = '';
  formVisible.value = true;
}

async function submitDemand() {
  // 只有手机号必填（与后端校验保持一致），微信号及其余字段全部选填
  const phone = form.phone.trim();
  if (!phone) return ElMessage.warning('请填写手机号');
  if (!/^\d{5,20}$/.test(phone)) return ElMessage.warning('手机号格式不正确');
  submitting.value = true;
  try {
    await submitParentDemand({
      ...form,
      phone,
      salary: form.salary === '' || form.salary == null ? null : Number(form.salary),
    });
    ElMessage.success('需求已提交，管理员会先电话与您确认，发布后老师就可以投递简历了');
    formVisible.value = false;
    loadDemands();
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false;
  }
}

// ==================== 收到的老师简历 ====================
const appsVisible = ref(false);
const appsLoading = ref(false);
const apps = ref([]);
const currentDemand = ref(null);

async function openApplications(d) {
  currentDemand.value = d;
  appsVisible.value = true;
  appsLoading.value = true;
  try {
    const data = await parentDemandApplications(d.id);
    apps.value = data.records || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    appsLoading.value = false;
  }
}

const APP_STATUS_TEXT = { 0: '待处理', 1: '已通过', 2: '未通过' };
const APP_STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'danger' };
const appStatusText = s => APP_STATUS_TEXT[s] ?? s;
const appStatusTag = s => APP_STATUS_TAG[s] ?? 'info';

// ==================== 通过/拒绝简历 ====================
const contactVisible = ref(false);
const contactInfo = ref({});

async function review(a, approved) {
  const tip = approved
    ? `确定通过「${a.profile?.name || a.teacherName}」的简历吗？通过后该家教单将关闭，其他老师无法再投递。`
    : `确定标记「${a.profile?.name || a.teacherName}」的简历为暂不合适吗？`;
  try {
    await ElMessageBox.confirm(tip, approved ? '通过简历' : '暂不合适', { type: approved ? 'success' : 'warning' });
  } catch (e) { return; }

  try {
    const res = await parentReviewApplication(a.id, approved);
    a.status = approved ? 1 : 2;
    if (approved) {
      // 展示管理端联系方式
      contactInfo.value = res || {};
      contactVisible.value = true;
      // 单子已关闭，刷新列表状态
      const d = demands.value.find(x => x.id === currentDemand.value.id);
      if (d) { d.jobStatus = '已关闭'; }
    } else {
      ElMessage.success('已标记为暂不合适');
    }
  } catch (e) { /* 拦截器已提示 */ }
}

// ==================== 图片预览 ====================
const imgPreviewVisible = ref(false);
const imgPreviewUrl = ref('');
function previewImg(url) {
  if (!url) return;
  imgPreviewUrl.value = url;
  imgPreviewVisible.value = true;
}

function fmtDate(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 10);
}

onMounted(() => loadDemands());
</script>

<style scoped>
.pt-page {
  max-width: 900px;
  margin: 0 auto;
}
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

/* ─── 顶部卡片 ─── */
.head-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.head-left h1 {
  margin: 0 0 6px;
  color: var(--text);
  font-size: 22px;
  font-weight: 600;
}
.head-left p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  max-width: 560px;
}

/* ─── 需求列表 ─── */
.demand-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}
.demand-card {
  padding: 16px 18px;
  transition: box-shadow 0.2s, transform 0.15s;
}
.demand-card:hover {
  transform: translateY(-2px);
}
.d-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.d-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.d-subject {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}
.d-loc {
  font-size: 13px;
  color: var(--muted);
}
/* 需求单编号：家长与管理员沟通时直接报号 */
.d-no {
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
  letter-spacing: 0.5px;
  color: var(--accent);
  background: rgba(108, 92, 231, 0.1);
  border-radius: 6px;
  padding: 1px 8px;
}
.d-salary {
  padding: 4px 14px;
  border-radius: 9999px;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  white-space: nowrap;
}
.d-tags {
  margin-top: 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.d-line {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.6;
}
.d-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.d-time {
  font-size: 12px;
  color: var(--control);
}
.empty-tip {
  text-align: center;
  color: var(--muted);
  padding: 40px 0;
  font-size: 14px;
}

/* ─── 表单 ─── */
.demand-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
}
/* 需求 3：除手机号/微信号外的字段全部收进折叠面板，不展开也能提交 */
.more-collapse {
  margin-top: 6px;
  border-top: none;
  border-bottom: none;
}
.more-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  line-height: 40px;
  background: #f8f8fe;
  border-radius: 10px;
  padding: 0 12px;
}
.more-collapse :deep(.el-collapse-item__content) {
  padding: 14px 4px 0;
}
.more-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
}

/* ─── 简历卡片 ─── */
.drawer-sub {
  font-size: 12px;
  color: var(--muted);
  font-weight: normal;
  margin-top: 4px;
}
.app-card {
  padding: 14px 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  margin-bottom: 12px;
  background: #fafbfe;
}
.app-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.app-photo {
  width: 76px;
  height: 96px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
  flex-shrink: 0;
}
.app-photo-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--control);
  background: #f2f3f8;
  cursor: default;
}
.app-head-info { flex: 1; min-width: 0; }
.app-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}
.app-brief {
  font-size: 13px;
  color: var(--color-text-regular);
  margin-top: 4px;
  line-height: 1.6;
}
.app-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
  margin-top: 10px;
}
.app-cell {
  font-size: 13px;
  color: var(--text);
  line-height: 1.6;
}
.app-cell span,
.app-block span {
  color: var(--muted);
  margin-right: 6px;
}
.app-block {
  font-size: 13px;
  color: var(--text);
  line-height: 1.7;
  margin-top: 8px;
}
.app-msg {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
  background: var(--raised);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.6;
}
.app-time {
  font-size: 12px;
  color: var(--control);
  margin-top: 8px;
}
.app-ops {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ─── 奖项证明缩略图 ─── */
.award-thumbs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.award-thumb {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
  display: block;
}

/* ─── 联系方式弹窗 ─── */
.contact-body p {
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.8;
  margin: 0 0 10px;
}
.contact-item {
  font-size: 16px;
  color: var(--text);
  padding: 10px 14px;
  background: #f8f8fe;
  border-radius: 10px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .app-grid { grid-template-columns: 1fr; }
}
/* ─── 移动端增强：需求卡底部换行 / 发布按钮撑满 ─── */
@media (max-width: 768px) {
  .head-card { padding: 16px; }
  .head-card .el-button { width: 100%; }
  .demand-card { padding: 14px; }
  .d-foot { flex-wrap: wrap; }
  .d-foot .d-time { flex: 1 1 100%; }
  .app-ops { flex-wrap: wrap; }
}
</style>
