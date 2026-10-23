<template>
  <div class="jobs-page">
    <header class="page-head">
      <h1>接单广场</h1>
    </header>

    <!-- ═══════════ 顶部筛选栏 ═══════════ -->
    <div class="filter-bar card">
      <el-select v-model="query.province" placeholder="选择省份" clearable style="width: 140px" @change="onProvinceChange">
        <el-option v-for="p in provinceList" :key="p" :label="p" :value="p" />
      </el-select>
      <el-select v-model="query.city" placeholder="全部城市" clearable style="width: 140px" :disabled="!query.province" @change="loadJobs(1)">
        <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
      </el-select>
      <!-- 需求 6：同行合作单与家教单分开，教师可按类型筛选 -->
      <el-select v-model="query.jobType" placeholder="全部类型" clearable style="width: 130px" @change="loadJobs(1)">
        <el-option label="家教单" :value="1" />
        <el-option label="同行合作单" :value="2" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="搜索城市、科目、编号..."
        clearable
        style="width: 220px"
        @keyup.enter="loadJobs(1)"
        @clear="loadJobs(1)"
      />
      <el-button type="primary" @click="loadJobs(1)">搜索</el-button>
      <el-button @click="openProfileDialog">编辑简历</el-button>
      <el-button @click="openNotice(null)">接单须知</el-button>
      <el-button @click="openMyApps">我的投递</el-button>
      <el-button type="warning" plain @click="openInvite">邀请同行</el-button>
      <span class="filter-tip">共 {{ total }} 单开放中</span>
    </div>

    <!-- ═══════════ 订单卡片列表（参考图的紫色系卡片） ═══════════ -->
    <div v-loading="loading" class="job-list">
      <div v-if="!loading && !jobList.length" class="empty-tip card">
        暂无开放中的家教单，换个筛选条件试试～
      </div>

      <div v-for="job in jobList" :key="job.id" class="job-card card">
        <div class="job-head">
          <div class="job-title">
            <span class="job-subject">{{ job.subject }}</span>
            <span class="job-loc">{{ job.province || '-' }}{{ job.city ? ' - ' + job.city : '' }} | {{ fmtDate(job.createTime) }}</span>
          </div>
          <span v-if="job.salary" class="job-salary">{{ job.salary }}元/小时</span>
        </div>

        <div class="job-tags">
          <el-tag v-if="job.jobType === 2" size="small" type="warning" effect="dark">同行合作单</el-tag>
          <el-tag v-if="job.grade" size="small" type="info" effect="plain">{{ job.grade }}</el-tag>
          <el-tag v-if="job.studentGender" size="small" type="info" effect="plain">{{ job.studentGender }}</el-tag>
          <el-tag v-if="job.expectGender" size="small" type="warning" effect="plain">{{ job.expectGender }}老师</el-tag>
          <el-tag v-if="job.frequency" size="small" type="info" effect="plain">{{ job.frequency }}</el-tag>
          <el-tag v-if="job.needPay" size="small" type="danger" effect="dark">需缴费解锁</el-tag>
        </div>

        <div v-if="job.address" class="job-line">地址：{{ job.address }}</div>
        <div v-if="job.situation" class="job-line">情况：{{ job.situation }}</div>
        <div v-if="job.requirement" class="job-line">要求：{{ job.requirement }}</div>

        <div class="job-foot">
          <span class="job-no">编号：{{ job.jobNo }}</span>
          <span class="job-apply-count">{{ job.applyCount || 0 }} 人已投递</span>
          <el-tag v-if="job.needPay && job.paid" type="success" size="small" effect="plain" style="margin-right: 4px">已缴费解锁</el-tag>
          <!-- 需求 5：好单可一键复制转发文字（自带自己的邀请码） -->
          <el-button size="small" round plain @click="onShare(job)">复制转发</el-button>
          <el-button
            v-if="!job.applied"
            type="primary"
            size="small"
            round
            @click="onApply(job)"
          >投递简历</el-button>
          <el-tag v-else type="success" effect="plain" size="small">已投递，等待审核</el-tag>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > query.size"
      v-model:current-page="query.page"
      :page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      class="pager"
      @current-change="loadJobs()"
    />

    <!-- ═══════════ 投递简历弹窗 ═══════════ -->
    <el-dialog v-model="applyDialog" title="投递简历" :width="isMobile ? '92%' : '520px'">
      <div class="apply-job-summary">
        <b>{{ applyTarget?.subject }}</b>
        <span v-if="applyTarget?.city">，{{ applyTarget.province }} - {{ applyTarget.city }}</span>
        <span v-if="applyTarget?.salary">，{{ applyTarget.salary }}元/小时</span>
      </div>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="投递时会自动附上您的完整简历（含个人照）和联系电话，家长通过后管理员会与您联系" />
      <el-input
        v-model="applyMessage"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="附言：简单介绍自己的教学经验、可上课时间等（选填，500 字以内）"
      />
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" :loading="applySubmitting" @click="submitApply">确认投递</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 接单须知弹窗（投递前必读；需缴费的单同时展示收款码与管理端联系方式） ═══════════ -->
    <el-dialog v-model="noticeVisible" title="接单须知（投递前请仔细阅读）" :width="isMobile ? '92%' : '620px'">
      <div v-loading="noticeLoading" class="notice-body">
        <div v-if="!noticeLoading && !noticeInfo.noticeText && !(noticeInfo.noticeImgs || []).length"
          class="empty-tip">管理员还未发布接单须知</div>
        <div v-if="noticeInfo.noticeText" class="notice-text">{{ noticeInfo.noticeText }}</div>
        <div v-if="(noticeInfo.noticeImgs || []).length" class="notice-imgs">
          <img v-for="(img, i) in noticeInfo.noticeImgs" :key="i" :src="img" class="notice-img" @click="previewImg(img)" />
        </div>

        <!-- 需缴费的单且未解锁：展示收款码 + 管理端联系方式，引导线下缴费 -->
        <div v-if="payBlocked" class="pay-guide">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 10px"
            title="该家教单需缴费后才能投递"
            description="请按接单须知联系管理员沟通，扫描下方收款码完成缴费；管理员确认到账后会为您开通本单的投递权限。" />
          <div v-if="noticeInfo.phone || noticeInfo.wechat" class="pay-contact">
            <div v-if="noticeInfo.phone">管理员电话：<b>{{ noticeInfo.phone }}</b></div>
            <div v-if="noticeInfo.wechat">管理员微信：<b>{{ noticeInfo.wechat }}</b></div>
          </div>
          <div v-if="noticeInfo.payQrUrl" class="pay-qr">
            <img :src="noticeInfo.payQrUrl" class="qr-img" @click="previewImg(noticeInfo.payQrUrl)" />
            <div class="qr-tip">收款码（点击可放大）</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="noticeVisible = false">关闭</el-button>
        <el-button v-if="!payBlocked" type="primary" @click="confirmReadNotice">我已阅读，继续投递</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 编辑个人信息（简历）弹窗 ═══════════ -->
    <el-dialog v-model="profileVisible" title="编辑个人信息（简历）" :width="isMobile ? '94%' : '680px'" top="4vh">
      <el-form :model="profile" label-width="110px" label-position="left" class="profile-form">
        <div class="form-section">接单信息</div>
        <el-form-item label="接单科目"><el-input v-model="profile.subjects" placeholder="如：初中数学、高中英语" /></el-form-item>
        <el-form-item label="可上课时间"><el-input v-model="profile.availableTime" placeholder="如：周一至周五晚上、周末全天" /></el-form-item>
        <el-form-item label="可长期授课">
          <el-select v-model="profile.longTerm" placeholder="请选择" clearable style="width: 200px">
            <el-option label="可" value="可" /><el-option label="不可" value="不可" /><el-option label="面议" value="面议" />
          </el-select>
        </el-form-item>

        <div class="form-section">个人简历</div>
        <div class="photo-row">
          <div class="photo-box">
            <img v-if="profile.photoUrl" :src="profile.photoUrl" class="photo-img" @click="previewImg(profile.photoUrl)" />
            <div v-else class="photo-empty">个人照</div>
          </div>
          <div class="photo-ops">
            <el-upload :show-file-list="false" :http-request="uploadPhoto" accept="image/*">
              <el-button size="small">{{ profile.photoUrl ? '更换照片' : '上传个人照' }}</el-button>
            </el-upload>
            <el-button v-if="profile.photoUrl" size="small" text type="danger" @click="profile.photoUrl = ''">移除</el-button>
            <div class="photo-tip">投递时将随简历一起展示给管理员和家长</div>
          </div>
        </div>
        <el-form-item label="奖项证明">
          <div class="award-imgs">
            <div v-for="(img, i) in profile.awardImgs" :key="i" class="award-img-box">
              <img :src="img" @click="previewImg(img)" />
              <span class="award-img-del" @click="profile.awardImgs.splice(i, 1)">×</span>
            </div>
            <el-upload :show-file-list="false" :http-request="uploadAwardImg" accept="image/*">
              <div class="award-img-add">＋</div>
            </el-upload>
          </div>
          <div class="award-tip">选填：获奖证书、资格证书等证明图片，投递时随简历一起展示给管理员和家长</div>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="profile.name" placeholder="真实姓名" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="profile.gender" placeholder="请选择" clearable style="width: 200px">
            <el-option label="男" value="男" /><el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄"><el-input v-model="profile.age" placeholder="如：21" style="width: 200px" /></el-form-item>
        <el-form-item label="学校"><el-input v-model="profile.school" placeholder="如：郑州大学" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="profile.major" placeholder="如：数学与应用数学" /></el-form-item>
        <el-form-item label="大几">
          <el-select v-model="profile.gradeYear" placeholder="请选择" clearable style="width: 200px">
            <el-option v-for="g in ['大一', '大二', '大三', '大四', '研一', '研二', '研三']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="教龄"><el-input v-model="profile.teachYears" placeholder="如：2年" style="width: 200px" /></el-form-item>
        <el-form-item label="籍贯"><el-input v-model="profile.hometown" placeholder="如：河南郑州" /></el-form-item>
        <el-form-item label="可教教材版本"><el-input v-model="profile.textbookVer" placeholder="如：人教版、北师大版" /></el-form-item>
        <el-form-item label="可辅导年级及科目"><el-input v-model="profile.teachScope" placeholder="如：初中全科、高一高二数学" /></el-form-item>
        <el-form-item label="备有辅导教材">
          <el-select v-model="profile.hasMaterial" placeholder="请选择" clearable style="width: 200px">
            <el-option label="有" value="有" /><el-option label="无" value="无" />
          </el-select>
        </el-form-item>
        <el-form-item label="其他优势"><el-input v-model="profile.otherAdvantage" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="如：有家教经验、普通话二级甲等、耐心负责" /></el-form-item>
        <el-form-item label="辅导优势"><el-input v-model="profile.teachAdvantage" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="如：善于引导学生思路、注重错题归纳" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存简历</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 图片预览 ═══════════ -->
    <el-dialog v-model="imgPreviewVisible" width="fit-content" class="img-preview-dialog">
      <img :src="imgPreviewUrl" style="max-width: 82vw; max-height: 74vh; display: block" />
    </el-dialog>

    <!-- ═══════════ 我的投递抽屉 ═══════════ -->
    <el-drawer v-model="myAppsVisible" title="我的投递与退费" :size="isMobile ? '90%' : '560px'">
      <div v-loading="myAppsLoading">
        <!-- 需求 1：试课失败后投递记录会被清空重新开放，退费进度单独放在工单里跟进 -->
        <div v-if="refundTasks.length" class="refund-block">
          <div class="refund-title">我的退费（{{ refundPending }} 笔待处理）</div>
          <div v-for="t in refundTasks" :key="t.id" class="refund-item">
            <div class="refund-line">
              <b>{{ t.subject || '家教单' }}</b>
              <span class="refund-no">编号 {{ t.jobNo || '—' }}</span>
              <el-tag :type="t.status === 1 ? 'success' : 'danger'" size="small">
                {{ t.status === 1 ? '已退费' : '待退费' }}
              </el-tag>
            </div>
            <div class="refund-line muted">
              <span v-if="Number(t.amount) > 0">应退 {{ t.amount }} 元</span>
              <span v-else>免费单，无需退款</span>
              <span v-if="t.refundTime">，{{ fmtDate(t.refundTime) }} 已办结</span>
              <span v-else>，{{ fmtDate(t.createTime) }} 登记</span>
            </div>
            <div v-if="t.reason" class="refund-line muted">原因：{{ t.reason }}</div>
            <div v-if="t.note" class="refund-line muted">管理端备注：{{ t.note }}</div>
          </div>
        </div>

        <div v-if="!myAppsLoading && !myApps.length" class="empty-tip">还没有投递记录</div>
        <div v-for="a in myApps" :key="a.id" class="my-app-card">
          <div class="my-app-head">
            <b>{{ a.subject || '家教单' }}</b>
            <el-tag :type="appStatusTag(a.status)" size="small">{{ appStatusText(a.status) }}</el-tag>
            <el-tag v-if="a.status === 1 && a.trialStatus != null" :type="TRIAL_TAG[a.trialStatus]" size="small" effect="plain">
              {{ TRIAL_TEXT[a.trialStatus] }}
            </el-tag>
          </div>
          <div class="my-app-line">
            编号 {{ a.jobNo }}<span v-if="a.city">，{{ a.province }} - {{ a.city }}</span><span v-if="a.salary">，{{ a.salary }}元/小时</span>
          </div>
          <div class="my-app-line muted">{{ fmtDate(a.createTime) }} 投递<span v-if="a.reviewTime">，{{ fmtDate(a.reviewTime) }} 审核</span></div>
          <div v-if="a.message" class="my-app-msg">附言：{{ a.message }}</div>
          <!-- 简历通过后：展示该单对应家长的联系方式 -->
          <div v-if="a.status === 1 && a.parentContact" class="my-app-contact">
            <div class="contact-title">家长联系方式（简历已通过）</div>
            <div class="contact-line">
              {{ a.parentContact.contactName }}，{{ a.parentContact.phone }}
              <span v-if="a.parentContact.wechat">，微信：{{ a.parentContact.wechat }}</span>
            </div>
            <div v-if="a.parentContact.grade || a.parentContact.studentLevel" class="contact-line muted">
              <span v-if="a.parentContact.grade">{{ a.parentContact.grade }}</span>
              <span v-if="a.parentContact.studentLevel">，{{ a.parentContact.studentLevel }}</span>
            </div>
            <div class="contact-line muted">请尽快与家长沟通对接上课事宜</div>
          </div>
          <!-- 试课阶段：试课中可确认结果；其他状态只展示提示 -->
          <div v-if="a.status === 1 && a.trialStatus === 0" class="trial-ops">
            <span class="trial-hint">试课结束后请确认结果：</span>
            <el-button type="success" size="small" @click="onTrial(a, true)">试课通过</el-button>
            <el-button type="danger" size="small" plain @click="onTrial(a, false)">试课失败，退费</el-button>
          </div>
          <div v-else-if="a.status === 1 && a.trialStatus === 1" class="trial-tip ok">试课通过，订单已完成</div>
          <div v-else-if="a.status === 1 && a.trialStatus === 2" class="trial-tip bad">试课失败已反馈管理端，等待退费处理</div>
          <div v-else-if="a.status === 1 && a.trialStatus === 3" class="trial-tip">退费已完成</div>
        </div>
      </div>
    </el-drawer>

    <!-- ═══════════ 需求 5：复制转发弹窗 ═══════════ -->
    <el-dialog v-model="shareVisible" title="转发这单给同行" :width="isMobile ? '94%' : '560px'">
      <div v-loading="shareLoading">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
          title="文字末尾自动带上您的邀请码，同行用它注册后就算您邀请的" />
        <el-input v-model="shareText" type="textarea" :rows="12" resize="none" class="share-text" />
      </div>
      <template #footer>
        <el-button @click="shareVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyShareText">一键复制</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 需求 5：我的邀请码弹窗 ═══════════ -->
    <el-dialog v-model="inviteVisible" title="邀请同行入驻" :width="isMobile ? '94%' : '520px'">
      <div v-loading="inviteLoading">
        <div class="invite-code-box">
          <div class="invite-code-label">我的专属邀请码</div>
          <div class="invite-code">{{ inviteInfo.inviteCode || '—' }}</div>
          <div class="invite-code-tip">同行在注册页填写这个码，邀请关系就记在您名下</div>
        </div>
        <div class="invite-stat">
          已邀请 <b>{{ inviteInfo.invitedCount || 0 }}</b> 位老师入驻
        </div>
        <div v-if="(inviteInfo.invitedUsers || []).length" class="invite-list">
          <div v-for="u in inviteInfo.invitedUsers" :key="u.id" class="invite-item">
            <span class="invite-name">{{ u.realName || u.username }}</span>
            <span class="invite-meta">{{ u.role === 'teacher' ? '老师' : u.role === 'parent' ? '家长' : u.role === 'student' ? '学生' : u.role }}，{{ fmtDate(u.createTime) }} 注册</span>
          </div>
        </div>
        <el-empty v-else description="还没有邀请到同行，把邀请码发到同行群里试试～" :image-size="70" />
      </div>
      <template #footer>
        <el-button @click="inviteVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyInviteCode">复制邀请码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  openTutorJobs, applyTutorJob, myTutorJobApplications, submitTrialResult,
  myTutorProfile, saveTutorProfile, tutorJobNotice, upload,
  myRefundTasks, tutorJobShareText, myInviteInfo,
} from '../api';
import { copyToClipboard } from '../utils/clipboard';

