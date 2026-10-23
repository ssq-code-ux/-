<template>
	<view class="page">
		<!-- ══ 数量选择 ══ -->
		<view v-if="phase === 'countSelect'" class="wrap">
			<view class="card center-card">
				<view class="big-emoji">🔄</view>
				<view class="center-title">错题复习</view>
				<view class="center-sub">复习多少个错题？</view>
				<view class="hint">当前有 {{ unreviewedCount }} 个待复习错题</view>
				<view class="count-grid">
					<text v-for="c in countOptions" :key="c" class="count-chip" :class="{ on: count === c }" @click="count = c">{{ c }}</text>
				</view>
				<view class="btn btn-primary center-btn" :class="{ 'btn-disabled': loading }" @click="begin">{{ loading ? '加载中…' : '🚀 开始复习' }}</view>
			</view>
		</view>

		<!-- ══ 复习中 ══ -->
		<view v-else-if="phase === 'reviewing'" class="wrap">
			<view class="phase-head">
				<text class="phase-badge">错题复习</text>
				<text class="phase-prog">{{ currentIndex + 1 }} / {{ words.length }}</text>
			</view>
			<view class="prog-track"><view class="prog-fill" :style="{ width: (currentIndex / words.length * 100) + '%' }"></view></view>

			<view v-if="current" class="card q-card">
				<view class="q-word-box">
					<text class="q-word">{{ current.word }}</text>
					<text class="speak-btn" @click="speak(current.word)">🔊</text>
					<text v-if="current.partOfSpeech" class="q-pos">{{ current.partOfSpeech }}</text>
				</view>
				<view class="q-ask">请选择以上单词的中文意思：</view>
				<view class="opts">
					<view v-for="opt in current.options" :key="opt" class="opt"
						:class="{ on: answer === opt, correct: (feedback && (feedback.showDetails || feedback.finalAttempt) && opt === current.translation) }"
						@click="pickAnswer(opt)">
						<text class="opt-radio"></text><text class="opt-text">{{ opt }}</text>
					</view>
				</view>

				<view v-if="feedback" class="fb" :class="feedback.correct ? 'fb-ok' : 'fb-bad'">
					<text class="fb-msg">{{ feedback.message }}</text>
				</view>

				<view v-if="feedback && (feedback.showDetails || feedback.finalAttempt)" class="detail-panel">
					<view class="dp-title">📋 全部选项解析</view>
					<view v-for="(d, i) in current.optionDetails" :key="i" class="dp-item" :class="{ 'dp-correct': d.optionText === current.translation }">
						<view class="dp-head">
							<text class="dp-num">{{ ['A', 'B', 'C', 'D'][i] }}</text>
							<text class="dp-text">{{ d.optionText }}</text>
							<text class="dp-arrow">←</text>
							<text class="dp-word">{{ d.sourceWord }}</text>
							<text v-if="d.partOfSpeech && d.partOfSpeech !== '—'" class="dp-pos">{{ d.partOfSpeech }}</text>
						</view>
						<view v-if="d.extensions" class="dp-ext">📚 {{ splitExt(d.extensions).join('、') }}</view>
					</view>
				</view>
			</view>

			<view class="foot">
				<view v-if="!feedback || (!feedback.showDetails && !feedback.finalAttempt)" class="btn btn-primary foot-btn" :class="{ 'btn-disabled': !answer }" @click="confirm">确认</view>
				<view v-else class="btn btn-primary foot-btn" @click="next">{{ currentIndex + 1 >= words.length ? '查看结果 →' : '下一题 →' }}</view>
			</view>
		</view>

		<!-- ══ 完成 ══ -->
		<view v-else class="wrap">
			<view class="card center-card">
				<view class="ring" :class="ringClass">
					<text class="ring-num">{{ correct }}</text>
					<text class="ring-total">/ {{ words.length }}</text>
				</view>
				<view class="score-msg">{{ correct === words.length ? '🎉 全部正确！' : '💪 继续加油，错题已重新记录！' }}</view>
			</view>

			<view class="card" v-if="errors.length">
				<view class="card-title">📋 仍需要复习的单词</view>
				<view v-for="(e, i) in errors" :key="'re' + i" class="rpt-item">
					<view class="rpt-head"><text class="rpt-word">{{ i + 1 }}. {{ e.word }}</text><text class="rpt-badge">❌</text></view>
					<view class="rpt-line">正确答案：<text class="rpt-correct">{{ e.correctAnswer }}</text></view>
				</view>
			</view>

			<view class="foot">
				<view class="btn btn-plain foot-btn" @click="finish">完成</view>
				<view class="btn btn-primary foot-btn" @click="reset">再来一次 🔄</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { reviewErrors, errorStats, recordQuizError } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				phase: 'countSelect', count: 10, unreviewedCount: 0, loading: false,
				words: [], currentIndex: 0, answer: '', feedback: null, attempt: 0,
				correct: 0, errors: []
			};
		},
		computed: {
			current() { return this.words[this.currentIndex] || null; },
			countOptions() {
				const max = Math.max(5, this.unreviewedCount || 10);
				const opts = [];
				for (let c = 5; c <= max; c += 5) opts.push(c);
				if (!opts.length) opts.push(5);
				return opts;
			},
			ringClass() { const r = this.words.length ? this.correct / this.words.length : 0; return r >= 0.8 ? 'ring-high' : (r >= 0.5 ? 'ring-mid' : 'ring-low'); }
		},
		async onLoad() {
			if (!guardRole('student')) return;
			try {
				const s = await errorStats();
				this.unreviewedCount = (s && s.errorCount) || 0;
			} catch (e) { /* 忽略 */ }
			this.count = Math.min(10, this.unreviewedCount || 10);
		},
		onUnload() { stopSpeak(); },
		methods: {
			splitExt(s) { return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []; },
			speak(w) { speakWord(w); },
			async begin() {
				if (this.loading) return;
				this.loading = true;
				try {
					const data = await reviewErrors(this.count);
					this.words = data.words || [];
					if (!this.words.length) { uni.showToast({ title: '暂无错题需要复习 🎉', icon: 'none' }); setTimeout(() => uni.navigateBack(), 900); return; }
					this.phase = 'reviewing'; this.currentIndex = 0; this.answer = ''; this.feedback = null; this.attempt = 0; this.correct = 0; this.errors = [];
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			pickAnswer(opt) { if (!this.feedback || (!this.feedback.showDetails && !this.feedback.finalAttempt)) this.answer = opt; },
			confirm() {
				if (!this.answer) { uni.showToast({ title: '请先选择一个答案', icon: 'none' }); return; }
				const word = this.current;
				const isCorrect = this.answer === word.translation;
				this.attempt++;
				if (isCorrect) {
					this.correct++;
					this.feedback = { correct: true, message: '✅ 回答正确！', showDetails: true };
				} else {
					this.errors.push({ wordId: word.id, word: word.word, wrongAnswer: this.answer, correctAnswer: word.translation });
					recordQuizError(word.id, this.answer, 'meaning').catch(() => {});
					if (this.attempt >= 3) {
						this.feedback = { correct: false, message: '❌ 很遗憾，正确答案是：' + word.translation, showDetails: true, finalAttempt: true };
					} else {
						const remaining = 3 - this.attempt;
						this.feedback = { correct: false, message: '❌ 选错了！请重新选择（还剩 ' + remaining + ' 次机会）', showDetails: false };
						this.answer = '';
						setTimeout(() => { this.feedback = null; }, 2000);
					}
				}
			},
			next() {
				this.feedback = null; this.answer = ''; this.attempt = 0;
				if (this.currentIndex + 1 >= this.words.length) this.phase = 'done';
				else this.currentIndex++;
			},
			reset() { this.phase = 'countSelect'; this.count = Math.min(10, this.unreviewedCount || 10); },
			finish() { uni.navigateBack(); }
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx 24rpx 40rpx; }
	.center-card { padding: 60rpx 40rpx; text-align: center; }
	.big-emoji { font-size: 96rpx; }
	.center-title { margin-top: 12rpx; font-size: 38rpx; font-weight: 700; color: #1f2d52; }
	.center-sub { margin-top: 10rpx; font-size: 26rpx; color: #909399; }
	.hint { margin-top: 8rpx; font-size: 23rpx; color: #6366f1; }
	.count-grid { display: flex; flex-wrap: wrap; gap: 16rpx; justify-content: center; margin: 32rpx 0; }
	.count-chip { width: 120rpx; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: 16rpx; background: #f2f4f8; color: #4b5563; font-size: 34rpx; font-weight: 700; border: 2rpx solid transparent; }
	.count-chip.on { background: #eef2ff; color: #6366f1; border-color: #6366f1; }
	.center-btn { width: 100%; text-align: center; }

	.phase-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.phase-badge { font-size: 24rpx; font-weight: 700; border-radius: 999rpx; padding: 8rpx 22rpx; background: #eef2ff; color: #6366f1; }
	.phase-prog { font-size: 24rpx; color: #909399; }
	.prog-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; margin-bottom: 20rpx; }
	.prog-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }

	.q-card { padding: 32rpx 28rpx; }
	.q-word-box { text-align: center; }
	.q-word { font-size: 64rpx; font-weight: 800; color: #1f2d52; }
	.speak-btn { font-size: 34rpx; padding: 2rpx 10rpx; }
	.q-pos { display: inline-block; margin-top: 10rpx; font-size: 24rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 4rpx 18rpx; }
	.q-ask { margin: 28rpx 0 16rpx; font-size: 27rpx; color: #4b5563; }
	.opts { display: flex; flex-direction: column; gap: 16rpx; }
	.opt { display: flex; align-items: center; gap: 16rpx; padding: 24rpx 22rpx; border-radius: 16rpx; border: 2rpx solid #eef0f4; background: #f8fafc; }
	.opt.on { border-color: #6366f1; background: #eef2ff; }
	.opt.correct { border-color: #22c55e; background: #dcfce7; }
	.opt-radio { width: 32rpx; height: 32rpx; border-radius: 50%; border: 3rpx solid #cbd5e1; flex-shrink: 0; }
	.opt.on .opt-radio { border-color: #6366f1; background: radial-gradient(circle, #6366f1 40%, #ffffff 45%); }
	.opt.correct .opt-radio { border-color: #22c55e; background: radial-gradient(circle, #22c55e 40%, #ffffff 45%); }
	.opt-text { font-size: 28rpx; color: #303133; }

	.fb { margin-top: 24rpx; padding: 22rpx 24rpx; border-radius: 16rpx; }
	.fb-ok { background: #dcfce7; }
	.fb-bad { background: #fee2e2; }
	.fb-msg { font-size: 28rpx; font-weight: 600; }
	.fb-ok .fb-msg { color: #166534; }
	.fb-bad .fb-msg { color: #991b1b; }

	.detail-panel { margin-top: 24rpx; padding: 22rpx; border-radius: 16rpx; background: #f8fafc; }
	.dp-title { font-size: 26rpx; font-weight: 700; color: #1f2d52; margin-bottom: 14rpx; }
	.dp-item { padding: 14rpx 0; border-bottom: 1rpx dashed #e5e7eb; }
	.dp-item:last-child { border-bottom: none; }
	.dp-correct .dp-text { color: #16a34a; font-weight: 700; }
	.dp-head { display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; }
	.dp-num { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 22rpx; }
	.dp-text { font-size: 26rpx; color: #303133; }
	.dp-arrow { font-size: 24rpx; color: #b0b4bb; }
	.dp-word { font-size: 26rpx; color: #6366f1; font-weight: 600; }
	.dp-pos { font-size: 20rpx; color: #909399; background: #eef0f4; border-radius: 6rpx; padding: 2rpx 10rpx; }
	.dp-ext { margin-top: 6rpx; font-size: 22rpx; color: #0d9488; }

	.foot { display: flex; gap: 20rpx; margin-top: 28rpx; }
	.foot-btn { flex: 1; text-align: center; }

	.ring { width: 200rpx; height: 200rpx; border-radius: 50%; margin: 0 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; }
	.ring-high { background: radial-gradient(circle, #ffffff 58%, #dcfce7 59%); border: 10rpx solid #22c55e; }
	.ring-mid { background: radial-gradient(circle, #ffffff 58%, #fef9c3 59%); border: 10rpx solid #eab308; }
	.ring-low { background: radial-gradient(circle, #ffffff 58%, #fee2e2 59%); border: 10rpx solid #ef4444; }
	.ring-num { font-size: 68rpx; font-weight: 800; color: #1f2d52; line-height: 1; }
	.ring-total { font-size: 26rpx; color: #909399; margin-top: 6rpx; }
	.score-msg { margin-top: 24rpx; font-size: 28rpx; font-weight: 600; color: #4b5563; }

	.rpt-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f2f6; }
	.rpt-item:last-child { border-bottom: none; }
	.rpt-head { display: flex; align-items: center; justify-content: space-between; }
	.rpt-word { font-size: 28rpx; font-weight: 700; color: #1f2d52; }
	.rpt-badge { font-size: 22rpx; }
	.rpt-line { margin-top: 6rpx; font-size: 24rpx; color: #6b7280; }
	.rpt-correct { color: #16a34a; font-weight: 600; }
</style>
