import request from '../utils/request';
import axios from 'axios';
import { compressImage } from '../utils/compress';

// Auth
export const login = (data) => request.post('/api/auth/login', data);
export const register = (data) => request.post('/api/auth/register', data);
export const logoutApi = (refreshToken) => request.post('/api/auth/logout', { refreshToken });

// Students & Teachers
export const students = () => request.get('/api/students');
export const searchStudents = (name) => {
  const params = name ? { name } : {};
  return request.get('/api/students/search', { params });
};
export const bindStudent = (id) => request.put(`/api/students/${id}/bind`);
export const myTeacher = () => request.get('/api/students/my-teacher');
export const teachers = () => request.get('/api/teachers');
export const saveProfile = (data) => request.post('/api/students/profile', data);
export const analysis = (id) => request.get(`/api/students/${id}/analysis`);
/** 某学生的档案（教师端编辑学生基本信息用：年级/学科/掌握程度） */
export const studentProfile = (id) => request.get(`/api/students/${id}/profile`);

// Homework
export const homeworkList = (studentId) => {
  const params = studentId ? { studentId } : {};
  return request.get('/api/homeworks', { params });
};
export const getHomework = (id) => request.get(`/api/homeworks/${id}`);
export const getGroupedHomeworks = () => request.get('/api/homeworks/grouped');
export const addHomework = (data) => request.post('/api/homeworks', data);
export const updateHomework = (id, data) => request.put(`/api/homeworks/${id}`, data);
export const submitHomework = (data) => request.post('/api/homeworks/submit', data);
export const submits = (id) => request.get(`/api/homeworks/${id}/submits`);
export const review = (data) => request.post('/api/homeworks/review', data);
export const reviewResult = (id) => request.get(`/api/homeworks/${id}/review`);
export const getSubmitReview = (submitId) => request.get(`/api/homeworks/submits/${submitId}/review`);
export const overdueHomework = () => request.get('/api/homeworks/overdue');
export const deleteHomework = (id) => request.delete(`/api/homeworks/${id}`);

// Answer Sheet — 答题卡作业（AI 出卷 + 自动判分）
/** AI 解析题目文本，返回带标准答案的结构化题目（仅教师）。题量大时大模型要算很久，超时放到 120s */
export const aiParseQuestions = (data) => request.post('/api/ai/answer-sheet/parse', data, { timeout: 120000 });
/** AI 图片识别出卷：多模态模型把题目图片转成文字后走与文本解析相同的出卷流程（multipart，file+sectionHint）。识别+解析两次调大模型，超时给更久 */
export const aiParseImageQuestions = (formData) => request.post('/api/ai/answer-sheet/parse-image', formData, { timeout: 180000 });
/** 教师核验后布置答题卡 */
export const assignAnswerSheet = (data) => request.post('/api/homeworks/answer-sheet', data);
/** 教师查看整卷（含答案与解析） */
export const answerSheetQuestions = (homeworkId) => request.get(`/api/homeworks/answer-sheet/${homeworkId}/questions`);
/** 学生拉取答题卡（不含答案） */
export const answerSheetPaper = (homeworkId) => request.get(`/api/homeworks/answer-sheet/${homeworkId}/paper`);
/** 学生提交答题卡，同步返回判分结果（主观题要等 AI 评分，同样给 120s） */
export const submitAnswerSheet = (data) => request.post('/api/homeworks/answer-sheet/submit', data, { timeout: 120000 });
/** 逐题批改结果 */
export const answerSheetResult = (submitId) => request.get(`/api/homeworks/answer-sheet/result/${submitId}`);
/** 教师复核调分 */
export const fixAnswerSheet = (data) => request.post('/api/homeworks/answer-sheet/fix', data);

// Goals
export const goals = (studentId) => request.get('/api/goals', { params: { studentId } });
export const addGoal = (data) => request.post('/api/goals', data);

