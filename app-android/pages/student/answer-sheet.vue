<template>
	<view class="page">
		<view v-if="loading" class="card"><view class="muted">加载试卷中…</view></view>

		<template v-else-if="sections.length">
			<!-- 卷头信息条 -->
			<view class="bar">
				<text class="bar-title">{{ title }}</text>
				<text class="bar-meta">共 {{ questions.length }} 题 · 满分 {{ fullScore }} 分 · 已答 {{ answeredCount }}</text>
			</view>
			<view v-if="content" class="card tip">{{ content }}</view>

			<!-- 大题 -->
			<view v-for="(sec, si) in sections" :key="si" class="card sec">
				<view v-if="sec.sectionType !== 'standalone'" class="sec-head">
					<text class="sec-title">{{ sec.title || sectionTypeLabel(sec.sectionType) }}</text>
					<text class="sec-badge">{{ sectionTypeLabel(sec.sectionType) }}</text>
				</view>
				<view v-if="sec.instruction" class="sec-inst">{{ sec.instruction }}</view>

				<!-- 材料原文（可折叠） -->
				<view v-if="sec.passage" class="passage-bar" @click="togglePassage(si)">
					<text>📄 材料原文</text>
					<text class="passage-toggle">{{ passageOpen[si] ? '收起 ▲' : '展开 ▼' }}</text>
				</view>
				<view v-if="sec.passage && passageOpen[si]" class="passage-text">{{ sec.passage }}</view>

				<!-- 备选词（选词填空） -->
				<view v-if="sec.wordBank && sec.wordBank.length" class="bank">
					<text class="bank-label">备选词</text>
					<text v-for="(w, wi) in sec.wordBank" :key="wi" class="bank-tag">{{ w }}</text>
				</view>

				<!-- 大题共用选项框（七选五） -->
				<view v-if="sec.sharedOptions && sec.sharedOptions.length" class="bank bank-col">
					<text class="bank-label">选项</text>
					<view v-for="o in sec.sharedOptions" :key="o.key" class="shared-opt">
						<text class="so-key">{{ o.key }}</text>
						<text class="so-text">{{ o.text }}</text>
					</view>
				</view>

				<!-- 小题 -->
				<view v-for="q in (sec.questions || [])" :key="q.id" class="q">
					<view class="q-head">
						<text class="q-seq">{{ q.seq }}</text>
						<text class="q-type">{{ questionTypeLabel(q.questionType) }}</text>
						<text class="q-score">{{ q.score }} 分</text>
					</view>
					<view class="q-content">{{ q.content }}</view>

					<!-- 单选 / 判断 -->
					<view v-if="q.questionType === 'single' || q.questionType === 'judge'" class="opts">
						<view v-for="o in optionsOf(sec, q)" :key="o.key" class="opt" :class="{ on: answers[q.id] === o.key }" @click="pickSingle(q.id, o.key)">
							<text class="opt-radio"></text>
							<text class="opt-text">{{ o.key }}. {{ o.text }}</text>
						</view>
					</view>

					<!-- 多选 -->
					<view v-else-if="q.questionType === 'multi'" class="opts">
						<view v-for="o in optionsOf(sec, q)" :key="o.key" class="opt" :class="{ on: (multi[q.id] || []).indexOf(o.key) >= 0 }" @click="toggleMulti(q.id, o.key)">
							<text class="opt-box"></text>
							<text class="opt-text">{{ o.key }}. {{ o.text }}</text>
						</view>
					</view>

					<!-- 填空 / 简答 -->
					<view v-else class="fill">
						<input v-if="q.questionType === 'blank' && isFillSection(sec.sectionType)" class="fill-input" v-model="answers[q.id]" placeholder="填写答案" />
						<textarea v-else class="fill-area" v-model="answers[q.id]" placeholder="填写答案" maxlength="2000" auto-height />
					</view>
				</view>
			</view>

			<view class="bottom-bar">
				<view class="btn btn-primary" :class="{ 'btn-disabled': submitting }" @click="doSubmit">{{ submitting ? '交卷中…' : '✍️ 交卷并自动判分' }}</view>
			</view>
		</template>

		<view v-else class="card"><empty-state text="这张答题卡还没有题目" /></view>
	</view>