const isMobile = computed(() => window.innerWidth < 768);

// ==================== 筛选与列表 ====================
const query = reactive({ keyword: '', province: null, city: null, jobType: null, page: 1, size: 10 });
const jobList = ref([]);
const total = ref(0);
const loading = ref(false);

/** 从列表数据里聚合省份/城市（简单实用：数据量小，无需单独接口） */
const provinceList = computed(() => {
  const s = new Set(jobList.value.map(j => j.province).filter(Boolean));
  return [...s];
});
const cityList = computed(() => {
  const s = new Set(jobList.value.filter(j => j.province === query.province).map(j => j.city).filter(Boolean));
  return [...s];
});

async function loadJobs(page) {
  if (page) query.page = page;
  loading.value = true;
  try {
    const params = { page: query.page, size: query.size };
    if (query.keyword) params.keyword = query.keyword;
    if (query.province) params.province = query.province;
    if (query.city) params.city = query.city;
    if (query.jobType) params.jobType = query.jobType;
    const data = await openTutorJobs(params);
    jobList.value = data.list || [];
    total.value = data.total || 0;
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false;
  }
}

function onProvinceChange() {
  query.city = null;
  loadJobs(1);
}

// ==================== 投递 ====================
const applyDialog = ref(false);
const applyTarget = ref(null);
const applyMessage = ref('');
const applySubmitting = ref(false);

