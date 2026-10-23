<template>
	<view class="page">
		<view class="wrap">
			<!-- 加载中 -->
			<view v-if="loading" class="card center-card"><view class="muted">加载中…</view></view>

			<!-- 空 -->
			<view v-else-if="!cards.length" class="card center-card">
				<view class="big-emoji">🎉</view>
				<view class="center-title">今天没有到期复习</view>
				<view class="center-sub">好好休息，明天再来巩固记忆吧</view>
				<view class="btn btn-primary center-btn" @click="back">返回</view>
			</view>

			<!-- 完成 -->
			<view v-else-if="finished" class="card center-card">
				<view class="big-emoji">{{ remembered >= cards.length * 0.8 ? '🏆' : '💪' }}</view>
				<view class="center-title">复习完成！</view>
				<view class="done-stats">
					<text class="ds-num">{{ remembered }}</text><text class="ds-sep">/</text><text class="ds-total">{{ cards.length }}</text>
				</view>
				<view class="center-sub">记住了 {{ remembered }} 个单词</view>
				<view class="btn btn-primary center-btn" @click="back">完成</view>
			</view>

			<!-- 复习中 -->
			<template v-else>
				<view class="phase-head">
					<text class="phase-badge">🔁 今日复习</text>
					<text class="phase-prog">{{ index + 1 }} / {{ cards.length }}</text>
				</view>
				<view class="prog-track"><view class="prog-fill" :style="{ width: (index / cards.length * 100) + '%' }"></view></view>
				<view class="stage-line">第 {{ (current.reviewStage || 0) + 1 }} 轮复习</view>

				<view class="fc-card" @click="revealed = !revealed">
					<view v-if="!revealed" class="fc-face">
						<text class="fc-word">{{ current.word }}</text>
						<text class="speak-btn" @click.stop="speak(current.word)">🔊</text>
						<text v-if="current.partOfSpeech" class="fc-pos">{{ current.partOfSpeech }}</text>
						<text class="fc-hint">👆 点击卡片查看释义</text>
					</view>
					<view v-else class="fc-face">
						<text class="fc-word-sm">{{ current.word }}</text>
						<text class="speak-btn" @click.stop="speak(current.word)">🔊</text>
						<text class="fc-trans">{{ current.translation }}</text>
						<view v-if="extList.length" class="fc-ext">
							<text v-for="(e, i) in extList" :key="i" class="fc-ext-tag">{{ e }}</text>
						</view>
					</view>
				</view>

				<view v-if="revealed" class="answer-row">
					<view class="ans-btn ans-no" @click="answerTodayReview(false)">🔁 没记住</view>
					<view class="ans-btn ans-yes" @click="answerTodayReview(true)">✅ 记住了</view>
				</view>
				<view v-else class="answer-tip">先回想一下释义，再点击卡片核对</view>
			</template>
		</view>
	</view>
</template>

<script>
	import { getTodayReview, submitReviewResult } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return { loading: false, cards: [], index: 0, revealed: false, remembered: 0, finished: false };
		},
		computed: {
			current() { return this.cards[this.index] || {}; },
			extList() { const e = this.current.extensions; return e ? String(e).split(',').map((x) => x.trim()).filter(Boolean) : []; }
		},
		onLoad() {
			if (!guardRole('student')) return;
			this.load();
		},
		onUnload() { stopSpeak(); },
		methods: {
			speak(w) { speakWord(w); },
			async load() {
				this.loading = true;
				try {
					const r = await getTodayReview(20);
					this.cards = (r && r.records) || [];
				} catch (e) { this.cards = []; } finally { this.loading = false; }
			},
			async answerTodayReview(correct) {
				const card = this.current;
				if (!card || !card.id) return;
				try { await submitReviewResult(card.id, correct); } catch (e) { /* 已提示 */ }
				if (correct) this.remembered++;
				if (this.index + 1 >= this.cards.length) this.finished = true;
				else { this.index++; this.revealed = false; }
			},
			back() { uni.navigateBack(); }
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx; }
	.center-card { padding: 70rpx 40rpx; text-align: center; }
	.big-emoji { font-size: 100rpx; }
	.center-title { margin-top: 16rpx; font-size: 36rpx; font-weight: 700; color: #1f2d52; }
	.center-sub { margin-top: 12rpx; font-size: 25rpx; color: #909399; }
	.center-btn { margin-top: 36rpx; width: 100%; text-align: center; }
	.done-stats { margin-top: 24rpx; display: flex; align-items: baseline; justify-content: center; gap: 12rpx; }
	.ds-num { font-size: 72rpx; font-weight: 800; color: #10b981; }
	.ds-sep { font-size: 40rpx; color: #cbd5e1; }
	.ds-total { font-size: 40rpx; color: #909399; }

	.phase-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.phase-badge { font-size: 24rpx; font-weight: 700; border-radius: 999rpx; padding: 8rpx 22rpx; background: #eef2ff; color: #6366f1; }
	.phase-prog { font-size: 24rpx; color: #909399; }
	.prog-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; }
	.prog-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }
	.stage-line { margin: 14rpx 0 20rpx; text-align: center; font-size: 23rpx; color: #909399; }

	.fc-card { min-height: 520rpx; border-radius: 28rpx; background: #ffffff; box-shadow: 0 10rpx 40rpx rgba(99, 102, 241, 0.14); display: flex; align-items: center; justify-content: center; }
	.fc-face { width: 100%; padding: 60rpx 44rpx; box-sizing: border-box; display: flex; flex-direction: column; align-items: center; text-align: center; }
	.fc-word { font-size: 76rpx; font-weight: 800; color: #1f2d52; }
	.speak-btn { margin-top: 14rpx; font-size: 40rpx; padding: 2rpx 12rpx; }
	.fc-pos { margin-top: 16rpx; font-size: 26rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 6rpx 22rpx; }
	.fc-word-sm { font-size: 40rpx; font-weight: 700; color: #6366f1; }
	.fc-trans { margin-top: 28rpx; font-size: 44rpx; font-weight: 700; color: #1f2d52; line-height: 1.4; }
	.fc-ext { margin-top: 28rpx; display: flex; flex-wrap: wrap; gap: 12rpx; justify-content: center; }
	.fc-ext-tag { font-size: 24rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.fc-hint { margin-top: 48rpx; font-size: 23rpx; color: #b0b4bb; }

	.answer-row { display: flex; gap: 24rpx; margin-top: 32rpx; }
	.ans-btn { flex: 1; text-align: center; height: 100rpx; line-height: 100rpx; border-radius: 20rpx; font-size: 30rpx; font-weight: 700; color: #ffffff; }
	.ans-no { background: linear-gradient(135deg, #fbbf24, #f59e0b); }
	.ans-yes { background: linear-gradient(135deg, #34d399, #10b981); }
	.answer-tip { margin-top: 32rpx; text-align: center; font-size: 24rpx; color: #b0b4bb; }
</style>