// File upload — 先压缩再上传，带重试机制应对网络波动
export const upload = async (file, retries = 2) => {
  // 客户端压缩：手机拍照普遍 5-15MB，压缩后 ~200-500KB，上传快 10 倍以上
  const compressed = await compressImage(file);

  let lastError = null;
  for (let i = 0; i <= retries; i++) {
    try {
      const formData = new FormData();
      formData.append('file', compressed);
      // 超时 120s（与 Nginx proxy_read_timeout 对齐），压缩后文件小，通常 5 秒内完成
      return await request.post('/api/files/upload', formData, { timeout: 120000 });
    } catch (err) {
      lastError = err;
      // 只有真正的网络/连接错误才重试。axios 网络错误是 Error 实例且没有 response；
      // 业务错误（从拦截器 reject 的 {code, message} 对象）不是 Error 实例，不重试
      const isNetworkError = err instanceof Error && !err.response;
      if (isNetworkError && i < retries) {
        console.warn(`上传失败，正在重试 (${i + 1}/${retries})...`);
        await new Promise(r => setTimeout(r, 1000));
        continue;
      }
      break;
    }
  }
  throw lastError;
};

// 语音上传（录音评语）— 音频不需要压缩，直接走同一个上传接口
export const uploadVoice = (blob, name = `voice-${Date.now()}.webm`) => {
  const formData = new FormData();
  formData.append('file', new File([blob], name, { type: blob.type || 'audio/webm' }));
  return request.post('/api/files/upload', formData, { timeout: 120000 });
};

// Vocabulary
export const vocabList = (params) => request.get('/api/vocabularies', { params });
export const vocabSearch = (params) => request.get('/api/vocabularies/search', { params });
export const addVocab = (data) => request.post('/api/vocabularies', data);
export const updateVocab = (id, data) => request.put(`/api/vocabularies/${id}`, data);
export const deleteVocab = (id) => request.delete(`/api/vocabularies/${id}`);
export const randomVocab = (params) => request.get('/api/vocabularies/random', { params });
/** 全部词库名称（学生词库/四级词库/六级词库...） */
export const vocabBanks = () => request.get('/api/vocabularies/banks');

// Learning Resources
export const resourceList = (params) => request.get('/api/resources', { params });
/** 已存在的资料类型（去重），供新增/编辑资料时下拉建议；传 subjectId 只看该学科下的类型 */
export const resourceTypes = (subjectId) =>
  request.get('/api/resources/types', { params: subjectId ? { subjectId } : {} });
export const searchTeachers = (keyword) => {
  const params = keyword ? { keyword } : {};
  return request.get('/api/resources/teachers/search', { params });
};
export const getResource = (id) => request.get(`/api/resources/${id}`);
export const addResource = (formData) => request.post('/api/resources', formData);
export const updateResource = (id, formData) => request.put(`/api/resources/${id}`, formData);
export const deleteResource = (id) => request.delete(`/api/resources/${id}`);
export const copyResource = (id) => request.post(`/api/resources/${id}/copy`);
export const copyAllResources = (teacherId) => request.post('/api/resources/copy-all', null, { params: { teacherId } });
// 文件代理（后端从 OSS 拉取转发，规避浏览器跨域）：预览/下载资料附件，返回 Blob 原始响应
export const resourceFileBlob = (id, index, mode = 'preview') =>
  request.get(`/api/resources/${id}/file`, { params: { index, mode }, responseType: 'blob', timeout: 120000 });

// Subjects（学科）
export const subjectList = () => request.get('/api/subjects');
// 注意：后端用 @RequestParam 接收 name，必须放 params 而不是 body
export const addSubjectApi = (name) => request.post('/api/subjects', {}, { params: { name } });
export const deleteSubjectApi = (id) => request.delete(`/api/subjects/${id}`);

// ========== 单词测验（两阶段 + 错题库） ==========
/** 开始一轮新测验，count 为单词数量；bank 可选，指定则在该词库内抽题 */
export const startQuiz = (count, bank) => request.post('/api/quiz/start', { count, bank });
/** 获取阶段二的拼写题列表 */
export const getSpellWords = (wordIds) => request.post('/api/quiz/spell-words', { wordIds });
/** 阶段二：检查拼写 */
export const checkSpelling = (wordId, userInput) =>
  request.post('/api/quiz/check-spelling', { wordId, userInput });
/** 记录阶段一的错误 */
export const recordQuizError = (wordId, wrongAnswer, errorType = 'meaning') =>
  request.post('/api/quiz/record-error', { wordId, wrongAnswer, errorType });

// 错题库
/** 获取错词列表；bank 可选按词库过滤，book=error|review|mastered 指定本子 */
export const getQuizErrors = (page = 1, size = 20, bank, book = 'error') =>
  request.get('/api/quiz/errors', { params: { page, size, bank, book } });
/** 开始错题复习；bank 可选，用于限定干扰项来源词库 */
export const reviewErrors = (count = 10, bank) =>
  request.post('/api/quiz/errors/review', { count, bank });
