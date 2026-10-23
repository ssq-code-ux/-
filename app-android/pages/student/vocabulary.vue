<template>
	<view class="page">
		<!-- 分段切换 -->
		<view class="seg">
			<text class="seg-item" :class="{ on: tab === 'list' }" @click="switchTab('list')">📖 词汇</text>
			<text class="seg-item" :class="{ on: tab === 'error' }" @click="switchTab('error')">
				错题本<text v-if="counts.error" class="seg-badge">{{ counts.error }}</text>
			</text>
			<text class="seg-item" :class="{ on: tab === 'review' }" @click="switchTab('review')">
				复习本<text v-if="counts.review" class="seg-badge seg-badge-blue">{{ counts.review }}</text>
			</text>
			<text class="seg-item" :class="{ on: tab === 'mastered' }" @click="switchTab('mastered')">
				已掌握<text v-if="counts.mastered" class="seg-badge seg-badge-green">{{ counts.mastered }}</text>
			</text>
		</view>

		<!-- ══ 词汇列表 ══ -->
		<template v-if="tab === 'list'">
			<view class="top-bar">
				<search-bar v-model="keyword" placeholder="搜索单词 / 释义" @search="reloadVocab" @clear="reloadVocab">
					<template #extra>
						<view class="tb-btn" @click="go('/pages/student/quiz')">🎯 测验</view>
						<view class="tb-btn" @click="go('/pages/student/dictation')">🎧 听写</view>
						<view class="tb-btn" @click="go('/pages/student/flashcards')">🃏 卡片</view>
					</template>
				</search-bar>
			</view>

			<view class="list">
				<view v-for="v in vocabularies" :key="v.id" class="card wcard">
					<view class="row-between">
						<view class="w-left">
							<text class="w-word">{{ v.word }}</text>
							<text class="speak-btn" @click="speak(v.word)">🔊</text>
							<text v-if="v.phonetic" class="w-phonetic">{{ v.phonetic }}</text>
						</view>
						<text class="w-pos" v-if="v.partOfSpeech">{{ v.partOfSpeech }}</text>
					</view>
					<text class="w-trans">{{ v.translation }}</text>
					<view v-if="v.extensions" class="w-ext">
						<text v-for="(e, i) in splitExt(v.extensions)" :key="i" class="w-ext-tag">{{ e }}</text>
					</view>
				</view>
				<empty-state v-if="!loading && !vocabularies.length" text="暂无词汇，请联系老师添加 📖" />
				<load-more :status="moreStatus" />
			</view>
		</template>

		<!-- ══ 错题三本 ══ -->
		<template v-else>
			<view class="err-head">
				<view class="err-stats">
					<text class="es-item">📋 错题本 <text class="es-strong">{{ counts.error }}</text></text>
					<text class="es-item">🔁 复习本 <text class="es-strong">{{ counts.review }}</text></text>
					<text class="es-item">✅ 已掌握 <text class="es-strong">{{ counts.mastered }}</text></text>
				</view>
				<view v-if="tab === 'error'" class="btn btn-primary err-review-btn" :class="{ 'btn-disabled': !counts.error }" @click="go('/pages/student/error-review')">🔄 复习错题</view>
			</view>

			<!-- 已掌握本：只显示单词 + 忘了 -->
			<view v-if="tab === 'mastered'" class="list">
				<view v-if="errorRecords.length" class="chips">
					<view v-for="rec in errorRecords" :key="rec.id" class="chip">
						<text class="chip-word">{{ rec.word }}</text>
						<text class="chip-mastered">已掌握</text>
						<text class="chip-forget" @click="forget(rec.id)">忘了</text>
					</view>
				</view>
				<empty-state v-else-if="!loading" text="暂无已掌握的单词" />
			</view>

			<view v-else class="list">
				<view v-for="rec in errorRecords" :key="rec.id" class="card ecard">
					<view class="ecard-top">
						<text class="e-word" @click="showDetail(rec)">{{ rec.word }}</text>
						<text class="speak-btn" @click="speak(rec.word)">🔊</text>
						<text v-if="rec.phonetic" class="e-phonetic">{{ rec.phonetic }}</text>
						<text v-if="rec.partOfSpeech" class="e-pos">{{ rec.partOfSpeech }}</text>
						<text class="e-type" :class="rec.errorType === 'spelling' ? 't-spell' : (rec.errorType === 'unfamiliar' ? 't-unfamil' : 't-mean')">{{ rec.errorType === 'spelling' ? '拼写错误' : (rec.errorType === 'unfamiliar' ? '不熟' : '选错意思') }}</text>
					</view>
					<view class="e-detail">
						<view class="e-line"><text class="e-label">释义：</text><text>{{ rec.translation }}</text></view>
						<view v-if="rec.extensions" class="e-ext">
							<text class="e-label">📚 延伸词：</text>
							<text v-for="(ext, i) in splitExt(rec.extensions)" :key="i" class="e-ext-tag">{{ ext }}</text>
						</view>
						<view class="e-line"><text class="e-label">你的答案：</text><text class="e-wrong">{{ rec.wrongAnswer || '未作答' }}</text></view>
						<view v-if="rec.correctAnswer" class="e-line"><text class="e-label">正确答案：</text><text class="e-correct">{{ rec.correctAnswer }}</text></view>
						<view class="e-meta">错误 {{ rec.errorCount }} 次 · {{ (rec.createTime || '').slice(0, 10) }}</view>
					</view>
					<view class="ecard-ops">
						<text v-if="tab === 'error'" class="mini-btn mini-btn-success" @click="markMastered(rec.id)">✅ 已掌握</text>
						<text v-else class="mini-btn mini-btn-success" @click="reviewMastered(rec.id)">✅ 再次掌握</text>
						<text class="mini-btn mini-btn-danger" @click="deleteError(rec.id)">🗑️ 删除</text>
					</view>
				</view>
				<empty-state v-if="!loading && !errorRecords.length" :text="tab === 'error' ? '暂无错题记录 🎉' : '暂无待复习单词 🎉'" />
				<load-more :status="moreStatus" />
			</view>
		</template>

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
				<view class="detail-line"><text class="dl-label">错误类型：</text><text>{{ detail.errorType === 'spelling' ? '拼写错误' : (detail.errorType === 'unfamiliar' ? '不熟' : '选错意思') }}</text></view>
				<view class="detail-line" v-if="detail.wrongAnswer"><text class="dl-label">你的作答：</text><text class="e-wrong">{{ detail.wrongAnswer }}</text></view>
				<view class="detail-line" v-if="detail.correctAnswer"><text class="dl-label">正确答案：</text><text class="e-correct">{{ detail.correctAnswer }}</text></view>
				<view class="detail-line"><text class="dl-label">错误次数：</text><text>{{ detail.errorCount || 1 }}</text></view>
				<view class="btn btn-primary detail-close" @click="detail = null">关闭</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { vocabList, vocabSearch, getQuizErrors, errorStats, markErrorMastered, reviewMastered as reviewMasteredApi, forgetError, deleteQuizError } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				tab: 'list', keyword: '', loading: false,
				vocabularies: [], vocabTotal: 0, vocabPage: 1, vocabSize: 12,
				errorRecords: [], errorTotal: 0, errorPage: 1, errorSize: 20,
				counts: { error: 0, review: 0, mastered: 0 },
				detail: null
			};
		},
		computed: {
			moreStatus() {
				if (this.loading) return 'loading';
				const len = this.tab === 'list' ? this.vocabularies.length : this.errorRecords.length;
				const total = this.tab === 'list' ? this.vocabTotal : this.errorTotal;
				if (len >= total) return 'noMore';
				return '';
			}
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			if (opt && opt.tab) this.tab = opt.tab === 'errors' ? 'error' : opt.tab;
		},
		onShow() {
			if (!guardRole('student')) return;
			if (this.tab === 'list') this.reloadVocab();
			else this.reloadErrors();
		},
		onPullDownRefresh() {
			const p = this.tab === 'list' ? this.reloadVocab() : this.reloadErrors();
			Promise.resolve(p).finally(() => uni.stopPullDownRefresh());
		},
		onUnload() { stopSpeak(); },
		onReachBottom() {
			if (this.loading) return;
			if (this.tab === 'list' && this.vocabularies.length < this.vocabTotal) this.loadVocab(this.vocabPage + 1);
			else if (this.tab !== 'list' && this.tab !== 'mastered' && this.errorRecords.length < this.errorTotal) this.loadErrors(this.errorPage + 1);
		},
		methods: {
			splitExt(s) { return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []; },
			speak(w) { speakWord(w); },
			showDetail(rec) { this.detail = rec; },
			switchTab(t) {
				if (this.tab === t) return;
				this.tab = t;
				if (t === 'list') this.reloadVocab();
				else this.reloadErrors();
			},
			go(url) { uni.navigateTo({ url }); },
			reloadVocab() { return this.loadVocab(1); },
			async loadVocab(page) {
				this.loading = true;
				try {
					const kw = String(this.keyword || '').trim();
					const r = kw ? await vocabSearch({ keyword: kw, page, size: this.vocabSize }) : await vocabList({ page, size: this.vocabSize });
					const rows = r.records || r.list || [];
					this.vocabularies = page === 1 ? rows : this.vocabularies.concat(rows);
					this.vocabTotal = r.total || 0;
					this.vocabPage = page;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			async loadErrorStats() {
				try {
					const s = await errorStats();
					this.counts.error = (s && s.errorCount) || 0;
					this.counts.review = (s && s.reviewCount) || 0;
					this.counts.mastered = (s && s.masteredCount) || 0;
				} catch (e) { /* 忽略 */ }
			},
			reloadErrors() { return this.loadErrors(1); },
			async loadErrors(page) {
				this.loading = true;
				try {
					const r = await getQuizErrors(page, this.errorSize, undefined, this.tab);
					const rows = r.records || [];
					this.errorRecords = page === 1 ? rows : this.errorRecords.concat(rows);
					this.errorTotal = r.total || 0;
					this.errorPage = page;
					this.counts.error = r.errorCount || 0;
					this.counts.review = r.reviewCount || 0;
					this.counts.mastered = r.masteredCount || 0;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			markMastered(id) {
				uni.showModal({
					title: '标记掌握', content: '确定已掌握这个单词吗？将移入复习本。',
					success: async (r) => {
						if (!r.confirm) return;
						try { await markErrorMastered(id); uni.showToast({ title: '已移入复习本', icon: 'none' }); this.reloadErrors(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			reviewMastered(id) {
				uni.showModal({
					title: '再次掌握', content: '确定已再次掌握这个单词吗？将移入已掌握本。',
					success: async (r) => {
						if (!r.confirm) return;
						try { await reviewMasteredApi(id); uni.showToast({ title: '已移入已掌握本', icon: 'none' }); this.reloadErrors(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			forget(id) {
				uni.showModal({
					title: '忘了', content: '确定忘记这个单词吗？将退回错题本。',
					success: async (r) => {
						if (!r.confirm) return;
						try { await forgetError(id); uni.showToast({ title: '已退回错题本', icon: 'none' }); this.reloadErrors(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			deleteError(id) {
				uni.showModal({
					title: '删除错题', content: '确定删除这条错题记录吗？', confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await deleteQuizError(id); uni.showToast({ title: '已删除', icon: 'success' }); this.loadErrorStats(); this.reloadErrors(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.seg { display: flex; margin: 20rpx 24rpx 0; background: #eef0f4; border-radius: 16rpx; padding: 6rpx; }
	.seg-item { flex: 1; text-align: center; height: 72rpx; line-height: 72rpx; font-size: 27rpx; color: #6b7280; border-radius: 12rpx; position: relative; }
	.seg-item.on { background: #ffffff; color: #6366f1; font-weight: 700; box-shadow: 0 2rpx 10rpx rgba(99, 102, 241, 0.12); }
	.seg-badge { position: absolute; top: 8rpx; margin-left: 6rpx; min-width: 30rpx; height: 30rpx; line-height: 30rpx; padding: 0 8rpx; border-radius: 999rpx; background: #ef4444; color: #ffffff; font-size: 19rpx; }
	.seg-badge-blue { background: #d97706; }
	.seg-badge-green { background: #16a34a; }
	.seg-item { font-size: 25rpx; }

	.top-bar { padding: 16rpx 0 0; }
	.tb-btn { height: 60rpx; line-height: 60rpx; padding: 0 22rpx; border-radius: 999rpx; background: #eef2ff; color: #6366f1; font-size: 24rpx; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.wcard { margin-bottom: 18rpx; }
	.w-left { display: flex; align-items: center; gap: 14rpx; flex: 1; min-width: 0; }
	.speak-btn { font-size: 30rpx; padding: 2rpx 8rpx; }
	.w-word { font-size: 36rpx; font-weight: 800; color: #1f2d52; }
	.w-pos { font-size: 22rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.w-phonetic { font-size: 24rpx; color: #909399; }
	.w-trans { display: block; margin-top: 10rpx; font-size: 27rpx; color: #606266; line-height: 1.6; }
	.w-ext { margin-top: 12rpx; display: flex; flex-wrap: wrap; gap: 10rpx; }
	.w-ext-tag { font-size: 22rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 5rpx 16rpx; }

	.err-head { margin: 16rpx 24rpx 0; padding: 24rpx; border-radius: 16rpx; background: linear-gradient(120deg, #6366f1, #38bdf8); color: #ffffff; }
	.err-stats { display: flex; flex-direction: column; gap: 8rpx; }
	.es-item { font-size: 25rpx; opacity: 0.95; }
	.es-strong { font-weight: 800; font-size: 28rpx; }
	.es-warn .es-strong { color: #ffe08a; }
	.err-review-btn { margin-top: 18rpx; width: 100%; text-align: center; background: #ffffff; color: #4f46e5; }

	.ecard { margin-bottom: 18rpx; }
	.ecard-mastered { opacity: 0.72; }
	.ecard-top { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
	.e-word { font-size: 32rpx; font-weight: 800; color: #1f2d52; }
	.e-pos { font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.e-type { font-size: 21rpx; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.t-spell { color: #c2410c; background: #fff7ed; }
	.t-mean { color: #7c3aed; background: #f3e8ff; }
	.t-unfamil { color: #6b7280; background: #f3f4f6; }
	.e-mastered { margin-left: auto; font-size: 21rpx; color: #16a34a; }

	.chips { display: flex; flex-wrap: wrap; gap: 16rpx; }
	.chip { display: flex; align-items: center; gap: 12rpx; padding: 12rpx 22rpx; border-radius: 999rpx; background: #f2f4f8; }
	.chip-word { font-size: 28rpx; color: #1f2d52; }
	.chip-forget { font-size: 24rpx; color: #e11d48; padding-left: 12rpx; border-left: 1rpx solid #e5e7eb; }
	.e-phonetic { font-size: 23rpx; color: #909399; }

	.detail-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0,0,0,.45); z-index: 999; display: flex; align-items: center; justify-content: center; }
	.detail-box { width: 620rpx; max-height: 80vh; overflow-y: auto; background: #ffffff; border-radius: 24rpx; padding: 36rpx 32rpx; }
	.detail-head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
	.detail-word { font-size: 40rpx; font-weight: 800; color: #1f2d52; }
	.detail-line { font-size: 27rpx; color: #4b5563; line-height: 1.8; }
	.dl-label { color: #909399; }
	.detail-close { margin-top: 26rpx; width: 100%; text-align: center; }
	.e-detail { margin-top: 14rpx; }
	.e-line { font-size: 25rpx; color: #4b5563; line-height: 1.7; }
	.e-label { color: #909399; }
	.e-ext { margin: 8rpx 0; display: flex; flex-wrap: wrap; align-items: center; gap: 8rpx; }
	.e-ext-tag { font-size: 21rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 4rpx 14rpx; }
	.e-wrong { color: #e11d48; }
	.e-correct { color: #16a34a; font-weight: 600; }
	.e-meta { margin-top: 8rpx; font-size: 21rpx; color: #b0b4bb; }
	.ecard-ops { margin-top: 16rpx; display: flex; gap: 14rpx; }
</style>