// ==================== 接单须知（投递前必读） ====================
const noticeVisible = ref(false);
const noticeLoading = ref(false);
const noticeInfo = ref({});
/** 当前从哪个单子进入须知（null = 主动查看须知） */
const noticeJob = ref(null);
/** 需缴费的单且未解锁时，须知弹窗里展示收款码+管理端联系方式，不能继续投递 */
const payBlocked = computed(() =>
  noticeJob.value && noticeJob.value.needPay && !noticeJob.value.paid && !noticeJob.value.applied);

async function openNotice(job) {
  noticeJob.value = job;
  noticeVisible.value = true;
  noticeLoading.value = true;
  try {
    noticeInfo.value = await tutorJobNotice();
  } catch (e) { /* 拦截器已提示 */ } finally {
    noticeLoading.value = false;
  }
}

/** 已阅读须知 校验简历完整度 进入投递弹窗 */
async function confirmReadNotice() {
  // 需缴费未解锁的单不能投（此时按钮本来不显示，双保险）
  if (payBlocked.value) return;

  // 简历核心字段必须先填（投递时自动附简历）
  if (!profileLoaded.value) await loadProfile();
  if (!profile.name || !profile.subjects) {
    ElMessage.warning('请先完善简历（姓名、接单科目必填），投递时会自动附上简历');
    noticeVisible.value = false;
    profileVisible.value = true;
    return;
  }
  noticeVisible.value = false;
  applyTarget.value = noticeJob.value;
  applyMessage.value = '';
  applyDialog.value = true;
}

