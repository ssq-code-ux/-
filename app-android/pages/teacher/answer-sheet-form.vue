<template>
	<view class="page">
		<!-- 步骤指示 -->
		<view class="steps">
			<view v-for="(s, i) in stepTitles" :key="i" class="step" :class="{ on: step === i, done: step > i }">
				<text class="step-no">{{ step > i ? '✓' : i + 1 }}</text>
				<text class="step-tx">{{ s }}</text>
				<text v-if="i < stepTitles.length - 1" class="step-line"></text>
			</view>
		</view>

		<!-- ========== 第 1 步：输入题目 ========== -->
		<template v-if="step === 0">
			<view v-if="appending" class="card append-tip">➕ 追加模式：解析结果会接在已有 {{ sections.length }} 个大题后面，不会覆盖前面的题。</view>

			<view class="card">
				<!-- 从作业管理进来时学生已选定；从首页进来先选学生 -->
				<view class="fi" v-if="presetStudent">
					<text class="fl">布置给</text>
					<view class="picker-in readonly">{{ studentName }}</view>
				</view>
				<view class="fi" v-else>
					<text class="fl req">布置给</text>
					<picker mode="selector" :range="studentRange" :value="studentSelIdx" @change="onStudentPick">
						<view class="picker-in">{{ studentRange[studentSelIdx] }} ▾</view>
					</picker>
				</view>
				<view class="fi">
					<text class="fl">学科</text>
					<picker mode="selector" :range="SHEET_SUBJECTS" :value="subjectIdx" @change="onSubjectPick">
						<view class="picker-in">{{ SHEET_SUBJECTS[subjectIdx] }} ▾</view>
					</picker>
				</view>
				<view class="fi">
					<text class="fl">这段是（题型提示，可留空让 AI 判断）</text>
					<input class="input" v-model="sectionHint" placeholder="如：完形填空 / 文言文阅读 / 解答题" maxlength="30" />
				</view>
				<view class="fi">
					<text class="fl">题目文本</text>
					<textarea class="textarea big" v-model="rawText" :placeholder="rawPlaceholder" maxlength="-1" />
				</view>
				<view class="fi">
					<text class="fl">或上传题目图片（拍照/截图，选图后优先用图片识别）</text>
					<view v-if="!imagePath" class="img-add" @click="chooseImg">📷 选择题目图片</view>
					<view v-else class="img-box">
						<image class="img" :src="imagePath" mode="aspectFit" @click="previewImg" />
						<text class="img-del" @click="imagePath = ''">✕</text>
					</view>
				</view>
				<view class="muted tip">解析后按满分 100 分自动均分每题分值。选好学科、写明题型比让 AI 猜准得多；文本解析约需 10~30 秒，图片识别会再久一点。</view>
			</view>
		</template>

		<!-- ========== 第 2 步：核验编辑 ========== -->
		<template v-else-if="step === 1">
			<view class="card sum-bar">
				<view class="sum-tx">共 <text class="hl">{{ sections.length }}</text> 个大题、<text class="hl">{{ questionCount }}</text> 小题，满分 <text class="hl">{{ fullScore }}</text> 分</view>
				<view class="mini-btn mini-btn-primary" @click="redistributeScores">重新均分（满分 100）</view>
				<view class="warn">⚠ AI 给的答案可能有误，发布前请逐题确认</view>
			</view>

			<view v-for="(sec, si) in sections" :key="si" class="card sec">
				<view class="sec-head">
					<text class="sec-seq">{{ si + 1 }}</text>
					<picker class="sec-type" mode="selector" :range="sectionTypeLabels" :value="sectionTypeIdx(sec.sectionType)" @change="onSectionTypePick(si, $event)">
						<view class="picker-in sm">{{ sectionLabel(sec.sectionType) }} ▾</view>
					</picker>
					<input class="input sec-title" v-model="sec.title" placeholder="大题标题" />
					<text class="mini-btn mini-btn-danger" @click="removeSection(si)">删除</text>
				</view>
				<input class="input mt" v-model="sec.instruction" placeholder="答题要求（可选）" />

				<!-- 材料原文（解答题/单句题没有共享材料） -->
				<view v-if="!isNoPassage(sec.sectionType)" class="block">
					<view class="block-label">📄 材料原文<text class="block-tip">文章里的空写成 _1_ _2_，编号对应下面的小题号</text></view>
					<textarea class="textarea" v-model="sec.passage" placeholder="完形/阅读的文章原文、对话原文或写作要求" maxlength="-1" />
				</view>

				<!-- 选词填空的备选词库 -->
				<view v-if="sec.sectionType === 'word_bank'" class="block">
					<view class="block-label">🗂️ 备选词库<text class="block-tip">多个词用 / 分隔</text></view>
					<input class="input" v-model="sec.wordBankText" placeholder="如：make / happy / carefully" />
				</view>

				<!-- 补全对话共用选项框 -->
				<view v-if="sec.sectionType === 'dialogue'" class="block">
					<view class="block-label">🔠 共用选项框<text class="block-tip">所有小题从这里选答案</text><text class="mini-btn mini-btn-primary" @click="addSharedOption(sec)">+ 加一项</text></view>
					<view v-for="(o, oi) in sec.sharedOptions" :key="oi" class="opt-row">
						<text class="opt-key">{{ o.key }}</text>
						<input class="input opt-in" v-model="o.text" placeholder="选项内容" />
						<text class="mini-btn mini-btn-danger" @click="sec.sharedOptions.splice(oi, 1)">✕</text>
					</view>
				</view>

				<!-- 小题 -->
				<view v-for="(q, qi) in sec.questions" :key="qi" class="q">
					<view class="q-head">
						<text class="q-seq">第 {{ globalSeq(si, qi) }} 题</text>
						<picker class="q-type" mode="selector" :range="questionTypeLabels" :value="questionTypeIdx(q.questionType)" @change="onQuestionTypePick(q, $event)">
							<view class="picker-in sm">{{ questionTypeLabel(q.questionType) }} ▾</view>
						</picker>
						<input class="input score" type="number" v-model="q.score" />
						<text class="unit">分</text>
						<text class="mini-btn mini-btn-danger" @click="sec.questions.splice(qi, 1)">删除</text>
					</view>
					<textarea class="textarea sm" v-model="q.content" placeholder="题干" maxlength="-1" auto-height />
					<!-- 选项（选择题/判断题） -->
					<view v-if="q.options && q.options.length" class="q-opts">
						<view v-for="(o, oi) in q.options" :key="oi" class="opt-row">
							<text class="opt-key">{{ o.key }}</text>
							<input class="input opt-in" v-model="o.text" placeholder="选项内容" />
						</view>
					</view>
					<view class="ans-row">
						<text class="ans-label">✅ 标准答案</text>
						<input class="input ans-in" v-model="q.standardAnswer" :placeholder="q.questionType === 'blank' ? '多种写法用竖线分隔，如 is|is not' : '如 B 或 AC'" />
					</view>
					<textarea class="textarea sm mt" v-model="q.analysis" placeholder="解析（学生提交后可见）" maxlength="-1" auto-height />
					<input class="input mt" v-model="q.knowledgePoint" placeholder="知识点标签（可选）" />
				</view>
				<view class="mini-btn add-q" @click="addQuestion(sec)">➕ 加一道小题</view>
			</view>

			<empty-state v-if="!sections.length" text="没有题目，请返回上一步重新解析" />
			<view v-if="sections.length" class="card append-btn" @click="startAppendSection">➕ 再粘一段（追加大题）</view>
		</template>

		<!-- ========== 第 3 步：发布 ========== -->
		<template v-else>
			<view class="card">
				<view class="fi">
					<text class="fl req">答题卡标题</text>
					<input class="input" v-model="form.title" placeholder="如：第三单元语法小测" maxlength="100" />
				</view>
				<view class="fi">
					<text class="fl">作答说明（可选）</text>
					<textarea class="textarea" v-model="form.content" placeholder="作答说明、注意事项" maxlength="2000" />
				</view>
				<view class="fi">
					<text class="fl">截止时间</text>
					<view class="dt-row">
						<picker mode="date" :value="datePart" @change="onDate">
							<view class="picker-in dt">{{ datePart || '选择日期' }} 📅</view>
						</picker>
						<picker mode="time" :value="timePart" @change="onTime">
							<view class="picker-in dt">{{ timePart }} ⏰</view>
						</picker>
						<text v-if="datePart" class="clear-dt" @click="clearDeadline">清除</text>
					</view>
				</view>
			</view>
			<view class="card summary">
				将给 <text class="hl">{{ studentName }}</text> 布置<text class="hl">{{ subject }}</text>答题卡：<text class="hl">{{ sections.length }}</text> 个大题、<text class="hl">{{ questionCount }}</text> 小题，满分 <text class="hl">{{ fullScore }}</text> 分。学生提交后系统自动判分，主观题由 AI 按学科评分，你可以再复核调分。
			</view>
		</template>

		<!-- 底部操作栏 -->
		<view class="bottom-bar">
			<template v-if="step === 0">
				<view class="btn btn-plain" @click="cancel">取消</view>
				<view class="btn btn-primary" :class="{ 'btn-disabled': parsing }" @click="doParse">
					{{ parsing ? 'AI 解析中…' : (imagePath ? '📷 AI 图片识别出卷' : '✨ AI 解析出卷') }}
				</view>
			</template>
			<template v-else-if="step === 1">
				<view class="btn btn-plain" @click="step = 0">上一步</view>
				<view class="btn btn-primary" :class="{ 'btn-disabled': !questionCount }" @click="step = 2">下一步</view>
			</template>
			<template v-else>
				<view class="btn btn-plain" @click="step = 1">上一步</view>
				<view class="btn btn-primary" :class="{ 'btn-disabled': publishing }" @click="doPublish">{{ publishing ? '发布中…' : '✅ 发布答题卡' }}</view>
			</template>
		</view>
	</view>
