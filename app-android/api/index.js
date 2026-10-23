import request, { get, post, put, del, postQuery, putQuery, postForm, putForm, upload as uploadFile, uploadTo } from '../utils/request.js';

export { uploadFile as upload };
export default request;

// ========== 鉴权 ==========
export const login = (data) => post('/api/auth/login', data);
export const register = (data) => post('/api/auth/register', data);
export const logoutApi = (refreshToken) => post('/api/auth/logout', { refreshToken });

// ========== 用户管理 ==========
/** 用户分页列表（keyword 搜账号/姓名/手机号；role/status 筛选） */
export const adminUsers = (params) => get('/api/admin/users', params);
/** 重置密码（统一重置为 123456，返回明文新密码） */
export const adminResetPassword = (id) => post(`/api/admin/users/${id}/reset-password`);
/** 封禁(0) / 解封(1) */
export const adminSetUserStatus = (id, status) => postQuery(`/api/admin/users/${id}/status`, { status });
/** 调整角色（teacher/student/parent） */
export const adminUpdateRole = (id, role) => postQuery(`/api/admin/users/${id}/role`, { role });
/** 解除绑定（type=parent 解除家长 / type=teacher 解除老师） */
export const adminUnbind = (id, type) => postQuery(`/api/admin/users/${id}/unbind`, { type });
/** 管理员把某用户密码改成自定义新密码 */
export const adminUpdatePassword = (id, newPassword) => post(`/api/admin/users/${id}/password`, { newPassword });
/** 删除用户（连带清理绑定关系与登录态；admin 与自己不可删） */
export const adminDeleteUser = (id) => del(`/api/admin/users/${id}`);
/** 某位老师邀请来的账号名单 */
export const adminUserInvites = (id) => get(`/api/admin/users/${id}/invites`);

// ========== 数据看板 ==========
/** 用户总量/角色分布/封禁数/通知总量 + 最近 7 天注册趋势 */
export const adminDashboard = () => get('/api/admin/dashboard');
/** 家教业务待办统计（待发布需求/删除申请/待审核投递/试课中/缴费收入等） */
export const adminTutorTodo = () => get('/api/admin/tutor/todo');

// ========== 家教单 / 同行合作单 ==========
/** 单子分页。view：open/closed/trial/pendingApply/refund/done；jobType：1家教单 2合作单 */
export const adminTutorJobs = (params) => get('/api/admin/tutor-jobs', params);
/** 发布单子（data 可带 jobType 与 templateId） */
export const adminPublishTutorJob = (data) => post('/api/admin/tutor-jobs', data);
/** 编辑家教单（仅开放中可编辑） */
export const adminUpdateTutorJob = (id, data) => put(`/api/admin/tutor-jobs/${id}`, data);
/** 手动下架（关闭） */
export const adminCloseTutorJob = (id) => post(`/api/admin/tutor-jobs/${id}/close`);
/** 重新上架（清掉旧通过记录） */
export const adminReopenTutorJob = (id) => post(`/api/admin/tutor-jobs/${id}/reopen`);
/** 彻底删除家教单（连带投递与缴费记录） */
export const adminDeleteTutorJob = (id) => post(`/api/admin/tutor-jobs/${id}/delete`);
/** 家教单详情（完整信息 + 投递数 + 家长联系方式） */
export const adminTutorJobDetail = (id) => get(`/api/admin/tutor-jobs/${id}`);
/** 某单的投递列表（审核用；附家长联系方式） */
export const adminTutorJobApplications = (jobId, params) => get(`/api/admin/tutor-jobs/${jobId}/applications`, params);
/** 审核投递（通过后该单自动关闭） */
export const adminReviewTutorJobApplication = (applicationId, approved) =>
	postQuery(`/api/admin/tutor-jobs/applications/${applicationId}/review`, { approved });

// ========== 家长需求 ==========
/** 家长需求列表（keyword 搜订单号/需求编号/家长称呼/电话/微信；status：-1全部 0待发布 1已发布；deleteRequested=1 只看删除申请） */
export const adminParentDemands = (params) => get('/api/admin/tutor/demands', params);
/** 家长需求详情（含联系方式与关联家教单） */
export const adminParentDemandDetail = (demandId) => get(`/api/admin/tutor/demands/${demandId}`);
/** 把家长需求发布为家教单 */
export const adminPublishParentDemand = (demandId, data) => post(`/api/admin/tutor/demands/${demandId}/publish`, data);
/** 确认删除家长需求单（连带删除关联家教单、投递、缴费记录） */
export const adminConfirmDeleteDemand = (demandId) => post(`/api/admin/tutor/demands/${demandId}/delete`);

