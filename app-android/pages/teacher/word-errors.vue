<template>
	<view class="page">
		<!-- ══ 三本列表 ══ -->
		<view v-if="phase === 'list'" class="wrap">
			<view class="book-tabs">
				<view class="book-tab" :class="{ on: bookTab === 'error' }" @click="switchBook('error')">错题本 {{ counts.error }}</view>
				<view class="book-tab" :class="{ on: bookTab === 'review' }" @click="switchBook('review')">复习本 {{ counts.review }}</view>
				<view class="book-tab" :class="{ on: bookTab === 'mastered' }" @click="switchBook('mastered')">已掌握 {{ counts.mastered }}</view>
			</view>

			<view class="card stat-card">
				<view class="stat-row">
					<view class="stat"><text class="stat-num n-red">{{ counts.error }}</text><text class="stat-label">错题本</text></view>
					<view class="stat"><text class="stat-num n-orange">{{ counts.review }}</text><text class="stat-label">复习本</text></view>
					<view class="stat"><text class="stat-num n-green">{{ counts.mastered }}</text><text class="stat-label">已掌握</text></view>
				</view>
				<view class="bank-line">词库：{{ bank }}</view>
				<view v-if="bookTab === 'error'" class="btn btn-primary stat-btn" :class="{ 'btn-disabled': !counts.error }" @click="goCountSelect">🚀 开始错题复习</view>
			</view>

			<!-- 已掌握本：只显示单词 + 忘了 -->
			<view v-if="bookTab === 'mastered'" class="card">
				<view v-if="errors.length" class="chips">
					<view v-for="e in errors" :key="e.id" class="chip">
						<text class="chip-word">{{ e.word }}</text>
						<text class="chip-mastered">已掌握</text>
						<text class="chip-forget" @click="forget(e.id)">忘了</text>
					</view>
				</view>
				<view v-else class="empty-card">
					<view class="empty-text">{{ loading ? '加载中…' : '暂无已掌握的单词' }}</view>
				</view>
			</view>

			<!-- 错题本 / 复习本 -->
			<view v-else-if="errors.length" class="card">
				<view class="card-title">📋 {{ bookTab === 'error' ? '错题记录' : '待复习单词' }}</view>
				<view v-for="e in errors" :key="e.id" class="err-item">
					<view class="err-head">
						<view class="err-word-box">
							<text class="err-word" @click="showDetail(e)">{{ e.word }}</text>
							<text class="speak-btn" @click="speak(e.word)">🔊</text>
							<text v-if="e.phonetic" class="err-phonetic">{{ e.phonetic }}</text>
						</view>
						<text class="err-type" :class="typeClass(e.errorType)">{{ typeText(e.errorType) }}</text>
					</view>
					<view class="err-trans">{{ e.translation }}</view>
					<view class="err-line" v-if="e.wrongAnswer">你的作答：<text class="err-wrong">{{ e.wrongAnswer }}</text></view>
					<view class="err-line">错误次数：<text class="hl">{{ e.errorCount || 1 }}</text></view>
					<view class="err-actions">
						<text v-if="bookTab === 'error'" class="mini-btn mini-btn-success" @click="mastered(e.id)">已掌握</text>
						<text v-else class="mini-btn mini-btn-success" @click="reviewMastered(e.id)">再次掌握</text>
						<text class="mini-btn mini-btn-danger" @click="removeError(e.id)">删除</text>
					</view>
				</view>
			</view>
			<view v-else class="card empty-card">
				<view class="empty-emoji">🎉</view>
				<view class="empty-text">{{ loading ? '加载中…' : (bookTab === 'error' ? '该词库暂无错题，继续保持！' : '该词库暂无待复习单词') }}</view>
			</view>

			<view v-if="total > size" class="pager">
				<view class="btn btn-plain pager-btn" :class="{ 'btn-disabled': page <= 1 }" @click="prevPage">上一页</view>
				<text class="pager-info">{{ page }} / {{ maxPage }}</text>
				<view class="btn btn-plain pager-btn" :class="{ 'btn-disabled': page >= maxPage }" @click="nextPage">下一页</view>
			</view>
		</view>

		<!-- ══ 数量选择 ══ -->
		<view v-else-if="phase === 'countSelect'" class="wrap">
			<view class="card center-card">
				<view class="big-emoji">🔄</view>
				<view class="center-title">错题复习</view>
				<view class="center-sub">词库：{{ bank }} · 复习多少个错题？</view>
				<view class="hint">当前错题本有 {{ counts.error }} 个单词</view>
				<view class="count-grid">
					<text v-for="c in countOptions" :key="c" class="count-chip" :class="{ on: count === c }" @click="count = c">{{ c }}</text>
				</view>
				<view class="btn btn-primary center-btn" :class="{ 'btn-disabled': loading }" @click="begin">{{ loading ? '加载中…' : '🚀 开始复习' }}</view>
				<view class="back-link" @click="phase = 'list'">← 返回错题本</view>
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
			<view class="foot">
				<view class="btn btn-plain foot-btn" @click="backToList">返回错题本</view>
				<view class="btn btn-primary foot-btn" @click="reset">再来一次 🔄</view>
			</view>
		</view>

		<!-- 单词详细信息弹窗 -->
		<view v-if="detail" class="detail-mask" @click="detail = null">
			<view class="detail-box" @click.stop>
				<view class="detail-head">
					<text class="detail-word">{{ detail.word }}</text>
					<text class="speak-btn" @click="speak(detail.word)">🔊</text>
				</view>
				<view class="detail-line"><text class="dl-label">读音：</text><text>{{ detail.phonetic || '—' }}</text></view>
				<view class="detail-line"><text class="dl-label">词性：</text><text>{{ detail.partOfSpeech || '—' }}</text></view>
				<view class="detail-line"><text class="dl-label">释义：</text><text>{{ detail.translation || '—' }}</text></view>
				<view class="detail-line" v-if="detail.extensions"><text class="dl-label">延伸词：</text><text>{{ detail.extensions }}</text></view>
				<view class="detail-line"><text class="dl-label">错误类型：</text><text>{{ typeText(detail.errorType) }}</text></view>
				<view class="detail-line" v-if="detail.wrongAnswer"><text class="dl-label">你的作答：</text><text class="err-wrong">{{ detail.wrongAnswer }}</text></view>
				<view class="detail-line" v-if="detail.correctAnswer"><text class="dl-label">正确答案：</text><text class="hl">{{ detail.correctAnswer }}</text></view>
				<view class="detail-line"><text class="dl-label">错误次数：</text><text>{{ detail.errorCount || 1 }}</text></view>
				<view class="btn btn-primary detail-close" @click="detail = null">关闭</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { getQuizErrors, reviewErrors, recordQuizError, markErrorMastered, reviewMastered as reviewMasteredApi, forgetError, deleteQuizError } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				phase: 'list', bank: '学生词库', loading: false,
				bookTab: 'error', errors: [], total: 0, page: 1, size: 20,
				counts: { error: 0, review: 0, mastered: 0 },
				count: 10, words: [], currentIndex: 0, answer: '', feedback: null, attempt: 0,
				correct: 0, reviewErrorsList: [], detail: null
			};
		},
		computed: {
			current() { return this.words[this.currentIndex] || null; },
			maxPage() { return Math.max(1, Math.ceil((this.total || 0) / this.size)); },
			countOptions() {
				const max = Math.max(5, Math.min(30, this.counts.error || 10));
				const opts = [];
				for (let c = 5; c <= max; c += 5) opts.push(c);
				if (!opts.length) opts.push(5);
				return opts;
			},
			ringClass() { const r = this.words.length ? this.correct / this.words.length : 0; return r >= 0.8 ? 'ring-high' : (r >= 0.5 ? 'ring-mid' : 'ring-low'); }
		},
		onLoad() {
			if (!guardRole('teacher')) return;
			try { this.bank = uni.getStorageSync('teacher-words-bank') || '学生词库'; } catch (e) { /* 忽略 */ }
			this.loadAll();
		},
		onShow() { if (guardRole('teacher') && this.phase === 'list') this.loadAll(); },
		onPullDownRefresh() { this.loadAll().finally(() => uni.stopPullDownRefresh()); },
		onUnload() { stopSpeak(); },
		methods: {
			speak(w) { speakWord(w); },
			showDetail(rec) { this.detail = rec; },
			typeText(t) { return t === 'spelling' ? '拼写' : (t === 'unfamiliar' ? '不熟' : '选义'); },
			typeClass(t) { return t === 'spelling' ? 't-spell' : (t === 'unfamiliar' ? 't-unfamil' : 't-meaning'); },
			async loadAll() {
				this.loading = true;
				try {
					const res = await getQuizErrors(this.page, this.size, this.bank, this.bookTab);
					this.errors = (res && res.records) || [];
					this.total = (res && res.total) || 0;
					this.counts.error = (res && res.errorCount) || 0;
					this.counts.review = (res && res.reviewCount) || 0;
					this.counts.mastered = (res && res.masteredCount) || 0;
				} catch (e) { this.errors = []; } finally {
					this.loading = false;
				}
			},
			switchBook(book) { this.bookTab = book; this.page = 1; this.loadAll(); },
			prevPage() { if (this.page <= 1) return; this.page--; this.loadAll(); },
			nextPage() { if (this.page >= this.maxPage) return; this.page++; this.loadAll(); },
			async mastered(id) {
				try { await markErrorMastered(id); uni.showToast({ title: '已移入复习本', icon: 'none' }); await this.loadAll(); } catch (e) { /* 已提示 */ }
			},
			async reviewMastered(id) {
				try { await reviewMasteredApi(id); uni.showToast({ title: '已移入已掌握本', icon: 'none' }); await this.loadAll(); } catch (e) { /* 已提示 */ }
			},
			async forget(id) {
				try { await forgetError(id); uni.showToast({ title: '已退回错题本', icon: 'none' }); await this.loadAll(); } catch (e) { /* 已提示 */ }
			},
			removeError(id) {
				uni.showModal({
					title: '提示', content: '确定删除该单词记录？',
					success: async (r) => {
						if (!r.confirm) return;
						try { await deleteQuizError(id); uni.showToast({ title: '已删除', icon: 'none' }); await this.loadAll(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			goCountSelect() {
				if (!this.counts.error) { uni.showToast({ title: '暂无错题 🎉', icon: 'none' }); return; }
				this.count = Math.min(10, this.counts.error || 10);
				this.phase = 'countSelect';
			},
			async begin() {
				if (this.loading) return;
				this.loading = true;
				try {
					const data = await reviewErrors(this.count, this.bank);
					this.words = data.words || [];
					if (!this.words.length) { uni.showToast({ title: '该词库暂无错题需要复习 🎉', icon: 'none' }); return; }
					this.phase = 'reviewing'; this.currentIndex = 0; this.answer = ''; this.feedback = null; this.attempt = 0; this.correct = 0; this.reviewErrorsList = [];
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
					this.reviewErrorsList.push({ wordId: word.id, word: word.word, wrongAnswer: this.answer, correctAnswer: word.translation });
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
			reset() { this.phase = 'countSelect'; this.count = Math.min(10, this.counts.error || 10); },
			backToList() { this.phase = 'list'; this.page = 1; this.loadAll(); }
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx 24rpx 40rpx; }
	.book-tabs { display: flex; gap: 12rpx; margin-bottom: 16rpx; }
	.book-tab { flex: 1; text-align: center; padding: 18rpx 0; border-radius: 16rpx; background: #f2f4f8; color: #4b5563; font-size: 26rpx; font-weight: 600; }
	.book-tab.on { background: #eef2ff; color: #6366f1; }
	.stat-card { padding: 30rpx 24rpx; }
	.stat-row { display: flex; }
	.stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
	.stat-num { font-size: 48rpx; font-weight: 800; }
	.n-red { color: #ef4444; }
	.n-orange { color: #d97706; }
	.n-green { color: #16a34a; }
	.stat-label { margin-top: 6rpx; font-size: 23rpx; color: #909399; }
	.bank-line { margin-top: 20rpx; text-align: center; font-size: 24rpx; color: #6366f1; }
	.stat-btn { margin-top: 22rpx; width: 100%; text-align: center; }

	.err-item { padding: 22rpx 0; border-bottom: 1rpx solid #f0f2f6; }
	.err-item:last-child { border-bottom: none; }
	.err-head { display: flex; align-items: center; justify-content: space-between; }
	.err-word-box { display: flex; align-items: center; gap: 10rpx; }
	.err-word { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.err-phonetic { font-size: 23rpx; color: #909399; }
	.detail-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0,0,0,.45); z-index: 999; display: flex; align-items: center; justify-content: center; }
	.detail-box { width: 620rpx; max-height: 80vh; overflow-y: auto; background: #ffffff; border-radius: 24rpx; padding: 36rpx 32rpx; }
	.detail-head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
	.detail-word { font-size: 40rpx; font-weight: 800; color: #1f2d52; }
	.detail-line { font-size: 27rpx; color: #4b5563; line-height: 1.8; }
	.dl-label { color: #909399; }
	.detail-close { margin-top: 26rpx; width: 100%; text-align: center; }
	.speak-btn { font-size: 30rpx; }
	.err-type { font-size: 22rpx; border-radius: 999rpx; padding: 4rpx 16rpx; }
	.t-spell { color: #d97706; background: #fef3c7; }
	.t-unfamil { color: #6b7280; background: #f3f4f6; }
	.t-meaning { color: #e11d48; background: #fdecef; }
	.err-trans { margin-top: 8rpx; font-size: 26rpx; color: #4b5563; }
	.err-line { margin-top: 6rpx; font-size: 23rpx; color: #6b7280; }
	.err-wrong { color: #e11d48; }
	.hl { color: #6366f1; font-weight: 700; }
	.err-actions { margin-top: 6rpx; }

	.chips { display: flex; flex-wrap: wrap; gap: 16rpx; }
	.chip { display: flex; align-items: center; gap: 12rpx; padding: 12rpx 22rpx; border-radius: 999rpx; background: #f2f4f8; }
	.chip-word { font-size: 28rpx; color: #1f2d52; }
	.chip-mastered { font-size: 20rpx; color: #16a34a; background: rgba(22,163,74,.12); border-radius: 999rpx; padding: 2rpx 12rpx; }
	.chip-forget { font-size: 24rpx; color: #e11d48; padding-left: 12rpx; border-left: 1rpx solid #e5e7eb; }

	.empty-card { padding: 40rpx; text-align: center; }
	.empty-emoji { font-size: 90rpx; }
	.empty-text { margin-top: 14rpx; font-size: 26rpx; color: #909399; }

	.pager { display: flex; align-items: center; justify-content: center; gap: 20rpx; margin-top: 10rpx; }
	.pager-btn { width: 180rpx; }
	.pager-info { font-size: 25rpx; color: #6b7280; }

	.center-card { padding: 60rpx 40rpx; text-align: center; }
	.big-emoji { font-size: 96rpx; }
	.center-title { margin-top: 12rpx; font-size: 38rpx; font-weight: 700; color: #1f2d52; }
	.center-sub { margin-top: 10rpx; font-size: 26rpx; color: #909399; }
	.hint { margin-top: 8rpx; font-size: 23rpx; color: #6366f1; }
	.count-grid { display: flex; flex-wrap: wrap; gap: 16rpx; justify-content: center; margin: 32rpx 0; }
	.count-chip { width: 120rpx; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: 16rpx; background: #f2f4f8; color: #4b5563; font-size: 34rpx; font-weight: 700; border: 2rpx solid transparent; }
	.count-chip.on { background: #eef2ff; color: #6366f1; border-color: #6366f1; }
	.center-btn { width: 100%; text-align: center; }
	.back-link { margin-top: 22rpx; font-size: 25rpx; color: #6366f1; }

	.phase-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.phase-badge { font-size: 24rpx; font-weight: 700; border-radius: 999rpx; padding: 8rpx 22rpx; background: #eef2ff; color: #6366f1; }
	.phase-prog { font-size: 24rpx; color: #909399; }
	.prog-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; margin-bottom: 20rpx; }
	.prog-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }

	.q-card { padding: 32rpx 28rpx; }
	.q-word-box { text-align: center; }
	.q-word { font-size: 64rpx; font-weight: 800; color: #1f2d52; }
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

	.foot { display: flex; gap: 20rpx; margin-top: 28rpx; }
	.foot-btn { flex: 1; text-align: center; }

	.ring { width: 200rpx; height: 200rpx; border-radius: 50%; margin: 0 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; }
	.ring-high { background: radial-gradient(circle, #ffffff 58%, #dcfce7 59%); border: 10rpx solid #22c55e; }
	.ring-mid { background: radial-gradient(circle, #ffffff 58%, #fef9c3 59%); border: 10rpx solid #eab308; }
	.ring-low { background: radial-gradient(circle, #ffffff 58%, #fee2e2 59%); border: 10rpx solid #ef4444; }
	.ring-num { font-size: 68rpx; font-weight: 800; color: #1f2d52; line-height: 1; }
	.ring-total { font-size: 26rpx; color: #909399; margin-top: 6rpx; }
	.score-msg { margin-top: 24rpx; font-size: 28rpx; font-weight: 600; color: #4b5563; }
</style>