/** 错题本「已掌握」：错题本 → 复习本 */
export const markErrorMastered = (id) =>
  request.put(`/api/quiz/errors/${id}/mastered`);
/** 复习本「再次掌握」：复习本 → 已掌握本 */
export const reviewMastered = (id) =>
  request.put(`/api/quiz/errors/${id}/review-mastered`);
/** 看词选意/默写两阶段都通过时，按单词标记为已掌握 */
export const markWordMastered = (wordId) =>
  request.post('/api/quiz/errors/mark-mastered', { wordId });
/** 清零某词库的已掌握进度：全部已掌握单词退回错题本（状态=不熟） */
export const resetMastered = (bank) =>
  request.post('/api/quiz/errors/reset-mastered', null, { params: bank ? { bank } : {} });
/** 已掌握本「忘了」：退回错题本 */
export const forgetError = (id) =>
  request.put(`/api/quiz/errors/${id}/forget`);
/** 删除错题记录 */
export const deleteQuizError = (id) =>
  request.delete(`/api/quiz/errors/${id}`);
/** 错题统计；bank 可选，按词库统计 */
export const errorStats = (bank) =>
  request.get('/api/quiz/errors/stats', { params: { bank } });

// 艾宾浩斯今日复习
/** 获取今日到期需复习的错词；bank 可选，按词库过滤 */
export const getTodayReview = (limit = 20, bank) =>
  request.get('/api/quiz/review/today', { params: { limit, bank } });
/** 提交单条复习结果（correct: 是否记住） */
export const submitReviewResult = (recordId, correct) =>
  request.post('/api/quiz/review/submit', { recordId, correct });

// ========== AI 智能扩展词汇 ==========
export const aiExpandVocabulary = (word) => {
  return request.post('/api/ai/vocabulary/expand', { word }, { timeout: 60000 });
};

// ========== AI 通用对话 ==========
export const aiChat = (message, history, context) => {
  return request.post('/api/ai/chat', { message, history, context }, { timeout: 60000 });
};

// ========== 家长端 ==========
/** 我的孩子列表 */
export const parentChildren = () => request.get('/api/parent/children');
/** 绑定孩子（传孩子的学生登录账号） */
export const parentBindChild = (childUsername) =>
  request.post('/api/parent/bind', null, { params: { childUsername } });
/** 孩子的学习分析报告 */
export const parentChildAnalysis = (childId) => request.get(`/api/parent/child/${childId}/analysis`);
/** 孩子的作业列表（含逾期标记） */
export const parentChildHomeworks = (childId) => request.get(`/api/parent/child/${childId}/homeworks`);
/** 孩子的本周学习周报（最近 7 天作业/批改/测验数据聚合） */
export const parentChildWeeklyReport = (childId) => request.get(`/api/parent/child/${childId}/weekly-report`);
/** 孩子的 AI 内容缓存（type=daily_advice 每日学习建议 / weekly_summary 周报总结，后端定时预生成） */
export const parentChildAiContent = (childId, type) =>
  request.get(`/api/parent/child/${childId}/ai-content`, { params: { type } });
/** 手动重新生成孩子的 AI 内容（覆盖当天/本周缓存） */
export const parentChildAiRefresh = (childId, type) =>
  request.post(`/api/parent/child/${childId}/ai-content/refresh`, null, { params: { type } });
/** 某份作业的详细内容（题目/提交内容/批改结果） */
export const parentChildHomeworkDetail = (childId, homeworkId) =>
  request.get(`/api/parent/child/${childId}/homework/${homeworkId}`);

// ========== 站内通知（三端通用） ==========
/** 我的通知列表 */
export const myNotifications = () => request.get('/api/notifications');
/** 未读通知数 */
export const notifyUnreadCount = () => request.get('/api/notifications/unread-count');
/** 标记单条通知已读 */
export const notifyMarkRead = (id) => request.post(`/api/notifications/${id}/read`);
/** 全部标记已读 */
export const notifyMarkAllRead = () => request.post('/api/notifications/read-all');

// ========== 微信推送设置 ==========
/** 我的推送设置（令牌打码返回） */
export const getPushSetting = () => request.get('/api/me/push-token');
/** 保存推送令牌（传空字符串表示关闭推送） */
export const savePushToken = (token) =>
  request.put('/api/me/push-token', null, { params: { token } });