// ========== 接单配置 ==========
/** 读取接单配置（须知/电话/微信/收款码） */
export const adminGetTutorConfig = () => get('/api/admin/tutor/config');
/** 保存接单配置 */
export const adminSaveTutorConfig = (data) => post('/api/admin/tutor/config', data);

// ========== 缴费 / 退费 ==========
/** 某单的缴费列表 */
export const adminJobPayments = (jobId) => get(`/api/admin/tutor/jobs/${jobId}/payments`);
/** 标记教师已缴费（解锁投递） */
export const adminMarkPaid = (jobId, teacherId, data) => post(`/api/admin/tutor/jobs/${jobId}/payments/${teacherId}`, data);
/** 退费（金额清零 + 写退费流水 + 办结该教师本单的退费工单） */
export const adminRefundPaid = (jobId, teacherId, data) => post(`/api/admin/tutor/jobs/${jobId}/payments/${teacherId}/refund`, data);
/** 退费工单分页（status：0待退费 1已办结 不传全部；keyword 搜单号/教师姓名/电话） */
export const adminRefundTasks = (params) => get('/api/admin/tutor-jobs/refund-tasks', params);
/** 办结退费工单（有缴费则退款清零，免费单仅标记已处理） */
export const adminSettleRefundTask = (taskId, note) => postQuery(`/api/admin/tutor-jobs/refund-tasks/${taskId}/settle`, { note });
/** 缴费/退费流水分页（type：1缴费 2退费） */
export const adminPaymentLogs = (params) => get('/api/admin/tutor-jobs/payment-logs', params);

// ========== 科目模板 ==========
/** 科目订单模板列表（jobType 可选） */
export const adminJobTemplates = (params) => get('/api/admin/tutor-jobs/templates', params);
/** 新增/编辑科目模板（同一科目同一单类型只允许一份） */
export const adminSaveJobTemplate = (data) => post('/api/admin/tutor-jobs/templates', data);
/** 删除科目模板 */
export const adminDeleteJobTemplate = (templateId) => post(`/api/admin/tutor-jobs/templates/${templateId}/delete`);

// ========== 批量发布 ==========
/** 把一大段多条家教信息拆分成草稿（不落库；大模型算得久，超时给 180s） */
export const adminParseTutorJobs = (data) => post('/api/admin/tutor-jobs/parse', data, { timeout: 180000 });
/** 批量发布（逐条独立成败，返回 { success, fail, jobs, errors }） */
export const adminBatchPublishTutorJobs = (jobs) => post('/api/admin/tutor-jobs/batch', jobs);

// ========== 🔄 App 版本 / wgt 热更新 ==========
/** 客户端检查更新（platform=android，current=当前 versionCode）；返回 { hasUpdate, forceUpdate, versionName, wgtUrl, apkUrl, changelog } */
export const checkAppVersion = (platform, current) => get('/api/app/version', { platform, current });

// ========== 🔔 站内通知（三端通用） ==========
export const myNotifications = () => get('/api/notifications');
export const notifyUnreadCount = () => get('/api/notifications/unread-count');
export const notifyMarkRead = (id) => post(`/api/notifications/${id}/read`);
export const notifyMarkAllRead = () => post('/api/notifications/read-all');

// ========== 📱 微信推送设置 ==========
export const getPushSetting = () => get('/api/me/push-token');
export const savePushToken = (token) => putQuery('/api/me/push-token', { token });
export const testPush = () => post('/api/me/push-test');
export const saveNotifyPrefs = (prefs) => put('/api/me/notify-prefs', prefs);
/** 本人修改密码（教师/学生/家长/管理员通用，需校验原密码） */
export const changeMyPassword = (oldPassword, newPassword) => post('/api/me/password', { oldPassword, newPassword });