function onApply(job) {
  // 投递前必须先阅读接单须知
  openNotice(job);
}

async function submitApply() {
  applySubmitting.value = true;
  try {
    await applyTutorJob(applyTarget.value.id, applyMessage.value.trim());
    ElMessage.success('投递成功，请等待管理员审核');
    applyDialog.value = false;
    applyTarget.value.applied = true;
    applyTarget.value.applyCount = (applyTarget.value.applyCount || 0) + 1;
    loadMyApps(); // 投递成功后同步刷新“我的投递”数据
  } catch (e) { /* 拦截器已提示（重复投递/单子已关闭/未缴费解锁等） */ } finally {
    applySubmitting.value = false;
  }
}

// ==================== 编辑个人信息（简历） ====================
const profileVisible = ref(false);
const profileSaving = ref(false);
const profileLoaded = ref(false);
const EMPTY_PROFILE = {
  subjects: '', availableTime: '', longTerm: '',
  name: '', gender: '', age: '', school: '', major: '', gradeYear: '',
  teachYears: '', hometown: '', textbookVer: '', teachScope: '', hasMaterial: '',
  otherAdvantage: '', teachAdvantage: '', photoUrl: '', awardImgs: [],
};
const profile = reactive({ ...EMPTY_PROFILE });

async function loadProfile() {
  try {
    const data = await myTutorProfile();
    Object.assign(profile, EMPTY_PROFILE, data || {});
    // 奖项证明始终是数组（老数据/后端返 null 时兜底）
    if (!Array.isArray(profile.awardImgs)) {
      profile.awardImgs = profile.awardImgs ? String(profile.awardImgs).split(',').filter(Boolean) : [];
    }
    profileLoaded.value = true;
  } catch (e) { /* 拦截器已提示 */ }
}

