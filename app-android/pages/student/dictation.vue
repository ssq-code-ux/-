<template>
	<view class="page">
		<!-- ══ 范围与数量选择 ══ -->
		<view v-if="phase === 'setup'" class="wrap">
			<view class="card center-card">
				<view class="big-emoji">🎧</view>
				<view class="center-title">单词听写</view>
				<view class="center-sub">听发音写单词，每词一次机会</view>
				<view class="scope-row">
					<view class="scope-item" :class="{ on: scope === 'all' }" @click="scope = 'all'">
						<text class="scope-name">全部单词</text>
						<text class="scope-desc">从整个词库随机抽取</text>
					</view>
					<view class="scope-item" :class="{ on: scope === 'errors' }" @click="scope = 'errors'">
						<text class="scope-name">错题本</text>
						<text class="scope-desc">只听写错过的单词</text>
					</view>
				</view>
				<view class="count-grid">
					<text v-for="c in [5, 10, 15, 20]" :key="c" class="count-chip" :class="{ on: count === c }" @click="count = c">{{ c }}</text>
				</view>
				<view class="btn btn-primary center-btn" :class="{ 'btn-disabled': loading }" @click="start">{{ loading ? '取词中…' : '🎧 开始听写' }}</view>
			</view>
		</view>

		<!-- ══ 听音写词 ══ -->
		<view v-else-if="phase === 'listen'" class="wrap">
			<view class="phase-head">
				<text class="phase-badge">🎧 听音写词</text>
				<text class="phase-prog">{{ index + 1 }} / {{ words.length }}</text>
			</view>
			<view class="prog-track"><view class="prog-fill" :style="{ width: (index / words.length * 100) + '%' }"></view></view>

			<view class="card q-card">
				<view class="play-btn" @click="replay">
					<text class="play-icon">🔊</text>
					<text class="play-text">点击播放发音</text>
				</view>
				<input class="spell-input" v-model="input" placeholder="输入你听到的单词…" :disabled="!!feedback" @confirm="confirmAnswer" />
				<view class="hint-line" v-if="!feedback && !hintVisible">
					<text class="hint-btn" @click="hintVisible = true">💡 看中文提示</text>
				</view>
				<view v-if="hintVisible" class="hint-text">提示：{{ current.translation }}</view>

				<view v-if="feedback" class="fb" :class="feedback.correct ? 'fb-ok' : 'fb-bad'">
					<text class="fb-msg">{{ feedback.message }}</text>
				</view>
			</view>

			<view class="foot">
				<view v-if="!feedback" class="btn btn-primary foot-btn" :class="{ 'btn-disabled': !input.trim() }" @click="confirmAnswer">确认</view>
				<view v-else class="btn btn-primary foot-btn" @click="next">{{ index + 1 >= words.length ? '查看报告 →' : '下一个 →' }}</view>
			</view>
		</view>

		<!-- ══ 听写报告 ══ -->
		<view v-else class="wrap">
			<view class="card center-card">
				<view class="big-emoji">{{ correct === words.length ? '🏆' : (correct >= words.length / 2 ? '👍' : '💪') }}</view>
				<view class="center-title">听写完成</view>
				<view class="done-stats">
					<text class="ds-num">{{ correct }}</text><text class="ds-sep">/</text><text class="ds-total">{{ words.length }}</text>
				</view>
				<view class="center-sub">{{ correct === words.length ? '全部听写正确，太棒了！' : (correct >= words.length / 2 ? '不错，继续加油！' : '需要多听多练哦~') }}</view>
			</view>

			<view class="card" v-if="errors.length">
				<view class="card-title">❌ 本轮听错的单词</view>
				<view v-for="(e, i) in errors" :key="'d' + i" class="rpt-item">
					<view class="rpt-head"><text class="rpt-word">{{ i + 1 }}. {{ e.word }}</text><text class="rpt-trans">{{ e.translation }}</text></view>
					<view class="rpt-line">你写的：<text class="rpt-wrong">{{ e.wrongAnswer }}</text></view>
				</view>
			</view>

			<view class="foot">
				<view class="btn btn-plain foot-btn" @click="finish">完成</view>
				<view v-if="errors.length" class="btn btn-primary foot-btn" @click="retryErrors">再听一轮 🎧</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { startQuiz, reviewErrors, checkSpelling } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				phase: 'setup', scope: 'all', count: 10, loading: false,
				words: [], index: 0, input: '', feedback: null, hintVisible: false,
				correct: 0, errors: []
			};
		},
		computed: {
			current() { return this.words[this.index] || {}; }
		},
		onLoad() { guardRole('student'); },
		onUnload() { stopSpeak(); },
		methods: {
			/* 开始听写：按范围取词（全词库随机 / 错题本），自动朗读第一个单词 */
			async start() {
				if (this.loading) return;
				this.loading = true;
				try {
					const data = this.scope === 'errors' ? await reviewErrors(this.count) : await startQuiz(this.count);
					const words = (data && data.words) || [];
					if (!words.length) {
						uni.showToast({ title: this.scope === 'errors' ? '暂无错题可听写' : '词汇不足，请联系老师添加', icon: 'none' });
						return;
					}
					this.words = words;
					this.count = words.length;
					this.phase = 'listen';
					this.index = 0;
					this.input = '';
					this.feedback = null;
					this.hintVisible = false;
					this.correct = 0;
					this.errors = [];
					speakWord(words[0].word);
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			replay() { const w = this.current; if (w && w.word) speakWord(w.word); },
			/* 提交听写答案：每词一次机会，拼错由后端自动记入错题库 */
			async confirmAnswer() {
				const input = String(this.input || '').trim();
				if (!input) { uni.showToast({ title: '请输入你听到的单词', icon: 'none' }); return; }
				if (this.feedback) return;
				const word = this.current;
				try {
					const result = await checkSpelling(word.id, input);
					if (result.correct) {
						this.correct++;
						this.feedback = { correct: true, message: '✅ 拼写正确！' };
					} else {
						this.feedback = { correct: false, message: '❌ 正确拼写：' + result.correctAnswer };
						this.errors.push({ wordId: word.id, word: result.correctAnswer, translation: word.translation, wrongAnswer: input });
					}
				} catch (e) { /* 已提示 */ }
			},
			/* 进入下一个单词（或完成看报告），自动朗读 */
			next() {
				this.feedback = null;
				this.input = '';
				this.hintVisible = false;
				if (this.index + 1 >= this.words.length) {
					this.phase = 'report';
					stopSpeak();
				} else {
					this.index++;
					this.replay();
				}
			},
			/* 报告页：把听错的单词再来一轮 */
			retryErrors() {
				if (!this.errors.length) return;
				this.words = this.errors.map((e) => ({ id: e.wordId, word: e.word, translation: e.translation }));
				this.phase = 'listen';
				this.index = 0;
				this.input = '';
				this.feedback = null;
				this.hintVisible = false;
				this.correct = 0;
				this.errors = [];
				this.replay();
			},
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
	.scope-row { display: flex; gap: 20rpx; margin-top: 36rpx; }
	.scope-item { flex: 1; padding: 24rpx 16rpx; border-radius: 16rpx; border: 2rpx solid #eef0f4; background: #f8fafc; }
	.scope-item.on { border-color: #6366f1; background: #eef2ff; }
	.scope-name { display: block; font-size: 27rpx; font-weight: 600; color: #1f2d52; }
	.scope-desc { display: block; margin-top: 8rpx; font-size: 21rpx; color: #909399; }
	.count-grid { display: flex; flex-wrap: wrap; gap: 16rpx; justify-content: center; margin: 32rpx 0; }
	.count-chip { width: 110rpx; height: 84rpx; line-height: 84rpx; text-align: center; border-radius: 16rpx; background: #f2f4f8; color: #4b5563; font-size: 32rpx; font-weight: 700; border: 2rpx solid transparent; }
	.count-chip.on { background: #eef2ff; color: #6366f1; border-color: #6366f1; }
	.center-btn { width: 100%; text-align: center; }

	.phase-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.phase-badge { font-size: 24rpx; font-weight: 700; border-radius: 999rpx; padding: 8rpx 22rpx; background: #e6fffa; color: #0d9488; }
	.phase-prog { font-size: 24rpx; color: #909399; }
	.prog-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; margin-bottom: 20rpx; }
	.prog-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #0d9488, #14b8a6); }

	.q-card { padding: 40rpx 28rpx; text-align: center; }
	.play-btn { width: 260rpx; height: 260rpx; margin: 0 auto; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #38bdf8); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10rpx; box-shadow: 0 12rpx 40rpx rgba(99, 102, 241, 0.35); }
	.play-icon { font-size: 80rpx; }
	.play-text { font-size: 24rpx; color: #ffffff; font-weight: 600; }
	.spell-input { margin-top: 36rpx; height: 92rpx; line-height: 92rpx; background: #f5f7fa; border-radius: 16rpx; padding: 0 24rpx; font-size: 32rpx; color: #303133; text-align: center; }
	.hint-line { margin-top: 18rpx; }
	.hint-btn { font-size: 24rpx; color: #6366f1; }
	.hint-text { margin-top: 14rpx; font-size: 26rpx; color: #6b7280; }

	.fb { margin-top: 24rpx; padding: 22rpx 24rpx; border-radius: 16rpx; text-align: left; }
	.fb-ok { background: #dcfce7; }
	.fb-bad { background: #fee2e2; }
	.fb-msg { font-size: 28rpx; font-weight: 600; }
	.fb-ok .fb-msg { color: #166534; }
	.fb-bad .fb-msg { color: #991b1b; }

	.foot { display: flex; gap: 20rpx; margin-top: 28rpx; }
	.foot-btn { flex: 1; text-align: center; }

	.done-stats { margin-top: 24rpx; display: flex; align-items: baseline; justify-content: center; gap: 12rpx; }
	.ds-num { font-size: 72rpx; font-weight: 800; color: #10b981; }
	.ds-sep { font-size: 40rpx; color: #cbd5e1; }
	.ds-total { font-size: 40rpx; color: #909399; }

	.rpt-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f2f6; }
	.rpt-item:last-child { border-bottom: none; }
	.rpt-head { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
	.rpt-word { font-size: 28rpx; font-weight: 700; color: #1f2d52; }
	.rpt-trans { font-size: 23rpx; color: #6b7280; flex: 1; min-width: 0; text-align: right; }
	.rpt-line { margin-top: 6rpx; font-size: 24rpx; color: #6b7280; }
	.rpt-wrong { color: #e11d48; }
</style>