/** 发一条测试推送 */
export const testPush = () => request.post('/api/me/push-test');
/** 保存订阅偏好（key=通知类型，value=是否订阅；勾选即时生效） */
export const saveNotifyPrefs = (prefs) =>
  request.put('/api/me/notify-prefs', prefs);
/** 本人修改密码（教师/学生/家长/管理员通用，需校验原密码） */
export const changeMyPassword = (oldPassword, newPassword) =>
  request.post('/api/me/password', { oldPassword, newPassword });

// ========== 管理端（仅 role=admin 可访问，后端逐接口校验） ==========
/** 用户分页列表（keyword 搜账号/姓名/手机号；role/status 筛选） */
export const adminUsers = (params) => request.get('/api/admin/users', { params });
/** 重置密码（统一重置为 123456，返回明文新密码） */
export const adminResetPassword = (id) =>
  request.post(`/api/admin/users/${id}/reset-password`);
/** 管理员把某用户密码改成自定义新密码 */
export const adminUpdatePassword = (id, newPassword) =>
  request.post(`/api/admin/users/${id}/password`, { newPassword });
/** 删除用户（连带清理绑定关系与登录态；admin 与自己不可删） */
export const adminDeleteUser = (id) => request.delete(`/api/admin/users/${id}`);
/** 封禁(0) / 解封(1) */
export const adminSetUserStatus = (id, status) =>
  request.post(`/api/admin/users/${id}/status`, null, { params: { status } });
/** 调整角色（teacher/student/parent） */
export const adminUpdateRole = (id, role) =>
  request.post(`/api/admin/users/${id}/role`, null, { params: { role } });
/** 解除绑定（type=parent 解除家长 / type=teacher 解除老师） */
export const adminUnbind = (id, type) =>
  request.post(`/api/admin/users/${id}/unbind`, null, { params: { type } });
/** 数据看板（用户总量/角色分布/封禁数/站内通知总量 + 最近 7 天注册趋势） */
export const adminDashboard = () => request.get('/api/admin/dashboard');
/** 审计日志分页（可按 action 筛选） */
export const adminAuditLogs = (params) =>
  request.get('/api/admin/audit-logs', { params });

// ========== 家教信息发布与投递 ==========
/** 教师端：开放中的单子分页（keyword/省份/城市/jobType 筛选；jobType=1家教单 2同行合作单 不传全部） */
export const openTutorJobs = (params) => request.get('/api/tutor-jobs', { params });
/** 教师端：投递简历（message 为附言；同一单只能投一次） */
export const applyTutorJob = (jobId, message) =>
  request.post(`/api/tutor-jobs/${jobId}/apply`, { message });
/** 教师端：我投递过的简历列表（含审核状态 + 试课状态） */
export const myTutorJobApplications = (params) =>
  request.get('/api/tutor-jobs/my-applications', { params });
/** 教师端：确认试课结果（成功→订单完成；失败→单子重开 + 清空投递历史 + 登记退费工单） */
export const submitTrialResult = (applicationId, success) =>
  request.post(`/api/tutor-jobs/applications/${applicationId}/trial`, { success });

/**
 * 管理端：单子分页。view 给状态下拉与概览条共用：
 * open开放中 / closed已关闭 / trial试课中 / done已完成 / pendingApply待审核投递 / refund试课失败待退费；
 * 传了 view 就忽略 status。
 */
export const adminTutorJobs = (params) =>
  request.get('/api/admin/tutor-jobs', { params });
/** 管理端：发布单子（data 可带 jobType 与 templateId，传了 templateId 就先用科目模板预填） */
export const adminPublishTutorJob = (data) =>
  request.post('/api/admin/tutor-jobs', data);
/** 管理端：编辑家教单（仅开放中可编辑） */
export const adminUpdateTutorJob = (id, data) =>
  request.put(`/api/admin/tutor-jobs/${id}`, data);
/** 管理端：手动下架（关闭） */
export const adminCloseTutorJob = (id) =>
  request.post(`/api/admin/tutor-jobs/${id}/close`);
/** 管理端：重新上架（清掉旧通过记录） */
export const adminReopenTutorJob = (id) =>
  request.post(`/api/admin/tutor-jobs/${id}/reopen`);
/** 管理端：彻底删除家教单（连带投递与缴费记录；关联家长需求回退为待发布） */
export const adminDeleteTutorJob = (id) =>
  request.post(`/api/admin/tutor-jobs/${id}/delete`);
