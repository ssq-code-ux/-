<template>
	<view class="page">
		<view v-if="loading" class="card"><view class="muted">加载批改结果…</view></view>

		<template v-else-if="result">
			<!-- 得分条 -->
			<view class="card score-bar">
				<view class="sc-item"><text class="sc-num">{{ result.gotScore }}</text><text class="sc-unit">/ {{ result.fullScore }} 分</text></view>
				<view class="sc-side">
					<text class="sc-line">全对 {{ result.rightCount }} / {{ result.total }} 题</text>
					<text v-if="result.review" class="sc-line">百分制 {{ result.review.score }} 分</text>
				</view>
			</view>
			<view v-if="pendingCount" class="card warn-bar">⏳ 有 {{ pendingCount }} 道主观题 AI 未判定，请逐题确认对错与得分</view>

			<!-- 逐题复核 -->
			<view v-for="(g, gi) in groups" :key="'g' + gi" class="card sec">
				<view v-if="g.sectionType !== 'standalone'" class="sec-head">
					<text class="sec-title">{{ g.title || sectionTypeLabel(g.sectionType) }}</text>
					<text class="sec-badge">{{ sectionTypeLabel(g.sectionType) }}</text>
				</view>
				<view v-if="g.passage" class="passage-bar" @click="togglePassage(gi)">
					<text>📄 材料原文</text>
					<text class="passage-toggle">{{ passageOpen[gi] ? '收起 ▲' : '展开 ▼' }}</text>
				</view>
				<view v-if="g.passage && passageOpen[gi]" class="passage-text">{{ g.passage }}</view>
				<view v-if="g.wordBank && g.wordBank.length" class="bank">
					<text class="bank-label">备选词</text>
					<text v-for="(w, wi) in g.wordBank" :key="wi" class="bank-tag">{{ w }}</text>
				</view>

				<view v-for="it in g.items" :key="it.questionId" class="r-card" :class="it.correctBool ? 'r-right' : 'r-wrong'">
					<view class="r-head">
						<text class="r-seq">{{ it.seq }}</text>
						<text class="r-type">{{ questionTypeLabel(it.questionType) }}</text>
						<text v-if="it.teacherFixed === 1" class="r-fixed">已复核</text>
						<text class="r-flag">{{ it.correctBool ? '✔ 判对' : '✖ 判错' }}</text>
					</view>
					<view class="pre r-content">{{ it.content }}</view>
					<view class="r-row"><text class="r-label">学生答案</text><text :class="{ 'r-mine-wrong': !it.correctBool }">{{ it.studentAnswer || '（未作答）' }}</text></view>
					<view class="r-row"><text class="r-label">标准答案</text><text class="r-std">{{ it.standardAnswer }}</text></view>
					<view v-if="it.aiComment" class="r-row"><text class="r-label">AI 点评</text><text>{{ it.aiComment }}</text></view>
					<view v-if="it.analysis" class="r-row"><text class="r-label">解析</text><text>{{ it.analysis }}</text></view>

					<!-- 调分 -->
					<view class="fix-row">
						<text class="fix-label">调分</text>
						<input class="fix-score" type="number" v-model="it.gotScore" />
						<text class="fix-unit">/ {{ it.score }} 分</text>
						<switch class="fix-switch" :checked="it.correctBool" color="#16a34a" style="transform:scale(0.8)" @change="it.correctBool = $event.detail.value" />
						<text class="fix-switch-tx">{{ it.correctBool ? '判对' : '判错' }}</text>
					</view>
				</view>
			</view>

			<!-- 整体评语 -->
			<view class="card">
				<view class="card-title">💬 整体评语</view>
				<textarea class="comment-area" v-model="comment" placeholder="留空则自动生成复核评语" maxlength="500" />
			</view>

			<view class="bottom-bar">
				<view class="btn btn-primary" :class="{ 'btn-disabled': fixing }" @click="doFix">{{ fixing ? '保存中…' : '💾 保存复核' }}</view>
			</view>
		</template>

		<view v-else class="card"><empty-state text="学生还没提交这张答题卡" /></view>
	</view>
</template>