async function openProfileDialog() {
  profileVisible.value = true;
  await loadProfile();
}

async function submitProfile() {
  if (!profile.name?.trim() || !profile.subjects?.trim()) {
    ElMessage.warning('姓名和接单科目必填');
    return;
  }
  profileSaving.value = true;
  try {
    await saveTutorProfile({
      ...profile,
      age: profile.age === '' || profile.age == null ? null : Number(profile.age),
    });
    ElMessage.success('简历已保存，投递时会自动附上');
    profileVisible.value = false;
  } catch (e) { /* 拦截器已提示 */ } finally {
    profileSaving.value = false;
  }
}

async function uploadPhoto(option) {
  try {
    const r = await upload(option.file);
    profile.photoUrl = r.fileUrl;
    ElMessage.success('个人照已上传');
  } catch (e) {
    ElMessage.error('照片上传失败，请重试');
  }
}

async function uploadAwardImg(option) {
  try {
    const r = await upload(option.file);
    profile.awardImgs.push(r.fileUrl);
    ElMessage.success('奖项证明已上传');
  } catch (e) {
    ElMessage.error('图片上传失败，请重试');
  }
}

// ==================== 图片预览 ====================
const imgPreviewVisible = ref(false);
const imgPreviewUrl = ref('');
function previewImg(url) {
  if (!url) return;
  imgPreviewUrl.value = url;
  imgPreviewVisible.value = true;
}