/** 管理端：家教单详情（完整信息 + 投递数 + 家长联系方式） */
export const adminTutorJobDetail = (id) =>
  request.get(`/api/admin/tutor-jobs/${id}`);
/** 管理端：某单的投递列表（审核用；附家长联系方式） */
export const adminTutorJobApplications = (jobId, params) =>
  request.get(`/api/admin/tutor-jobs/${jobId}/applications`, { params });
/** 管理端：审核投递（通过后该单自动关闭） */
export const adminReviewTutorJobApplication = (applicationId, approved) =>
  request.post(`/api/admin/tutor-jobs/applications/${applicationId}/review`, null, { params: { approved } });

// ========== 家教 V2：教师简历 / 家长需求 / 接单配置 / 缴费解锁 ==========
/** 教师端：我的简历（一人一份） */
export const myTutorProfile = () => request.get('/api/tutor-profile/me');
/** 教师端：保存简历（upsert） */
export const saveTutorProfile = (data) => request.post('/api/tutor-profile/me', data);
/** 教师端：接单须知 + 管理端联系方式 + 收款码 */
export const tutorJobNotice = () => request.get('/api/tutor-profile/notice');

/** 家长端：提交找家教需求（只手机号必填，微信号选填） */
export const submitParentDemand = (data) => request.post('/api/parent-tutor/demands', data);
/** 家长端：我的需求单列表 */
export const myParentDemands = (params) => request.get('/api/parent-tutor/demands', { params });
/** 家长端：某需求单收到的教师简历投递列表 */
export const parentDemandApplications = (demandId) =>
  request.get(`/api/parent-tutor/demands/${demandId}/applications`);
/** 家长端：通过/拒绝某份投递（通过后返回管理端联系方式） */
export const parentReviewApplication = (applicationId, approved) =>
  request.post(`/api/parent-tutor/applications/${applicationId}/review`, null, { params: { approved } });
/** 家长端：申请删除需求单（管理端确认后才会真正删除） */
export const parentRequestDeleteDemand = (demandId) =>
  request.post(`/api/parent-tutor/demands/${demandId}/delete-request`);

/** 管理端：家长需求列表（keyword 搜订单号/需求编号/家长称呼/电话/微信；status：-1全部 0待发布 1已发布；deleteRequested=1 只看删除申请） */
export const adminParentDemands = (params) =>
  request.get('/api/admin/tutor/demands', { params });
/** 管理端：家教业务待办统计（待发布需求/删除申请/待审核投递/试课中/缴费收入） */
export const adminTutorTodo = () => request.get('/api/admin/tutor/todo');
/** 管理端：家长需求详情（含联系方式与关联家教单） */
export const adminParentDemandDetail = (demandId) =>
  request.get(`/api/admin/tutor/demands/${demandId}`);
/** 管理端：把家长需求发布为家教单 */
export const adminPublishParentDemand = (demandId, data) =>
  request.post(`/api/admin/tutor/demands/${demandId}/publish`, data);
/** 管理端：确认删除家长需求单（连带删除关联家教单、投递、缴费记录） */
export const adminConfirmDeleteDemand = (demandId) =>
  request.post(`/api/admin/tutor/demands/${demandId}/delete`);
/** 管理端：读取接单配置（须知/电话/微信/收款码） */
export const adminGetTutorConfig = () => request.get('/api/admin/tutor/config');
/** 管理端：保存接单配置 */
export const adminSaveTutorConfig = (data) => request.post('/api/admin/tutor/config', data);
/** 管理端：某单的缴费列表 */
export const adminJobPayments = (jobId) =>
  request.get(`/api/admin/tutor/jobs/${jobId}/payments`);
/** 管理端：标记教师已缴费（解锁投递） */
export const adminMarkPaid = (jobId, teacherId, data) =>
  request.post(`/api/admin/tutor/jobs/${jobId}/payments/${teacherId}`, data);
/** 管理端：退费（金额清零 + 写退费流水 + 办结该教师本单的退费工单） */
export const adminRefundPaid = (jobId, teacherId, data) =>
  request.post(`/api/admin/tutor/jobs/${jobId}/payments/${teacherId}/refund`, data);