// ========== 👪 家长端：孩子学习情况 ==========
export const parentChildren = () => get('/api/parent/children');
export const parentBindChild = (childUsername) => postQuery('/api/parent/bind', { childUsername });
export const parentChildAnalysis = (childId) => get(`/api/parent/child/${childId}/analysis`);
export const parentChildHomeworks = (childId) => get(`/api/parent/child/${childId}/homeworks`);
export const parentChildWeeklyReport = (childId) => get(`/api/parent/child/${childId}/weekly-report`);
export const parentChildAiContent = (childId, type) => get(`/api/parent/child/${childId}/ai-content`, { type });
export const parentChildAiRefresh = (childId, type) => postQuery(`/api/parent/child/${childId}/ai-content/refresh`, { type });
export const parentChildHomeworkDetail = (childId, homeworkId) => get(`/api/parent/child/${childId}/homework/${homeworkId}`);

// ========== 🔍 家长端：找家教 ==========
export const submitParentDemand = (data) => post('/api/parent-tutor/demands', data);
export const myParentDemands = (params) => get('/api/parent-tutor/demands', params);
export const parentDemandApplications = (demandId) => get(`/api/parent-tutor/demands/${demandId}/applications`);
export const parentReviewApplication = (applicationId, approved) => postQuery(`/api/parent-tutor/applications/${applicationId}/review`, { approved });
export const parentRequestDeleteDemand = (demandId) => post(`/api/parent-tutor/demands/${demandId}/delete-request`);

// ========== 📌 教师端：家教接单 ==========
export const openTutorJobs = (params) => get('/api/tutor-jobs', params);
export const applyTutorJob = (jobId, message) => post(`/api/tutor-jobs/${jobId}/apply`, { message });
export const myTutorJobApplications = (params) => get('/api/tutor-jobs/my-applications', params);
export const submitTrialResult = (applicationId, success) => post(`/api/tutor-jobs/applications/${applicationId}/trial`, { success });
export const myRefundTasks = () => get('/api/tutor-jobs/my-refund-tasks');
export const tutorJobShareText = (jobId) => get(`/api/tutor-jobs/${jobId}/share-text`);
export const myInviteInfo = () => get('/api/tutor-profile/invite');
export const myTutorProfile = () => get('/api/tutor-profile/me');
export const saveTutorProfile = (data) => post('/api/tutor-profile/me', data);
export const tutorJobNotice = () => get('/api/tutor-profile/notice');

// ========== 🧑‍🏫 学生 / 老师 / 绑定 ==========
export const students = () => get('/api/students');
export const searchStudents = (name) => get('/api/students/search', name ? { name } : {});
export const bindStudent = (id) => put(`/api/students/${id}/bind`);
export const myTeacher = () => get('/api/students/my-teacher');
export const teachers = () => get('/api/teachers');
export const saveProfile = (data) => post('/api/students/profile', data);
export const analysis = (id) => get(`/api/students/${id}/analysis`);
/** 某学生的档案（教师端编辑学生基本信息用：年级/学科/掌握程度） */
export const studentProfile = (id) => get(`/api/students/${id}/profile`);

// ========== 📝 作业 ==========
export const homeworkList = (studentId) => get('/api/homeworks', studentId ? { studentId } : {});
export const getHomework = (id) => get(`/api/homeworks/${id}`);
export const getGroupedHomeworks = () => get('/api/homeworks/grouped');
export const addHomework = (data) => post('/api/homeworks', data);
export const updateHomework = (id, data) => put(`/api/homeworks/${id}`, data);
export const submitHomework = (data) => post('/api/homeworks/submit', data);
export const submits = (id) => get(`/api/homeworks/${id}/submits`);
export const review = (data) => post('/api/homeworks/review', data);
export const reviewResult = (id) => get(`/api/homeworks/${id}/review`);
export const getSubmitReview = (submitId) => get(`/api/homeworks/submits/${submitId}/review`);
export const overdueHomework = () => get('/api/homeworks/overdue');
export const deleteHomework = (id) => del(`/api/homeworks/${id}`);

// ========== 🎯 答题卡作业（AI 出卷 + 自动判分） ==========
export const aiParseQuestions = (data) => post('/api/ai/answer-sheet/parse', data, { timeout: 120000 });
/** 图片识别出卷：拍照/截图上传，多模态模型转文字后走与文本解析相同的出卷流程 */
export const aiParseImageQuestions = (filePath, formData) =>
	uploadTo('/api/ai/answer-sheet/parse-image', filePath, formData, 'file', { timeout: 180000, failMsg: '图片识别失败，请重试' });