// ==================== 我的投递 ====================
const myAppsVisible = ref(false);
const myAppsLoading = ref(false);
const myApps = ref([]);

// 打开抽屉时每次都重新拉取（之前只置 visible 不加载，导致投递过也显示空）
function openMyApps() {
  myAppsVisible.value = true;
  loadMyApps();
  loadRefunds();
}

async function loadMyApps() {
  myAppsLoading.value = true;
  try {
    const data = await myTutorJobApplications({ page: 1, size: 50 });
    myApps.value = data.list || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    myAppsLoading.value = false;
  }
}

// 需求 1：试课失败后投递记录被清空，退费进度只能从独立工单里看
const refundTasks = ref([]);
const refundPending = ref(0);

async function loadRefunds() {
  try {
    const data = await myRefundTasks();
    refundTasks.value = data.list || [];
    refundPending.value = data.pending || 0;
  } catch (e) { /* 拦截器已提示 */ }
}

const APP_STATUS_TEXT = { 0: '待审核', 1: '已通过', 2: '未通过' };
const APP_STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'danger' };
const appStatusText = s => APP_STATUS_TEXT[s] ?? s;
const appStatusTag = s => APP_STATUS_TAG[s] ?? 'info';

// 试课状态（简历通过后才进入）
const TRIAL_TEXT = { 0: '试课中', 1: '订单已完成', 2: '试课失败待退费', 3: '已退费' };
const TRIAL_TAG = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' };

async function onTrial(a, success) {
  try {
    await ElMessageBox.confirm(
      success
        ? '确认试课已通过吗？确认后该订单将标记为完成。'
        : '确认试课失败吗？提交后将通知管理端为您办理退费，请耐心等待处理。',
      success ? '试课通过' : '试课失败，申请退费',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: success ? 'success' : 'warning' });
    await submitTrialResult(a.id, success);
    ElMessage.success(success ? '已确认试课通过，订单完成' : '已反馈管理端，请等待退费处理');
    loadMyApps();
  } catch (e) { /* 取消或拦截器已提示 */ }
}

function fmtDate(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 10);
}

// 复制逻辑抽到 utils/clipboard.js 了：家长端复制周报是同一套降级需求（http 下没有 clipboard API）

// ==================== 需求 5：复制转发 + 邀请同行 ====================
const shareVisible = ref(false);
const shareLoading = ref(false);
const shareText = ref('');