// ========== 家教 V3：同行合作单 / 邀请码 / 退费工单 / 缴费流水 / 科目模板 / 批量发布 ==========
/** 教师端：我的退费工单（试课失败后投递记录已被清空，退费进度只能从这里看） */
export const myRefundTasks = () => request.get('/api/tutor-jobs/my-refund-tasks');
/** 教师端：某单的转发文案（可直接复制到微信，末尾附自己的邀请码） */
export const tutorJobShareText = (jobId) => request.get(`/api/tutor-jobs/${jobId}/share-text`);
/** 教师端：我的邀请码 + 已邀请人数 + 邀请名单 */
export const myInviteInfo = () => request.get('/api/tutor-profile/invite');

/** 管理端：把一大段多条家教信息拆分成草稿（不落库；大模型算得久，超时给 180s） */
export const adminParseTutorJobs = (data) =>
  request.post('/api/admin/tutor-jobs/parse', data, { timeout: 180000 });
/** 管理端：批量发布（逐条独立成败，返回 { success, fail, jobs, errors }） */
export const adminBatchPublishTutorJobs = (jobs) =>
  request.post('/api/admin/tutor-jobs/batch', jobs);
/** 管理端：科目订单模板列表（jobType 可选） */
export const adminJobTemplates = (params) =>
  request.get('/api/admin/tutor-jobs/templates', { params });
/** 管理端：新增/编辑科目模板（同一科目同一单类型只允许一份） */
export const adminSaveJobTemplate = (data) =>
  request.post('/api/admin/tutor-jobs/templates', data);
/** 管理端：删除科目模板 */
export const adminDeleteJobTemplate = (templateId) =>
  request.post(`/api/admin/tutor-jobs/templates/${templateId}/delete`);
/** 管理端：退费工单分页（status：0待退费 1已办结 不传全部；keyword 搜单号/教师姓名/电话） */
export const adminRefundTasks = (params) =>
  request.get('/api/admin/tutor-jobs/refund-tasks', { params });
/** 管理端：办结退费工单（有缴费则退款清零，免费单仅标记已处理） */
export const adminSettleRefundTask = (taskId, note) =>
  request.post(`/api/admin/tutor-jobs/refund-tasks/${taskId}/settle`, null, { params: { note } });
/** 管理端：缴费/退费流水分页（type：1缴费 2退费；只增不改，时间永久留痕） */
export const adminPaymentLogs = (params) =>
  request.get('/api/admin/tutor-jobs/payment-logs', { params });
/** 管理端：某位老师邀请来的账号名单（老师详细信息里看） */
export const adminUserInvites = (id) => request.get(`/api/admin/users/${id}/invites`);

// ========== App 版本 / wgt 热更新（管理端发布，客户端自动更新） ==========
/**
 * 上传安装包（.wgt / .apk）：不压缩，原样上传到 OSS，返回 { fileUrl }。
 * 大文件（>5MB，APK）优先「浏览器直传 OSS」——先向后端要一个预签名 PUT URL，
 * 再直接把文件 PUT 到 OSS，绕开服务器中转（服务器上行带宽有限，APK 走服务端约 45s 且易超时）。
 * 直传失败（如 OSS 未配 CORS）时自动回退到后端中转 /api/files/upload。
 */
export const uploadWgt = async (file) => {
  const contentType = file.type || 'application/octet-stream';
  if (file.size > 5 * 1024 * 1024) {
    try {
      const p = await request.post('/api/files/presign', { filename: file.name, contentType });
      if (p && p.uploadUrl) {
        // 用裸 axios 直传 OSS（不要带本站的 Authorization 头）
        await axios.put(p.uploadUrl, file, {
          headers: { 'Content-Type': contentType },
          timeout: 600000
        });
        return { fileUrl: p.fileUrl };
      }
    } catch (e) {
      console.warn('直传 OSS 失败，回退到后端中转:', e && e.message ? e.message : e);
    }
  }
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/api/files/upload', formData, { timeout: 600000 });
};
/** 管理端发布新 App 版本（versionCode 必须递增；wgtUrl/apkUrl 至少一个） */
export const adminPublishAppVersion = (data) => request.post('/api/admin/app/version', data);
/** 管理端查看版本历史（最近 20 条） */
export const adminAppVersions = (platform) =>
  request.get('/api/admin/app/versions', { params: platform ? { platform } : {} });
/** 管理端删除一条版本记录（按主键 id） */
export const adminDeleteAppVersion = (id) => request.delete(`/api/admin/app/version/${id}`);
/** 公开：官网首页获取最新安装包（APK）下载信息，免登录；返回 { hasApk, apkUrl, versionName, versionCode, changelog } */
export const getAppDownload = (platform = 'android') =>
  request.get('/api/app/download', { params: { platform } });