export const assignAnswerSheet = (data) => post('/api/homeworks/answer-sheet', data);
export const answerSheetQuestions = (homeworkId) => get(`/api/homeworks/answer-sheet/${homeworkId}/questions`);
export const answerSheetPaper = (homeworkId) => get(`/api/homeworks/answer-sheet/${homeworkId}/paper`);
export const submitAnswerSheet = (data) => post('/api/homeworks/answer-sheet/submit', data, { timeout: 120000 });
export const answerSheetResult = (submitId) => get(`/api/homeworks/answer-sheet/result/${submitId}`);
export const fixAnswerSheet = (data) => post('/api/homeworks/answer-sheet/fix', data);

// ========== 🎯 学习目标 ==========
export const goals = (studentId) => get('/api/goals', { studentId });
export const addGoal = (data) => post('/api/goals', data);

// ========== 📚 词汇 ==========
export const vocabList = (params) => get('/api/vocabularies', params);
export const vocabSearch = (params) => get('/api/vocabularies/search', params);
export const addVocab = (data) => post('/api/vocabularies', data);
export const updateVocab = (id, data) => put(`/api/vocabularies/${id}`, data);
export const deleteVocab = (id) => del(`/api/vocabularies/${id}`);
export const randomVocab = (params) => get('/api/vocabularies/random', params);
export const vocabBanks = () => get('/api/vocabularies/banks');

// ========== 📁 学习资料（新增/编辑走表单体；预览 Word 等为风险功能，后续再议） ==========
export const resourceList = (params) => get('/api/resources', params);
export const resourceTypes = (subjectId) => get('/api/resources/types', subjectId ? { subjectId } : {});
export const searchTeachers = (keyword) => get('/api/resources/teachers/search', keyword ? { keyword } : {});
export const addResource = (data) => postForm('/api/resources', data);
export const updateResource = (id, data) => putForm(`/api/resources/${id}`, data);
export const getResource = (id) => get(`/api/resources/${id}`);
export const deleteResource = (id) => del(`/api/resources/${id}`);
export const copyResource = (id) => post(`/api/resources/${id}/copy`);
export const copyAllResources = (teacherId) => postQuery('/api/resources/copy-all', { teacherId });

// ========== 📖 学科 ==========
export const subjectList = () => get('/api/subjects');
export const addSubjectApi = (name) => postQuery('/api/subjects', { name });
export const deleteSubjectApi = (id) => del(`/api/subjects/${id}`);

// ========== 🃏 单词测验 + 错题库 + 艾宾浩斯复习 ==========
export const startQuiz = (count, bank) => post('/api/quiz/start', bank ? { count, bank } : { count });
export const getSpellWords = (wordIds) => post('/api/quiz/spell-words', { wordIds });
export const checkSpelling = (wordId, userInput) => post('/api/quiz/check-spelling', { wordId, userInput });
export const recordQuizError = (wordId, wrongAnswer, errorType = 'meaning') => post('/api/quiz/record-error', { wordId, wrongAnswer, errorType });
export const getQuizErrors = (page = 1, size = 20, bank, book = 'error') => {
	const params = { page, size, book };
	if (bank) params.bank = bank;
	return get('/api/quiz/errors', params);
};
export const reviewErrors = (count = 10, bank) => post('/api/quiz/errors/review', bank ? { count, bank } : { count });
export const markErrorMastered = (id) => put(`/api/quiz/errors/${id}/mastered`);
export const reviewMastered = (id) => put(`/api/quiz/errors/${id}/review-mastered`);
export const forgetError = (id) => put(`/api/quiz/errors/${id}/forget`);
export const markWordMastered = (wordId) => post('/api/quiz/errors/mark-mastered', { wordId });
export const resetMastered = (bank) => postQuery('/api/quiz/errors/reset-mastered', bank ? { bank } : {});
export const deleteQuizError = (id) => del(`/api/quiz/errors/${id}`);
export const errorStats = (bank) => get('/api/quiz/errors/stats', bank ? { bank } : {});
export const getTodayReview = (limit = 20, bank) => get('/api/quiz/review/today', bank ? { limit, bank } : { limit });
export const submitReviewResult = (recordId, correct) => post('/api/quiz/review/submit', { recordId, correct });

// ========== 🤖 AI ==========
export const aiExpandVocabulary = (word) => post('/api/ai/vocabulary/expand', { word }, { timeout: 60000 });
export const aiChat = (message, history, context) => post('/api/ai/chat', { message, history, context }, { timeout: 60000 });