async function onShare(job) {
  shareVisible.value = true;
  shareLoading.value = true;
  shareText.value = '';
  try {
    const data = await tutorJobShareText(job.id);
    shareText.value = data.text || '';
  } catch (e) { /* 拦截器已提示 */ } finally {
    shareLoading.value = false;
  }
}

async function copyShareText() {
  const ok = await copyToClipboard(shareText.value);
  ok ? ElMessage.success('已复制，去微信/QQ 粘贴给同行吧')
     : ElMessage.error('复制失败，请长按文本框手动全选复制');
}

const inviteVisible = ref(false);
const inviteLoading = ref(false);
const inviteInfo = ref({});

async function openInvite() {
  inviteVisible.value = true;
  inviteLoading.value = true;
  try {
    inviteInfo.value = await myInviteInfo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    inviteLoading.value = false;
  }
}

async function copyInviteCode() {
  const code = inviteInfo.value.inviteCode;
  if (!code) return ElMessage.warning('邀请码还没生成，请稍后重试');
  const ok = await copyToClipboard(`我在「智能学习助手」平台接家教单，用我的邀请码 ${code} 注册就能一起接单：${location.origin}/login?invite=${code}`);
  ok ? ElMessage.success('邀请码与链接已复制') : ElMessage.error('复制失败，请手动记下邀请码 ' + code);
}

onMounted(() => loadJobs(1));
</script>

<style scoped>
.jobs-page {
  max-width: 900px;
  margin: 0 auto;
}
.page-head {
  margin-bottom: 16px;
}
.page-head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}

/* ─── 筛选栏 ─── */
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
}
.filter-tip {
  font-size: 13px;
  color: var(--muted);
  margin-left: auto;
}

/* ─── 卡片 ─── */
.card {
  border-radius: var(--radius-lg);
}
.job-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}
.job-card {
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 16px 18px;
  transition: box-shadow 0.2s, transform 0.15s;
  border-radius: 16px;
}
.job-card:hover {
  transform: translateY(-2px);
}

.job-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.job-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.job-subject {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}
.job-loc {
  font-size: 13px;
  color: var(--muted);
}
.job-salary {
  padding: 4px 14px;
  border-radius: 9999px;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  white-space: nowrap;
}

.job-tags {
  margin-top: 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.job-line {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.6;
}

.job-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.job-no {
  font-size: 13px;
  color: var(--control);
  font-family: monospace;
}
.job-apply-count {
  font-size: 12px;
  color: var(--muted);
  margin-right: auto;
}

.empty-tip {
  text-align: center;
  color: var(--muted);
  padding: 40px 0;
  font-size: 14px;
}

/* ─── 投递弹窗 ─── */
.apply-job-summary {
  font-size: 14px;
  color: var(--color-text-regular);
  margin-bottom: 12px;
}

/* ─── 接单须知弹窗 ─── */
.notice-body {
  max-height: 58vh;
  overflow-y: auto;
  padding-right: 4px;
}
.notice-text {
  white-space: pre-wrap;
  font-size: 14px;
  color: var(--text);
  line-height: 1.8;
  background: #f8f8fe;
  border-radius: 10px;
  padding: 12px 14px;
}
.notice-imgs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.notice-img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
}
.pay-guide {
  margin-top: 14px;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
  padding-top: 12px;
}
.pay-contact {
  font-size: 14px;
  color: var(--text);
  line-height: 1.9;
}
.pay-qr {
  text-align: center;
  margin-top: 10px;
}
.qr-img {
  width: 220px;
  height: 220px;
  object-fit: contain;
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
}
.qr-tip {
  font-size: 12px;
  color: var(--muted);
  margin-top: 6px;
}

/* ─── 简历编辑弹窗 ─── */
.profile-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
}
.form-section {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
  margin: 14px 0 10px;
  padding-left: 8px;
  border-left: 3px solid var(--accent);
}
.form-section:first-child { margin-top: 0; }
.photo-row {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
  padding-left: 4px;
}
.photo-box {
  width: 92px;
  height: 116px;
  border-radius: 10px;
  border: 1px dashed rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #fafbfe;
  flex-shrink: 0;
}
.photo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: zoom-in;
}
.photo-empty {
  font-size: 12px;
  color: var(--control);
}
.photo-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.photo-tip {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}

