<template>
	<view class="page">
		<!-- 模式选择（开始前） -->
		<view v-if="phase === 'setup'" class="wrap">
			<view class="card setup-card">
				<view class="setup-emoji">🃏</view>
				<view class="setup-title">记忆卡片</view>
				<view class="setup-sub">翻转卡片记忆单词，标记「认识 / 不熟」</view>
				<view class="mode-row">
					<view class="mode-item" :class="{ on: studyMode === 'all' }" @click="studyMode = 'all'">
						<text class="mode-name">全部单词</text>
						<text class="mode-desc">从整个词库随机抽取</text>
					</view>
					<view class="mode-item" :class="{ on: studyMode === 'unknown' }" @click="studyMode = 'unknown'">
						<text class="mode-name">只练不熟的</text>
						<text class="mode-desc">排除已标记认识的单词</text>
					</view>
				</view>
				<view class="setup-info" v-if="allWords.length">
					词库共 <text class="hl">{{ allWords.length }}</text> 个单词，已掌握 <text class="hl">{{ knownCount }}</text> 个
				</view>
				<view v-if="allWords.length" class="mastery-track"><view class="mastery-fill" :style="{ width: progressPct + '%' }"></view></view>
				<view class="btn btn-primary setup-btn" :class="{ 'btn-disabled': !allWords.length }" @click="startDeck">开始学习</view>
				<view v-if="allWords.length" class="btn btn-plain reset-btn" @click="resetProgress">清零进度</view>
			</view>
		</view>

		<!-- 翻卡中 -->
		<view v-else-if="phase === 'study'" class="wrap">
			<view class="progress-bar">
				<view class="progress-head">
					<text>{{ currentIndex + 1 }} / {{ deck.length }}</text>
					<text class="prog-known">已认识 {{ sessionKnown }}</text>
				</view>
				<view class="progress-track"><view class="progress-fill" :style="{ width: (currentIndex / deck.length * 100) + '%' }"></view></view>
			</view>

			<view class="fc-card" :class="{ flipped }" @click="onCardClick">
				<view class="fc-count">已掌握：{{ knownCount }}/{{ allWords.length }}</view>
				<!-- 正面：仅单词 + 发音按钮 + 提示 -->
				<view v-if="!flipped" class="fc-face fc-front">
					<text class="fc-word">{{ current.word }}</text>
					<text class="speak-btn" @click.stop="speak(current.word)">🔊</text>
					<text class="fc-hint">👆 点击卡片查看释义</text>
				</view>
				<!-- 背面：单词 + 词性 + 读音 + 翻译 + 延伸词 -->
				<view v-else class="fc-face fc-back">
					<text class="fc-word-sm">{{ current.word }}</text>
					<text class="fc-speak-back" @click.stop="speak(current.word)">🔊 播放读音</text>
					<view class="fc-meta">
						<text v-if="current.partOfSpeech" class="fc-pos">{{ current.partOfSpeech }}</text>
						<text v-if="current.phonetic" class="fc-phonetic">{{ current.phonetic }}</text>
					</view>
					<text class="fc-trans">{{ current.translation }}</text>
					<view v-if="extList.length" class="fc-ext">
						<text v-for="(e, i) in extList" :key="i" class="fc-ext-tag">{{ e }}</text>
					</view>
					<text class="fc-hint">👆 点击卡片返回</text>
				</view>
			</view>

			<view v-if="!answered" class="answer-row">
				<view class="ans-btn ans-no" @click="answer(false)">😕 不熟</view>
				<view class="ans-btn ans-yes" @click="answer(true)">😊 认识</view>
			</view>
			<view class="nav-row">
				<view class="nav-btn" :class="{ 'nav-disabled': currentIndex <= 0 }" @click="prevCard">← 上一个</view>
				<view class="nav-btn" :class="{ 'nav-disabled': currentIndex >= deck.length - 1 }" @click="nextCard">下一个 →</view>
			</view>
		</view>

		<!-- 完成 -->
		<view v-else class="wrap">
			<view class="card done-card">
				<view class="done-emoji">{{ sessionKnown >= deck.length * 0.8 ? '🏆' : '💪' }}</view>
				<view class="done-title">本轮完成！</view>
				<view class="done-stats">
					<view class="done-stat"><text class="ds-num ds-yes">{{ sessionKnown }}</text><text class="ds-label">认识</text></view>
					<view class="done-stat"><text class="ds-num ds-no">{{ deck.length - sessionKnown }}</text><text class="ds-label">不熟</text></view>
				</view>
				<view class="btn btn-primary done-btn" @click="startDeck">再来一轮</view>
				<view class="btn btn-plain done-btn" @click="studyMode = 'unknown'; startDeck()">只练不熟的</view>
				<view class="done-back" @click="phase = 'setup'">← 返回选择</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { vocabList, recordQuizError, errorStats, markWordMastered, resetMastered } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				phase: 'setup', studyMode: 'all', userId: '',
				allWords: [], knownIds: [], masteredCount: 0, deck: [],
				currentIndex: 0, flipped: false, sessionKnown: 0, answered: false
			};
		},
		computed: {
			current() { return this.deck[this.currentIndex] || {}; },
			extList() { const e = this.current.extensions; return e ? String(e).split(',').map((x) => x.trim()).filter(Boolean) : []; },
			// 进度以后端「已掌握本」的数量为准
			knownCount() { return this.masteredCount; },
			progressPct() { return this.allWords.length ? Math.round((this.masteredCount / this.allWords.length) * 100) : 0; }
		},
		onLoad() {
			const user = guardRole('student');
			if (!user) return;
			this.userId = String(user.id || '');
			this.loadKnown();
			this.loadStats();
			this.loadWords();
		},
		onUnload() { stopSpeak(); },
		methods: {
			speak(w) { speakWord(w); },
			storageKey() { return 'flashcards-known-' + this.userId; },
			loadKnown() { try { this.knownIds = uni.getStorageSync(this.storageKey()) || []; } catch (e) { this.knownIds = []; } },
			saveKnown() { try { uni.setStorageSync(this.storageKey(), this.knownIds); } catch (e) { /* 忽略 */ } },
			async loadStats() {
				try {
					const s = await errorStats();
					this.masteredCount = (s && s.masteredCount) || 0;
				} catch (e) { /* 忽略，沿用本地计数 */ }
			},
			async loadWords() {
				try {
					// 分页拉取全部（单页上限 1000，避免只拿到 1000 个）
					const size = 1000;
					let page = 1, all = [], total = 0;
					while (page <= 100) {
						const r = await vocabList({ page, size });
						const rows = r.records || r.list || [];
						all = all.concat(rows);
						total = r.total || 0;
						if (!rows.length || all.length >= total) break;
						page++;
					}
					this.allWords = all;
				} catch (e) { this.allWords = []; }
			},
			startDeck() {
				if (!this.allWords.length) { uni.showToast({ title: '暂无词汇，请联系老师添加', icon: 'none' }); return; }
				let pool = this.allWords;
				if (this.studyMode === 'unknown') {
					pool = this.allWords.filter((w) => this.knownIds.indexOf(w.id) < 0);
					if (!pool.length) { uni.showToast({ title: '全部单词都已认识啦 🎉', icon: 'none' }); return; }
				}
				// Fisher-Yates 洗牌
				const arr = pool.slice();
				for (let i = arr.length - 1; i > 0; i--) {
					const j = Math.floor(Math.random() * (i + 1));
					[arr[i], arr[j]] = [arr[j], arr[i]];
				}
				this.deck = arr.slice(0, 50);
				this.currentIndex = 0; this.flipped = false; this.sessionKnown = 0; this.answered = false;
				this.phase = 'study';
				this.playCurrent();
			},
			playCurrent() {
				const w = this.current;
				if (w && w.word) speakWord(w.word);
			},
			onCardClick() {
				// 无论是否已作答，点击卡片都能翻转（点「不熟」后也能翻回去看）
				this.flipped = !this.flipped;
				if (this.flipped) this.playCurrent();
			},
			resetPerCard() { this.flipped = false; this.answered = false; },
			prevCard() {
				if (this.currentIndex <= 0) return;
				this.currentIndex--;
				this.resetPerCard();
				this.playCurrent();
			},
			nextCard() {
				if (this.currentIndex + 1 >= this.deck.length) { this.phase = 'done'; return; }
				this.currentIndex++;
				this.resetPerCard();
				this.playCurrent();
			},
			answer(known) {
				const w = this.current;
				if (!w || this.answered) return;
				const idx = this.knownIds.indexOf(w.id);
				if (known) {
					this.sessionKnown++;
					if (idx < 0) this.knownIds.push(w.id);
					// 认识 → 计入后端「已掌握本」，进度随之增加
					this.masteredCount++;
					markWordMastered(w.id).catch(() => {});
				} else {
					if (idx >= 0) this.knownIds.splice(idx, 1);
					// 不熟 → 退回错题本（状态=不熟），进度相应减少
					this.masteredCount = Math.max(0, this.masteredCount - 1);
					recordQuizError(w.id, '', 'unfamiliar').catch(() => {});
				}
				this.saveKnown();
				// 认识/不熟都自动翻到背面看详细信息，等用户点「下一个」
				this.flipped = true;
				this.answered = true;
				this.playCurrent();
			},
			resetProgress() {
				uni.showModal({
					title: '清零进度', content: '确定清零该词库的背诵进度吗？所有单词将重新归入"不认识"。',
					success: async (r) => {
						if (!r.confirm) return;
						try { await resetMastered(); } catch (e) { /* 忽略 */ }
						this.knownIds = [];
						this.masteredCount = 0;
						this.saveKnown();
						uni.showToast({ title: '进度已清零', icon: 'none' });
					}
				});
			}
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx; }
	.setup-card { padding: 60rpx 40rpx; text-align: center; }
	.setup-emoji { font-size: 96rpx; }
	.setup-title { margin-top: 16rpx; font-size: 38rpx; font-weight: 700; color: #1f2d52; }
	.setup-sub { margin-top: 10rpx; font-size: 25rpx; color: #909399; }
	.mode-row { display: flex; gap: 20rpx; margin-top: 36rpx; }
	.mode-item { flex: 1; padding: 24rpx 16rpx; border-radius: 16rpx; border: 2rpx solid #eef0f4; background: #f8fafc; }
	.mode-item.on { border-color: #6366f1; background: #eef2ff; }
	.mode-name { display: block; font-size: 27rpx; font-weight: 600; color: #1f2d52; }
	.mode-desc { display: block; margin-top: 8rpx; font-size: 21rpx; color: #909399; }
	.setup-info { margin-top: 30rpx; font-size: 25rpx; color: #6b7280; }
	.hl { color: #6366f1; font-weight: 700; }
	.setup-btn { margin-top: 30rpx; width: 100%; text-align: center; }
	.reset-btn { margin-top: 16rpx; width: 100%; text-align: center; }
	.mastery-track { height: 16rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; margin-top: 24rpx; }
	.mastery-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }
	.nav-row { display: flex; gap: 20rpx; margin-top: 20rpx; }
	.nav-btn { flex: 1; text-align: center; height: 84rpx; line-height: 84rpx; border-radius: 20rpx; background: #eef0f4; color: #4b5563; font-size: 28rpx; font-weight: 600; }
	.nav-disabled { opacity: 0.45; }

	.progress-bar { margin-bottom: 24rpx; }
	.progress-head { display: flex; justify-content: space-between; font-size: 24rpx; color: #6b7280; margin-bottom: 12rpx; }
	.prog-known { color: #10b981; font-weight: 600; }
	.progress-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; }
	.progress-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }

	.fc-card { position: relative; min-height: 560rpx; border-radius: 28rpx; background: #ffffff; box-shadow: 0 10rpx 40rpx rgba(99, 102, 241, 0.14); display: flex; align-items: center; justify-content: center; }
	.fc-count { position: absolute; top: 18rpx; right: 22rpx; z-index: 5; font-size: 22rpx; font-weight: 600; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 6rpx 18rpx; }
	.ans-next { background: linear-gradient(135deg, #667eea, #38bdf8); }
	.fc-face { width: 100%; padding: 60rpx 44rpx; box-sizing: border-box; display: flex; flex-direction: column; align-items: center; text-align: center; }
	.fc-word { font-size: 76rpx; font-weight: 800; color: #1f2d52; letter-spacing: 2rpx; }
	.speak-btn { margin-top: 14rpx; font-size: 40rpx; padding: 2rpx 12rpx; }
	.fc-pos { font-size: 26rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 6rpx 22rpx; }
	.fc-meta { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; flex-wrap: wrap; justify-content: center; }
	.fc-phonetic { font-size: 30rpx; color: #4b5563; }
	.fc-word-sm { font-size: 40rpx; font-weight: 700; color: #6366f1; }
	.fc-speak-back { margin-top: 14rpx; font-size: 24rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 8rpx 24rpx; }
	.fc-trans { margin-top: 28rpx; font-size: 44rpx; font-weight: 700; color: #1f2d52; line-height: 1.4; }
	.fc-ext { margin-top: 28rpx; display: flex; flex-wrap: wrap; gap: 12rpx; justify-content: center; }
	.fc-ext-tag { font-size: 24rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.fc-hint { margin-top: 48rpx; font-size: 23rpx; color: #b0b4bb; }

	.answer-row { display: flex; gap: 24rpx; margin-top: 32rpx; }
	.ans-btn { flex: 1; text-align: center; height: 100rpx; line-height: 100rpx; border-radius: 20rpx; font-size: 32rpx; font-weight: 700; color: #ffffff; }
	.ans-no { background: linear-gradient(135deg, #f87171, #ef4444); }
	.ans-yes { background: linear-gradient(135deg, #34d399, #10b981); }

	.done-card { padding: 70rpx 40rpx; text-align: center; }
	.done-emoji { font-size: 110rpx; }
	.done-title { margin-top: 16rpx; font-size: 38rpx; font-weight: 700; color: #1f2d52; }
	.done-stats { display: flex; justify-content: center; gap: 80rpx; margin: 40rpx 0; }
	.done-stat { display: flex; flex-direction: column; align-items: center; }
	.ds-num { font-size: 60rpx; font-weight: 800; }
	.ds-yes { color: #10b981; }
	.ds-no { color: #ef4444; }
	.ds-label { margin-top: 8rpx; font-size: 24rpx; color: #6b7280; }
	.done-btn { width: 100%; text-align: center; margin-bottom: 20rpx; }
	.done-back { margin-top: 10rpx; font-size: 25rpx; color: #6366f1; }
</style>