</template>

<script>
	import { aiParseQuestions, aiParseImageQuestions, assignAnswerSheet, students } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	/* 大题题型：只影响怎么展示（文章/词库/共用选项），判分仍看小题的 questionType */
	const SECTION_TYPES = [
		{ value: 'cloze', label: '完形填空' },
		{ value: 'reading', label: '阅读理解' },
		{ value: 'word_bank', label: '选词填空' },
		{ value: 'blank_fill', label: '盲填' },
		{ value: 'dialogue', label: '补全对话' },
		{ value: 'solution', label: '解答题' },
		{ value: 'writing', label: '书面表达' },
		{ value: 'standalone', label: '单句题' }
	];
	const QUESTION_TYPES = [
		{ value: 'single', label: '单选' },
		{ value: 'multi', label: '多选' },
		{ value: 'judge', label: '判断' },
		{ value: 'blank', label: '填空' },
		{ value: 'short', label: '简答/写作' }
	];
	const SHEET_SUBJECTS = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];

	export default {
		data() {
			return {
				SECTION_TYPES, QUESTION_TYPES, SHEET_SUBJECTS,
				sectionTypeLabels: SECTION_TYPES.map((t) => t.label),
				questionTypeLabels: QUESTION_TYPES.map((t) => t.label),
				stepTitles: ['输入题目', '核验答案', '发布'],
				rawPlaceholder: '一次粘一个大题（阅读/材料题请连文章一起粘），不带答案也行，AI 会自己解。\n英语例：\n二、完形填空\nTom is a middle school student. He __1__ up at six ...\n1. A. get  B. gets  C. getting\n数学例：\n三、解答题\n解方程：2x + 6 = 20',

				step: 0,
				studentId: null, studentName: '',
				presetStudent: false, studentList: [], studentSelIdx: 0,
				subjectIdx: 2, // 默认英语
				sectionHint: '',
				rawText: '',
				imagePath: '',
				appending: false,
				parsing: false, publishing: false,
				sections: [],
				form: { title: '', content: '' },
				datePart: '', timePart: '23:59'
			};
		},
		computed: {
			subject() { return this.SHEET_SUBJECTS[this.subjectIdx]; },
			studentRange() {
				return ['请选择学生'].concat(this.studentList.map((s) => `${s.realName || '未命名'} (@${s.username})`));
			},
			questionCount() { return this.sections.reduce((n, s) => n + ((s.questions || []).length), 0); },
			fullScore() {
				return this.sections.reduce((sum, s) =>
					sum + (s.questions || []).reduce((a, q) => a + (Number(q.score) || 0), 0), 0);
			}
		},
		onLoad(opt) {
			if (!guardRole('teacher')) return;
			uni.setNavigationBarTitle({ title: 'AI 出卷' });
			this.studentId = opt && opt.studentId ? opt.studentId : null;
			if (this.studentId) {
				// 从作业管理进来：学生已选定
				this.presetStudent = true;
				this.studentName = opt && opt.studentName ? decodeURIComponent(opt.studentName) : '该学生';
			} else {
				// 从首页进来：先拉学生列表，让老师在卷子里选
				this.presetStudent = false;
				this.loadStudents();
			}
		},
		methods: {
			sectionLabel(t) { const s = this.SECTION_TYPES.find((x) => x.value === t); return s ? s.label : '题目'; },
			sectionTypeIdx(t) { const i = this.SECTION_TYPES.findIndex((x) => x.value === t); return i < 0 ? this.SECTION_TYPES.length - 1 : i; },
			questionTypeLabel(t) { const s = this.QUESTION_TYPES.find((x) => x.value === t); return s ? s.label : '题目'; },
			questionTypeIdx(t) { const i = this.QUESTION_TYPES.findIndex((x) => x.value === t); return i < 0 ? 3 : i; },
			isNoPassage(t) { return t === 'standalone' || t === 'solution'; },
			globalSeq(si, qi) {
				let n = 0;
				for (let i = 0; i < si; i++) n += (this.sections[i].questions || []).length;
				return n + qi + 1;
			},

			onSubjectPick(e) { this.subjectIdx = Number(e.detail.value); this.sectionHint = ''; },
			async loadStudents() {
				try { this.studentList = (await students()) || []; } catch (e) { this.studentList = []; }
			},
			onStudentPick(e) {
				const idx = Number(e.detail.value);
				this.studentSelIdx = idx;
				if (idx === 0) { this.studentId = null; this.studentName = ''; return; }
				const s = this.studentList[idx - 1];
				this.studentId = s.id;
				this.studentName = s.realName || s.username || '该学生';
			},
			chooseImg() {
				uni.chooseImage({
					count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
					success: (res) => { this.imagePath = (res.tempFilePaths && res.tempFilePaths[0]) || ''; }
				});
			},
			previewImg() { if (this.imagePath) uni.previewImage({ urls: [this.imagePath] }); },

			/* AI 返回的大题补齐前端编辑需要的字段，免得模板里到处判空 */
			normalizeSections(list) {
				return list.map((s) => ({
					sectionType: s.sectionType || 'standalone',
					title: s.title || this.sectionLabel(s.sectionType || 'standalone'),
					passage: s.passage || '',
					instruction: s.instruction || '',
					wordBankText: (s.wordBank || []).join(' / '),
					sharedOptions: s.sharedOptions || [],
					questions: (s.questions || []).map((q) => ({
						questionType: q.questionType || 'blank',
						content: q.content || '',
						options: q.options || [],
						standardAnswer: q.standardAnswer || '',
						analysis: q.analysis || '',
						score: q.score != null ? q.score : 5,
						knowledgePoint: q.knowledgePoint || ''
					}))
				}));
			},

			async doParse() {
				if (this.parsing) return;
				if (!this.imagePath && !String(this.rawText || '').trim()) {
					uni.showToast({ title: '请先粘贴题目或选择题目图片', icon: 'none' }); return;
				}
				this.parsing = true;
				uni.showLoading({ title: 'AI 出卷中…', mask: true });
				try {
					const hint = String(this.sectionHint || '').trim();
					const subj = String(this.subject || '').trim();
					const list = this.imagePath
						? await aiParseImageQuestions(this.imagePath, { defaultScore: '5', sectionHint: hint, subject: subj })
						: await aiParseQuestions({ rawText: this.rawText, defaultScore: 5, sectionHint: hint, subject: subj });
					if (!list || !list.length) {
						uni.showToast({ title: 'AI 没能识别出题目，换个格式或换张更清晰的图片再试试', icon: 'none' });
						return;
					}
					const parsed = this.normalizeSections(list);
					this.sections = this.appending ? this.sections.concat(parsed) : parsed;
					this.appending = false;
					this.redistributeScores();
					this.step = 1;
					this.imagePath = '';
					const qs = parsed.reduce((n, s) => n + s.questions.length, 0);
					uni.showToast({ title: `解析出 ${parsed.length} 个大题、${qs} 小题`, icon: 'none' });
				} catch (e) { /* 已提示 */ } finally {
					uni.hideLoading();
					this.parsing = false;
				}
			},

			/* 按满分 100 自动均分所有小题分值：整除不尽时排前面的题多 1 分，总和恰为 100 */
			redistributeScores() {
				const all = [];
				this.sections.forEach((s) => (s.questions || []).forEach((q) => all.push(q)));
				const n = all.length;
				if (!n) return;
				const base = Math.floor(100 / n);
				const rem = 100 % n;
				all.forEach((q, i) => { q.score = base + (i < rem ? 1 : 0); });
			},

			onSectionTypePick(si, e) {
				const sec = this.sections[si];
				sec.sectionType = this.SECTION_TYPES[Number(e.detail.value)].value;
				// 改成单句题/解答题就没有共享材料了，清掉避免误传
				if (this.isNoPassage(sec.sectionType)) sec.passage = '';
			},
			onQuestionTypePick(q, e) { q.questionType = this.QUESTION_TYPES[Number(e.detail.value)].value; },
			addSharedOption(sec) {
				if (!sec.sharedOptions) sec.sharedOptions = [];
				sec.sharedOptions.push({ key: String.fromCharCode(65 + sec.sharedOptions.length), text: '' });
			},
			addQuestion(sec) {
				sec.questions.push({
					// 作文/解答题要成段作答或写过程，默认简答；其他大题默认填空
					questionType: (sec.sectionType === 'writing' || sec.sectionType === 'solution') ? 'short' : 'blank',
					content: '', options: [], standardAnswer: '', analysis: '', score: 5, knowledgePoint: ''
				});
			},
			removeSection(si) {
				uni.showModal({
					title: '删除大题', content: `确定删除第 ${si + 1} 个大题及其所有小题吗？`, confirmColor: '#e11d48',
					success: (r) => { if (r.confirm) this.sections.splice(si, 1); }
				});
			},
			/* 回到第一步再粘一段：一张卷子通常有好几个大题，分次解析比一次喂整卷准 */
			startAppendSection() {
				this.appending = true;
				this.rawText = '';
				this.sectionHint = '';
				this.imagePath = '';
				this.step = 0;
			},

			onDate(e) { this.datePart = e.detail.value; },
			onTime(e) { this.timePart = e.detail.value; },
			clearDeadline() { this.datePart = ''; this.timePart = '23:59'; },
			buildDeadline() { return this.datePart ? `${this.datePart}T${this.timePart || '23:59'}:00` : ''; },
			splitWordBank(text) {
				if (!text) return null;
				const arr = String(text).split(/[\/／,，、\n]/).map((s) => s.trim()).filter(Boolean);
				return arr.length ? arr : null;
			},

			cancel() { uni.navigateBack(); },

			async doPublish() {
				if (this.publishing) return;
				if (!this.studentId) { uni.showToast({ title: '请先选择学生', icon: 'none' }); return; }
				if (!String(this.form.title || '').trim()) { uni.showToast({ title: '请填写答题卡标题', icon: 'none' }); return; }
				for (const sec of this.sections) {
					if ((sec.questions || []).some((q) => !q.content || !String(q.content).trim())) {
						uni.showToast({ title: '有题目的题干为空，请返回核验', icon: 'none' }); return;
					}
					if (!this.isNoPassage(sec.sectionType) && !String(sec.passage || '').trim()) {
						uni.showToast({ title: `【${sec.title || this.sectionLabel(sec.sectionType)}】缺材料原文`, icon: 'none' }); return;
					}
				}
				this.publishing = true;
				try {
					let seq = 1;
					await assignAnswerSheet({
						studentId: this.studentId,
						title: this.form.title.trim(),
						content: this.form.content,
						subject: String(this.subject || '').trim() || null,
						deadline: this.buildDeadline() || null,
						sections: this.sections.map((sec, si) => ({
							seq: si + 1,
							sectionType: sec.sectionType,
							title: sec.title,
							instruction: sec.instruction || null,
							passage: this.isNoPassage(sec.sectionType) ? null : (sec.passage || null),
							wordBank: sec.sectionType === 'word_bank' ? this.splitWordBank(sec.wordBankText) : null,
							sharedOptions: (sec.sharedOptions && sec.sharedOptions.length) ? sec.sharedOptions : null,
							questions: (sec.questions || []).map((q) => ({
								seq: seq++,
								questionType: q.questionType,
								content: q.content,
								options: (q.options && q.options.length) ? q.options : null,
								standardAnswer: q.standardAnswer,
								analysis: q.analysis,
								score: Number(q.score) || 5,
								knowledgePoint: q.knowledgePoint
							}))
						}))
					});
					uni.showToast({ title: '答题卡已发布', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 800);
				} catch (e) { /* 已提示 */ } finally { this.publishing = false; }
			}
		}
	};
</script>

<style scoped>
	.steps { display: flex; align-items: center; justify-content: center; padding: 24rpx; }
	.step { display: flex; align-items: center; }
	.step-no { width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: #e5e7eb; color: #9ca3af; font-size: 24rpx; font-weight: 700; }
	.step.on .step-no { background: #6366f1; color: #ffffff; }
	.step.done .step-no { background: #16a34a; color: #ffffff; }
	.step-tx { margin: 0 10rpx; font-size: 24rpx; color: #9ca3af; }
	.step.on .step-tx { color: #1f2d52; font-weight: 700; }
	.step.done .step-tx { color: #16a34a; }
	.step-line { width: 48rpx; height: 2rpx; background: #e5e7eb; margin-right: 10rpx; }

	.append-tip { font-size: 24rpx; color: #b45309; background: #fffbeb; }
	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.picker-in { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.picker-in.readonly { color: #0d9488; font-weight: 600; }
	.picker-in.sm { height: 60rpx; line-height: 60rpx; font-size: 24rpx; padding: 0 16rpx; }
	.textarea { width: 100%; box-sizing: border-box; min-height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; color: #303133; }
	.textarea.big { min-height: 320rpx; }
	.textarea.sm { min-height: 80rpx; }
	.mt { margin-top: 12rpx; }
	.tip { font-size: 22rpx; color: #909399; line-height: 1.6; }

	.img-add { height: 120rpx; line-height: 120rpx; text-align: center; border-radius: 12rpx; border: 2rpx dashed #cbd5e1; color: #6366f1; font-size: 27rpx; background: #fafbfc; }
	.img-box { position: relative; width: 240rpx; height: 240rpx; }
	.img { width: 240rpx; height: 240rpx; border-radius: 12rpx; background: #f2f4f8; }
	.img-del { position: absolute; top: -14rpx; right: -14rpx; width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: #e11d48; color: #ffffff; font-size: 26rpx; }

	.sum-bar { display: flex; flex-direction: column; gap: 10rpx; }
	.sum-tx { font-size: 26rpx; color: #4b5563; }
	.hl { color: #6366f1; font-weight: 700; }
	.warn { font-size: 22rpx; color: #e6a23c; }

	.sec { margin-top: 20rpx; }
	.sec-head { display: flex; align-items: center; gap: 12rpx; }
	.sec-seq { width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 24rpx; font-weight: 700; flex-shrink: 0; }
	.sec-type { flex-shrink: 0; }
	.sec-title { flex: 1; min-width: 0; }
	.block { margin-top: 16rpx; }
	.block-label { display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; font-size: 24rpx; color: #0d9488; font-weight: 600; margin-bottom: 10rpx; }
	.block-tip { font-size: 21rpx; color: #909399; font-weight: 400; }

	.opt-row { display: flex; align-items: center; gap: 12rpx; margin-top: 10rpx; }
	.opt-key { width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: #eef2ff; color: #6366f1; font-size: 22rpx; font-weight: 700; flex-shrink: 0; }
	.opt-in { flex: 1; min-width: 0; }

	.q { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx dashed #eef0f4; }
	.q-head { display: flex; align-items: center; gap: 12rpx; }
	.q-seq { font-size: 24rpx; color: #1f2d52; font-weight: 700; flex-shrink: 0; }
	.q-type { flex-shrink: 0; }
	.score { width: 90rpx; flex-shrink: 0; text-align: center; padding: 0 8rpx; }
	.unit { font-size: 22rpx; color: #909399; flex-shrink: 0; }
	.q-opts { margin-top: 12rpx; }
	.ans-row { display: flex; align-items: center; gap: 12rpx; margin-top: 12rpx; }
	.ans-label { font-size: 24rpx; color: #16a34a; font-weight: 600; flex-shrink: 0; }
	.ans-in { flex: 1; min-width: 0; }
	.add-q { margin-top: 18rpx; }
	.append-btn { text-align: center; color: #6366f1; font-size: 27rpx; font-weight: 600; }

	.summary { font-size: 26rpx; color: #4b5563; line-height: 1.8; }
	.dt-row { display: flex; gap: 16rpx; align-items: center; }
	.picker-in.dt { flex: 1; text-align: center; }
	.clear-dt { font-size: 24rpx; color: #e11d48; flex-shrink: 0; }

	.mini-btn { font-size: 23rpx; color: #6b7280; padding: 6rpx 18rpx; border-radius: 999rpx; background: #f2f4f8; flex-shrink: 0; }
	.mini-btn-primary { color: #6366f1; background: #eef2ff; }
	.mini-btn-danger { color: #e11d48; background: #fdecef; }

	.bottom-bar { display: flex; gap: 20rpx; padding: 20rpx 24rpx 50rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