/* ─── 奖项证明（多图上传） ─── */
.award-imgs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.award-img-box {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.award-img-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: zoom-in;
  display: block;
}
.award-img-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 50%;
  cursor: pointer;
}
.award-img-add {
  width: 88px;
  height: 88px;
  border: 1px dashed var(--control);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--muted);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}
.award-img-add:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.award-tip {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
  margin-top: 6px;
}

/* ─── 我的投递 ─── */
.my-app-card {
  padding: 12px 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 10px;
  margin-bottom: 10px;
  background: #fafbfe;
}
.my-app-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.my-app-line {
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.7;
}
.my-app-msg {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  background: var(--raised);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.6;
}

/* 简历通过后的家长联系方式块 */
.my-app-contact {
  margin-top: 10px;
  border: 1px solid #f3d19e;
  background: #f7efdf;
  border-radius: 10px;
  padding: 10px 12px;
}
.contact-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--warning);
  margin-bottom: 4px;
}
.contact-line {
  font-size: 14px;
  color: var(--text);
  line-height: 1.8;
  word-break: break-all;
}
.contact-line.muted,
.muted { color: var(--control); }

/* 试课确认操作区 */
.trial-ops {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.trial-hint { font-size: 12px; color: var(--muted); }
.trial-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--muted);
  background: var(--raised);
  border-radius: 8px;
  padding: 8px 12px;
}
.trial-tip.ok { color: #529b2e; background: #e7f2ec; }
.trial-tip.bad { color: #c45656; background: #fdecec; }

/* 需求 1：我的退费工单块 */
.refund-block {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #f3d19e;
  background: #f7efdf;
  border-radius: 10px;
}
.refund-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--warning);
  margin-bottom: 8px;
}
.refund-item {
  padding: 8px 10px;
  background: var(--surface);
  border-radius: 8px;
  margin-bottom: 6px;
}
.refund-item:last-child { margin-bottom: 0; }
.refund-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--text);
  line-height: 1.8;
}
.refund-line.muted { color: var(--muted); font-size: 12px; }
.refund-no {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: var(--accent);
}

/* 需求 5：转发文字 / 邀请码 */
.share-text :deep(textarea) {
  font-size: 13px;
  line-height: 1.8;
  font-family: Consolas, Monaco, 'Microsoft YaHei', monospace;
}
.invite-code-box {
  text-align: center;
  padding: 18px 14px;
  border-radius: 14px;
  background: var(--raised);
  border: 1px dashed #b9aef7;
}
.invite-code-label { font-size: 13px; color: var(--muted); }
.invite-code {
  margin: 8px 0;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 6px;
  color: var(--accent);
  font-family: Consolas, Monaco, monospace;
}
.invite-code-tip { font-size: 12px; color: #a0a4b8; }
.invite-stat {
  margin: 12px 0 8px;
  font-size: 14px;
  color: var(--color-text-regular);
}
.invite-stat b { color: var(--accent); font-size: 18px; }
.invite-list {
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
}
.invite-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 8px 2px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  font-size: 13px;
}
.invite-name { color: var(--text); font-weight: 600; }
.invite-meta { color: var(--control); font-size: 12px; }

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .filter-bar .el-select,
  .filter-bar .el-input { width: 100% !important; }
  .job-salary { font-size: 13px; }
}
/* ═══════════════ 编辑体：去掉"卡片套件"，改发丝线分区 ═══════════════ */
.filter-bar.card,
.job-card.card,
.empty-tip.card {
  background: transparent;
  border: none;
  border-radius: 0;
}
.filter-bar.card {
  border-bottom: 1px solid var(--border);
  padding: 0 0 14px;
  margin-bottom: 20px;
}
.job-card.card {
  border-top: 1px solid var(--border);
  padding: 18px 0;
}
.job-card.card:first-child { border-top: none; }
.job-list { gap: 0; }
.my-app-card {
  background: transparent;
  border: none;
  border-top: 1px solid var(--border);
  border-radius: 0;
}

/* ─── 移动端增强：筛选栏 / 家教单卡片窄屏优化 ─── */
@media (max-width: 768px) {
  .filter-bar { padding: 0 0 12px; gap: 8px; }
  .filter-bar .el-select,
  .filter-bar .el-input { width: 100% !important; }
  .filter-tip { margin-left: 0; width: 100%; }
  .job-card { padding: 14px 0; }
  .job-head { flex-wrap: wrap; }
  .job-salary { font-size: 13px; }
}
</style>