<script>
	import { getHomework, submits, answerSheetResult, fixAnswerSheet } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				id: null, loading: false, fixing: false,
				result: null, comment: '', passageOpen: {}
			};
		},
		computed: {
			/* 逐题结果按大题分组，材料从后端返回的 sections 里取 */
			groups() {
				const d = this.result;
				if (!d || !d.items) return [];
				const metaBySeq = {};
				(d.sections || []).forEach((s) => { metaBySeq[s.seq] = s; });
				const bySeq = {};
				const groups = [];
				d.items.forEach((it) => {
					const key = it.sectionSeq == null ? 0 : it.sectionSeq;
					if (!bySeq[key]) {
						const meta = metaBySeq[key] || {};
						bySeq[key] = {
							sectionType: it.sectionType || meta.sectionType || 'standalone',
							title: it.sectionTitle || meta.title || '',
							passage: meta.passage || '',
							wordBank: meta.wordBank || [],
							items: []
						};
						groups.push(bySeq[key]);
					}
					bySeq[key].items.push(it);
				});
				return groups;
			},
			/* correct 为 null 表示 AI 未判定的主观题 */
			pendingCount() {
				const d = this.result;
				if (!d || !d.items) return 0;
				return d.items.filter((it) => it.correct === null || it.correct === undefined).length;
			}
		},
		onLoad(opt) {
			if (!guardRole('teacher')) return;
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
			togglePassage(gi) {
				const open = Object.assign({}, this.passageOpen);
				open[gi] = !open[gi];
				this.passageOpen = open;
			},
			async load() {
				this.loading = true;
				try {
					const hw = await getHomework(this.id).catch(() => null);
					if (hw && hw.title) uni.setNavigationBarTitle({ title: hw.title });
					const subs = (await submits(this.id).catch(() => [])) || [];
					if (!subs.length) { this.result = null; return; }
					const r = await answerSheetResult(subs[0].id);
					// correctBool 给开关用；correct 可能是 null（AI 未判），默认判错待老师确认
					(r.items || []).forEach((it) => { it.correctBool = it.correct === 1; });
					this.result = r;
					this.comment = r.review ? (r.review.comment || '') : '';
				} catch (e) { this.result = null; } finally { this.loading = false; }
			},
			async doFix() {
				if (this.fixing || !this.result) return;
				this.fixing = true;
				try {
					await fixAnswerSheet({
						submitId: this.result.submitId,
						comment: this.comment,
						items: (this.result.items || []).map((it) => ({
							questionId: it.questionId,
							correct: it.correctBool ? 1 : 0,
							gotScore: Number(it.gotScore) || 0
						}))
					});
					uni.showToast({ title: '复核已保存', icon: 'success' });
					setTimeout(() => this.load(), 600);
				} catch (e) { /* 已提示 */ } finally { this.fixing = false; }
			}
		}
	};
</script>

<style scoped>
	.score-bar { display: flex; align-items: center; gap: 28rpx; }
	.sc-item { display: flex; align-items: baseline; gap: 8rpx; }
	.sc-num { font-size: 56rpx; font-weight: 800; color: #6366f1; line-height: 1; }
	.sc-unit { font-size: 24rpx; color: #909399; }
	.sc-side { flex: 1; min-width: 0; }
	.sc-line { display: block; font-size: 25rpx; color: #4b5563; line-height: 1.7; }
	.warn-bar { font-size: 24rpx; color: #b45309; background: #fffbeb; }

	.sec { margin-top: 20rpx; }
	.sec-head { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
	.sec-title { font-size: 29rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.sec-badge { flex-shrink: 0; font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.passage-bar { margin-top: 14rpx; display: flex; align-items: center; justify-content: space-between; padding: 16rpx 20rpx; border-radius: 12rpx; background: #f8fafc; font-size: 25rpx; color: #0d9488; font-weight: 600; }
	.passage-toggle { font-size: 22rpx; color: #909399; font-weight: 400; }
	.passage-text { margin-top: 12rpx; font-size: 26rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.bank { margin-top: 14rpx; display: flex; align-items: center; gap: 10rpx; flex-wrap: wrap; }
	.bank-label { font-size: 22rpx; color: #909399; }
	.bank-tag { font-size: 23rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 6rpx 18rpx; }

	.r-card { margin-top: 20rpx; padding: 20rpx; border-radius: 14rpx; background: #f8fafc; border-left: 6rpx solid #cbd5e1; }
	.r-right { border-left-color: #22c55e; background: #f0fdf4; }
	.r-wrong { border-left-color: #ef4444; background: #fef2f2; }
	.r-head { display: flex; align-items: center; gap: 12rpx; }
	.r-seq { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 22rpx; font-weight: 700; }
	.r-type { font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.r-fixed { font-size: 21rpx; color: #b45309; background: #fef3c7; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.r-flag { margin-left: auto; font-size: 22rpx; color: #4b5563; font-weight: 700; }
	.pre { font-size: 26rpx; color: #303133; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
	.r-content { margin-top: 12rpx; }
	.r-row { margin-top: 10rpx; font-size: 25rpx; color: #4b5563; line-height: 1.7; }
	.r-label { color: #909399; margin-right: 10rpx; }
	.r-mine-wrong { color: #e11d48; }
	.r-std { color: #16a34a; font-weight: 600; }

	.fix-row { margin-top: 16rpx; display: flex; align-items: center; gap: 12rpx; padding-top: 16rpx; border-top: 1rpx dashed #e5e7eb; }
	.fix-label { font-size: 24rpx; color: #4b5563; flex-shrink: 0; }
	.fix-score { width: 100rpx; height: 64rpx; line-height: 64rpx; text-align: center; background: #ffffff; border: 2rpx solid #e5e7eb; border-radius: 10rpx; font-size: 26rpx; color: #303133; flex-shrink: 0; }
	.fix-unit { font-size: 22rpx; color: #909399; flex-shrink: 0; }
	.fix-switch { margin-left: auto; flex-shrink: 0; }
	.fix-switch-tx { font-size: 24rpx; color: #4b5563; flex-shrink: 0; width: 60rpx; }

	.card-title { font-size: 27rpx; font-weight: 700; color: #1f2d52; margin-bottom: 14rpx; }
	.comment-area { width: 100%; box-sizing: border-box; min-height: 130rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; color: #303133; }

	.bottom-bar { padding: 24rpx 24rpx 50rpx; }
	.bottom-bar .btn { width: 100%; text-align: center; }
</style>