</template>

<script>
	import { getHomework, answerSheetPaper, submitAnswerSheet } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				id: null, title: '答题卡', content: '',
				sections: [], answers: {}, multi: {}, passageOpen: {},
				loading: false, submitting: false
			};
		},
		computed: {
			/* 拉平后的小题：统分、统已答、交卷都不关心分层 */
			questions() {
				const out = [];
				(this.sections || []).forEach((s) => (s.questions || []).forEach((q) => out.push(q)));
				return out;
			},
			fullScore() { return this.questions.reduce((sum, q) => sum + (Number(q.score) || 0), 0); },
			answeredCount() {
				return this.questions.filter((q) => {
					if (q.questionType === 'multi') return (this.multi[q.id] || []).length > 0;
					return !!(this.answers[q.id] && String(this.answers[q.id]).trim());
				}).length;
			}
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			this.id = opt && opt.id;
			this.load();
		},
		methods: {
			questionTypeLabel(t) {
				const m = { single: '单选', multi: '多选', judge: '判断', blank: '填空', short: '简答' };
				return m[t] || '题目';
			},
			sectionTypeLabel(t) {
				const m = {
					cloze: '完形填空', reading: '阅读理解', word_bank: '选词填空',
					blank_fill: '盲填', dialogue: '补全对话', solution: '解答题',
					writing: '书面表达', standalone: '单句题'
				};
				return m[t] || '题目';
			},
			/* 文章里挖空的题型，输入框横着摆更紧凑 */
			isFillSection(t) { return t === 'cloze' || t === 'word_bank' || t === 'blank_fill'; },
			/* 小题自带选项就用自己的，否则回退到大题共用选项框（七选五） */
			optionsOf(sec, q) {
				if (q.options && q.options.length) return q.options;
				return sec.sharedOptions || [];
			},
			togglePassage(si) {
				const open = Object.assign({}, this.passageOpen);
				open[si] = !open[si];
				this.passageOpen = open;
			},
			pickSingle(qid, key) { this.answers[qid] = key; },
			toggleMulti(qid, key) {
				const arr = (this.multi[qid] || []).slice();
				const i = arr.indexOf(key);
				if (i >= 0) arr.splice(i, 1);
				else arr.push(key);
				const multi = Object.assign({}, this.multi);
				multi[qid] = arr;
				this.multi = multi;
			},
			async load() {
				this.loading = true;
				try {
					const hw = await getHomework(this.id).catch(() => null);
					if (hw) {
						this.title = hw.title || '答题卡';
						this.content = hw.content || '';
						uni.setNavigationBarTitle({ title: this.title });
					}
					const list = (await answerSheetPaper(this.id)) || [];
					this.sections = list;
					// 预初始化每个作答键，保证 v-model 响应式
					const answers = {};
					const multi = {};
					const open = {};
					list.forEach((sec, si) => {
						open[si] = true;
						(sec.questions || []).forEach((q) => {
							if (q.questionType === 'multi') multi[q.id] = [];
							else answers[q.id] = '';
						});
					});
					this.answers = answers;
					this.multi = multi;
					this.passageOpen = open;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			doSubmit() {
				if (this.submitting) return;
				const answers = this.questions.map((q) => ({
					questionId: q.id,
					// 多选拼成 "AC"，后端判分时忽略选项顺序
					answer: q.questionType === 'multi' ? (this.multi[q.id] || []).slice().sort().join('') : (this.answers[q.id] || '')
				}));
				const unanswered = answers.filter((a) => !String(a.answer).trim()).length;
				const go = async () => {
					this.submitting = true;
					try {
						const r = await submitAnswerSheet({ homeworkId: this.id, answers });
						uni.showToast({ title: `交卷成功，得分 ${r.gotScore}/${r.fullScore}`, icon: 'none' });
						// 判分是同步完成的，直接跳结果页看逐题对错与解析
						setTimeout(() => uni.redirectTo({ url: `/pages/student/homework-review?id=${this.id}` }), 900);
					} catch (e) { /* 已提示 */ } finally { this.submitting = false; }
				};
				if (unanswered) {
					uni.showModal({
						title: '提示', content: `还有 ${unanswered} 题没作答，未作答的题会计 0 分，确定交卷？`,
						confirmText: '确定交卷', cancelText: '再检查下',
						success: (res) => { if (res.confirm) go(); }
					});
				} else {
					go();
				}
			}
		}
	};
</script>

<style scoped>
	.bar { margin: 20rpx 24rpx 0; padding: 22rpx 24rpx; border-radius: 16rpx; background: linear-gradient(120deg, #6366f1, #38bdf8); color: #ffffff; }
	.bar-title { display: block; font-size: 30rpx; font-weight: 700; }
	.bar-meta { display: block; margin-top: 8rpx; font-size: 23rpx; opacity: 0.92; }
	.tip { font-size: 25rpx; color: #4b5563; line-height: 1.7; white-space: pre-wrap; }

	.sec { margin: 20rpx 24rpx 0; }
	.sec-head { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
	.sec-title { font-size: 29rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.sec-badge { flex-shrink: 0; font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.sec-inst { margin-top: 10rpx; font-size: 24rpx; color: #6b7280; line-height: 1.6; }

	.passage-bar { margin-top: 14rpx; display: flex; align-items: center; justify-content: space-between; padding: 16rpx 20rpx; border-radius: 12rpx; background: #f8fafc; font-size: 25rpx; color: #0d9488; font-weight: 600; }
	.passage-toggle { font-size: 22rpx; color: #909399; font-weight: 400; }
	.passage-text { margin-top: 12rpx; font-size: 26rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }

	.bank { margin-top: 14rpx; display: flex; align-items: center; gap: 10rpx; flex-wrap: wrap; }
	.bank-col { flex-direction: column; align-items: stretch; }
	.bank-label { font-size: 22rpx; color: #909399; }
	.bank-tag { font-size: 23rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 6rpx 18rpx; }
	.shared-opt { display: flex; gap: 12rpx; align-items: flex-start; padding: 10rpx 14rpx; border-radius: 10rpx; background: #f8fafc; }
	.so-key { flex-shrink: 0; width: 36rpx; height: 36rpx; line-height: 36rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 21rpx; font-weight: 700; }
	.so-text { font-size: 25rpx; color: #303133; line-height: 1.6; }

	.q { margin-top: 26rpx; padding-top: 22rpx; border-top: 1rpx dashed #eef0f4; }
	.q-head { display: flex; align-items: center; gap: 12rpx; }
	.q-seq { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 22rpx; font-weight: 700; }
	.q-type { font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.q-score { margin-left: auto; font-size: 21rpx; color: #909399; }
	.q-content { margin-top: 12rpx; font-size: 27rpx; color: #303133; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }

	.opts { display: flex; flex-direction: column; gap: 14rpx; margin-top: 14rpx; }
	.opt { display: flex; align-items: center; gap: 14rpx; padding: 20rpx 20rpx; border-radius: 14rpx; border: 2rpx solid #eef0f4; background: #f8fafc; }
	.opt.on { border-color: #6366f1; background: #eef2ff; }
	.opt-radio { width: 30rpx; height: 30rpx; border-radius: 50%; border: 3rpx solid #cbd5e1; flex-shrink: 0; }
	.opt.on .opt-radio { border-color: #6366f1; background: radial-gradient(circle, #6366f1 40%, #ffffff 45%); }
	.opt-box { width: 30rpx; height: 30rpx; border-radius: 8rpx; border: 3rpx solid #cbd5e1; flex-shrink: 0; }
	.opt.on .opt-box { border-color: #6366f1; background: #6366f1; }
	.opt-text { font-size: 26rpx; color: #303133; line-height: 1.6; }

	.fill { margin-top: 14rpx; }
	.fill-input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.fill-area { width: 100%; box-sizing: border-box; min-height: 120rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; color: #303133; }

	.bottom-bar { padding: 24rpx 24rpx 50rpx; }
	.bottom-bar .btn { width: 100%; text-align: center; }
</style>
