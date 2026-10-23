<template>
	<view class="page">
		<!-- ══ 数量选择 ══ -->
		<view v-if="phase === 'countSelect'" class="wrap">
			<view class="card center-card">
				<view class="big-emoji">🎯</view>
				<view class="center-title">看词选意</view>
				<view class="center-bank">词库：{{ bank }}</view>
				<view class="center-sub">本次要背诵/检测多少个单词？（可自定义）</view>
				<view class="count-grid">
					<text v-for="c in [5, 10, 15, 20, 30, 50]" :key="c" class="count-chip" :class="{ on: wordCount === c }" @click="wordCount = c">{{ c }}</text>
				</view>
				<view class="custom-count">
					<text>自定义：</text>
					<input class="custom-input" type="number" v-model="wordCount" />
					<text>个</text>
				</view>
				<view class="btn btn-primary center-btn" :class="{ 'btn-disabled': loading }" @click="startPhase1">{{ loading ? '出题中…' : '🚀 开始' }}</view>
			</view>
		</view>

		<!-- ══ 阶段一：看词选义 ══ -->
		<view v-else-if="phase === 'phase1'" class="wrap">
			<view class="phase-head">
				<text class="phase-badge b1">阶段一：看词选义</text>
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
						:class="{ on: answer === opt, correct: (feedback && (feedback.showDetails || feedback.finalAttempt) && opt === current.translation), wrong: (feedback && !feedback.correct && !feedback.showDetails && !feedback.finalAttempt && answer === opt && opt !== current.translation) }"
						@click="pickAnswer(opt)">
						<text class="opt-radio"></text><text class="opt-text">{{ opt }}</text>
					</view>
				</view>

				<view v-if="feedback" class="fb" :class="feedback.correct ? 'fb-ok' : 'fb-bad'">
					<text class="fb-msg">{{ feedback.message }}</text>
					<view v-if="feedback.correct && feedback.extensions" class="fb-ext">
						<text class="ext-label">📚 延伸词：</text>
						<text v-for="(e, i) in splitExt(feedback.extensions)" :key="i" class="ext-tag">{{ e }}</text>
					</view>
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

			<view v-if="feedback && feedback.correct" class="foot">
				<view class="btn btn-warn foot-btn" :class="{ 'btn-disabled': flaggedIds.indexOf(current.id) >= 0 }" @click="flagUnfamiliar(current.id)">
					{{ flaggedIds.indexOf(current.id) >= 0 ? '已加入错题本' : '不熟，加入错题本' }}
				</view>
			</view>
			<view class="foot">
				<view v-if="!feedback || (!feedback.showDetails && !feedback.finalAttempt)" class="btn btn-primary foot-btn" :class="{ 'btn-disabled': !answer }" @click="confirmPhase1">确认</view>
				<view v-else class="btn btn-primary foot-btn" @click="nextPhase1">{{ currentIndex + 1 >= words.length ? '进入阶段二 →' : '下一题 →' }}</view>
			</view>
		</view>

		<!-- ══ 阶段二：看义写词 ══ -->
		<view v-else-if="phase === 'phase2'" class="wrap">
			<view class="phase-head">
				<text class="phase-badge b2">阶段二：看义写词</text>
				<text class="phase-prog">{{ p2Index + 1 }} / {{ p2Words.length }}</text>
			</view>
			<view class="prog-track"><view class="prog-fill f2" :style="{ width: (p2Index / p2Words.length * 100) + '%' }"></view></view>

			<view v-if="p2Current" class="card q-card">
				<view class="q-ask">请根据意思写出对应的英文单词：</view>
				<view class="q-meaning">{{ p2Current.translation }}</view>
				<text v-if="p2Current.partOfSpeech" class="q-pos-hint">词性提示：{{ p2Current.partOfSpeech }}</text>
				<input class="spell-input" v-model="p2Input" placeholder="请输入英文单词…" :disabled="!!(p2Feedback && (p2Feedback.showDetails || p2Feedback.finalAttempt))" @confirm="confirmPhase2" />
				<view v-if="p2Feedback" class="fb" :class="p2Feedback.correct ? 'fb-ok' : 'fb-bad'">
					<text class="fb-msg">{{ p2Feedback.message }}</text>
					<view v-if="p2Feedback.correct && p2Feedback.extensions" class="fb-ext">
						<text class="ext-label">📚 延伸词：</text>
						<text v-for="(e, i) in splitExt(p2Feedback.extensions)" :key="i" class="ext-tag">{{ e }}</text>
					</view>
				</view>
			</view>

			<view class="foot">
				<view v-if="!p2Feedback || (!p2Feedback.showDetails && !p2Feedback.finalAttempt)" class="btn btn-primary foot-btn" :class="{ 'btn-disabled': !p2Input.trim() }" @click="confirmPhase2">确认</view>
				<view v-else class="btn btn-primary foot-btn" @click="nextPhase2">{{ p2Index + 1 >= p2Words.length ? '查看报告 →' : '下一题 →' }}</view>
			</view>
		</view>

		<!-- ══ 报告 ══ -->
		<view v-else class="wrap">
			<view class="card center-card">
				<view class="ring" :class="ringClass">
					<text class="ring-num">{{ p2Correct }}</text>
					<text class="ring-total">/ {{ p2Words.length }}</text>
				</view>
				<view class="score-msg">{{ reportMsg }}</view>
				<view class="mastered-hint">✅ 已掌握（看词选意 + 默写都通过）：{{ mastered }} 个</view>
			</view>

			<view class="card" v-if="p1Errors.length">
				<view class="card-title">📖 阶段一错题（看词选义）</view>
				<view v-for="(e, i) in p1Errors" :key="'e1' + i" class="rpt-item">
					<view class="rpt-head"><text class="rpt-word">{{ i + 1 }}. {{ e.word }}</text><text class="rpt-badge">❌ 选错</text></view>
					<view class="rpt-line">你的选择：<text class="rpt-wrong">{{ e.wrongAnswer }}</text></view>
					<view class="rpt-line">正确答案：<text class="rpt-correct">{{ e.correctAnswer }}</text></view>
				</view>
			</view>

			<view class="card" v-if="p2Errors.length">
				<view class="card-title">✍️ 阶段二错题（看义写词）</view>
				<view v-for="(e, i) in p2Errors" :key="'e2' + i" class="rpt-item">
					<view class="rpt-head"><text class="rpt-word">{{ i + 1 }}. {{ e.translation }}</text><text class="rpt-badge">❌ 拼错</text></view>
					<view class="rpt-line">你写的：<text class="rpt-wrong">{{ e.wrongAnswer }}</text></view>
					<view class="rpt-line">正确答案：<text class="rpt-correct">{{ e.correctAnswer }}</text></view>
				</view>
			</view>

			<view class="foot">
				<view class="btn btn-plain foot-btn" @click="reset">再来一次 🔄</view>
				<view v-if="p1Errors.length || p2Errors.length" class="btn btn-primary foot-btn" @click="goErrors">去错题本复习 📋</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { startQuiz, getSpellWords, checkSpelling, recordQuizError, markWordMastered } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				phase: 'countSelect', wordCount: 10, loading: false, bank: '学生词库',
				words: [], currentIndex: 0, answer: '', feedback: null, attempt: 0, p1Errors: [],
				p2Words: [], p2Index: 0, p2Input: '', p2Feedback: null, p2Attempt: 0, p2Errors: [], p2Correct: 0,
				p1Passed: [], p2Passed: [], flaggedIds: [], mastered: 0
			};
		},
		computed: {
			current() { return this.words[this.currentIndex] || null; },
			p2Current() { return this.p2Words[this.p2Index] || null; },
			ringClass() { const r = this.p2Words.length ? this.p2Correct / this.p2Words.length : 0; return r >= 0.8 ? 'ring-high' : (r >= 0.5 ? 'ring-mid' : 'ring-low'); },
			reportMsg() {
				if (this.p2Correct === this.p2Words.length) return '🎉 全部正确！太棒了！';
				if (this.p2Correct >= this.p2Words.length / 2) return '👍 继续加油！';
				return '💪 需要多加练习哦~';
			}
		},
		onLoad() {
			if (!guardRole('teacher')) return;
			try { this.bank = uni.getStorageSync('teacher-words-bank') || '学生词库'; } catch (e) { /* 忽略 */ }
		},
		onUnload() { stopSpeak(); },
		methods: {
			splitExt(s) { return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []; },
			speak(w) { speakWord(w); },
			async startPhase1() {
				if (this.loading) return;
				this.loading = true;
				try {
					const data = await startQuiz(this.wordCount, this.bank);
					this.words = data.words || [];
					if (!this.words.length) { uni.showToast({ title: '该词库词汇不足', icon: 'none' }); return; }
					this.wordCount = this.words.length;
					this.phase = 'phase1'; this.currentIndex = 0; this.answer = ''; this.feedback = null; this.attempt = 0; this.p1Errors = [];
					this.p1Passed = []; this.p2Passed = []; this.flaggedIds = []; this.mastered = 0;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			pickAnswer(opt) { if (!this.feedback || (!this.feedback.showDetails && !this.feedback.finalAttempt)) this.answer = opt; },
			confirmPhase1() {
				if (!this.answer) { uni.showToast({ title: '请先选择一个答案', icon: 'none' }); return; }
				const word = this.current;
				const isCorrect = this.answer === word.translation;
				this.attempt++;
				if (isCorrect) {
					if (this.p1Passed.indexOf(word.id) < 0) this.p1Passed.push(word.id);
					this.feedback = { correct: true, message: '✅ 回答正确！', extensions: word.extensions, showDetails: true };
				} else {
					const already = this.p1Errors.find((e) => e.wordId === word.id);
					if (!already) {
						this.p1Errors.push({ wordId: word.id, word: word.word, wrongAnswer: this.answer, correctAnswer: word.translation });
						recordQuizError(word.id, this.answer, 'meaning').catch(() => {});
					}
					if (this.attempt >= 3) {
						this.feedback = { correct: false, message: '❌ 很遗憾，正确答案是：' + word.translation, extensions: null, showDetails: true, finalAttempt: true };
					} else {
						const remaining = 3 - this.attempt;
						this.feedback = { correct: false, message: '❌ 选错了！请重新选择（还剩 ' + remaining + ' 次机会）', extensions: null, showDetails: false };
						this.answer = '';
						setTimeout(() => { this.feedback = null; }, 2000);
					}
				}
			},
			nextPhase1() {
				this.feedback = null; this.answer = ''; this.attempt = 0;
				if (this.currentIndex + 1 >= this.words.length) this.startPhase2();
				else this.currentIndex++;
			},
			async startPhase2() {
				this.loading = true;
				try {
					const ids = this.words.map((w) => w.id);
					const data = await getSpellWords(ids);
					this.p2Words = data.words || [];
					this.phase = 'phase2'; this.p2Index = 0; this.p2Input = ''; this.p2Feedback = null; this.p2Attempt = 0; this.p2Errors = []; this.p2Correct = 0;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			async confirmPhase2() {
				const input = String(this.p2Input || '').trim();
				if (!input) { uni.showToast({ title: '请输入单词', icon: 'none' }); return; }
				const word = this.p2Current;
				this.p2Attempt++;
				try {
					const result = await checkSpelling(word.id, input);
					if (result.correct) {
						this.p2Correct++;
						if (this.p2Passed.indexOf(word.id) < 0) this.p2Passed.push(word.id);
						this.p2Feedback = { correct: true, message: '✅ 拼写正确！', extensions: result.extensions, showDetails: true };
					} else if (this.p2Attempt >= 3) {
						this.p2Feedback = { correct: false, message: '❌ 很遗憾，正确答案是：' + result.correctAnswer + (result.errorHint ? '\n💡 ' + result.errorHint : ''), extensions: null, showDetails: true, finalAttempt: true };
						this.p2Errors.push({ wordId: word.id, translation: word.translation, wrongAnswer: input, correctAnswer: result.correctAnswer });
					} else {
						const remaining = 3 - this.p2Attempt;
						this.p2Feedback = { correct: false, message: '❌ 拼写错误！' + (result.errorHint ? '💡 ' + result.errorHint + '\n' : '') + '请重新输入（还剩 ' + remaining + ' 次机会）', extensions: null, showDetails: false };
						this.p2Input = '';
						setTimeout(() => { this.p2Feedback = null; }, 2500);
					}
				} catch (e) { /* 已提示 */ }
			},
			nextPhase2() {
				this.p2Feedback = null; this.p2Input = ''; this.p2Attempt = 0;
				if (this.p2Index + 1 >= this.p2Words.length) { this.finalizeMastery(); this.phase = 'report'; }
				else this.p2Index++;
			},
			/** 阶段一答对后手动标记"不熟" → 加入错题本 */
			flagUnfamiliar(id) {
				if (this.flaggedIds.indexOf(id) >= 0) return;
				this.flaggedIds.push(id);
				recordQuizError(id, '', 'unfamiliar').catch(() => {});
				uni.showToast({ title: '已加入错题本', icon: 'none' });
			},
			/** 阶段一答对 + 阶段二拼写通过（且未被标记不熟）→ 已掌握本 */
			finalizeMastery() {
				const ids = this.words.map((w) => w.id);
				const masters = ids.filter((id) => this.p1Passed.indexOf(id) >= 0 && this.p2Passed.indexOf(id) >= 0 && this.flaggedIds.indexOf(id) < 0);
				this.mastered = masters.length;
				masters.forEach((id) => markWordMastered(id).catch(() => {}));
			},
			reset() {
				this.phase = 'countSelect'; this.wordCount = 10; this.words = []; this.p2Words = [];
				this.p1Errors = []; this.p2Errors = []; this.p2Correct = 0;
			},
			goErrors() { uni.navigateTo({ url: '/pages/teacher/word-errors' }); }
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx 24rpx 40rpx; }
	.center-card { padding: 60rpx 40rpx; text-align: center; }
	.big-emoji { font-size: 96rpx; }
	.center-title { margin-top: 12rpx; font-size: 38rpx; font-weight: 700; color: #1f2d52; }
	.center-bank { display: inline-block; margin-top: 12rpx; font-size: 24rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 6rpx 24rpx; }
	.center-sub { margin-top: 12rpx; font-size: 26rpx; color: #909399; }
	.count-grid { display: flex; flex-wrap: wrap; gap: 16rpx; justify-content: center; margin: 36rpx 0; }
	.count-chip { width: 120rpx; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: 16rpx; background: #f2f4f8; color: #4b5563; font-size: 34rpx; font-weight: 700; border: 2rpx solid transparent; }
	.count-chip.on { background: #eef2ff; color: #6366f1; border-color: #6366f1; }
	.custom-count { display: flex; align-items: center; justify-content: center; gap: 14rpx; margin-bottom: 28rpx; font-size: 26rpx; color: #4b5563; }
	.custom-input { width: 160rpx; height: 72rpx; line-height: 72rpx; text-align: center; background: #f5f7fa; border-radius: 12rpx; font-size: 30rpx; color: #303133; }
	.btn-warn { background: #fef3c7; color: #b45309; }
	.mastered-hint { margin-top: 16rpx; font-size: 26rpx; color: #16a34a; font-weight: 600; }
	.center-btn { width: 100%; text-align: center; }

	.phase-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.phase-badge { font-size: 24rpx; font-weight: 700; border-radius: 999rpx; padding: 8rpx 22rpx; }
	.b1 { background: #eef2ff; color: #6366f1; }
	.b2 { background: #e6fffa; color: #0d9488; }
	.phase-prog { font-size: 24rpx; color: #909399; }
	.prog-track { height: 12rpx; border-radius: 999rpx; background: #eef0f4; overflow: hidden; margin-bottom: 20rpx; }
	.prog-fill { height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #6366f1, #38bdf8); }
	.prog-fill.f2 { background: linear-gradient(90deg, #0d9488, #14b8a6); }

	.q-card { padding: 32rpx 28rpx; }
	.q-word-box { text-align: center; }
	.q-word { font-size: 64rpx; font-weight: 800; color: #1f2d52; }
	.speak-btn { font-size: 34rpx; padding: 2rpx 10rpx; }
	.q-pos { display: inline-block; margin-top: 10rpx; font-size: 24rpx; color: #6366f1; background: #eef2ff; border-radius: 999rpx; padding: 4rpx 18rpx; }
	.q-ask { margin: 28rpx 0 16rpx; font-size: 27rpx; color: #4b5563; }
	.q-meaning { font-size: 44rpx; font-weight: 700; color: #1f2d52; text-align: center; line-height: 1.4; }
	.q-pos-hint { display: block; margin-top: 14rpx; text-align: center; font-size: 24rpx; color: #909399; }

	.opts { display: flex; flex-direction: column; gap: 16rpx; }
	.opt { display: flex; align-items: center; gap: 16rpx; padding: 24rpx 22rpx; border-radius: 16rpx; border: 2rpx solid #eef0f4; background: #f8fafc; }
	.opt.on { border-color: #6366f1; background: #eef2ff; }
	.opt.correct { border-color: #22c55e; background: #dcfce7; }
	.opt.wrong { border-color: #ef4444; background: #fee2e2; }
	.opt-radio { width: 32rpx; height: 32rpx; border-radius: 50%; border: 3rpx solid #cbd5e1; flex-shrink: 0; }
	.opt.on .opt-radio { border-color: #6366f1; background: radial-gradient(circle, #6366f1 40%, #ffffff 45%); }
	.opt.correct .opt-radio { border-color: #22c55e; background: radial-gradient(circle, #22c55e 40%, #ffffff 45%); }
	.opt-text { font-size: 28rpx; color: #303133; }

	.spell-input { margin-top: 28rpx; height: 92rpx; line-height: 92rpx; background: #f5f7fa; border-radius: 16rpx; padding: 0 24rpx; font-size: 32rpx; color: #303133; text-align: center; }

	.fb { margin-top: 24rpx; padding: 22rpx 24rpx; border-radius: 16rpx; }
	.fb-ok { background: #dcfce7; }
	.fb-bad { background: #fee2e2; }
	.fb-msg { font-size: 28rpx; font-weight: 600; color: #1f2d52; white-space: pre-wrap; }
	.fb-ok .fb-msg { color: #166534; }
	.fb-bad .fb-msg { color: #991b1b; }
	.fb-ext { margin-top: 12rpx; display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; }
	.ext-label { font-size: 24rpx; color: #4b5563; }
	.ext-tag { font-size: 22rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 4rpx 14rpx; }

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
	.score-msg { margin-top: 24rpx; font-size: 30rpx; font-weight: 600; color: #4b5563; }

	.rpt-item { padding: 18rpx 0; border-bottom: 1rpx solid #f0f2f6; }
	.rpt-item:last-child { border-bottom: none; }
	.rpt-head { display: flex; align-items: center; justify-content: space-between; }
	.rpt-word { font-size: 28rpx; font-weight: 700; color: #1f2d52; }
	.rpt-badge { font-size: 21rpx; color: #e11d48; background: #fdecef; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.rpt-line { margin-top: 6rpx; font-size: 24rpx; color: #6b7280; }
	.rpt-wrong { color: #e11d48; }
	.rpt-correct { color: #16a34a; font-weight: 600; }
</style>