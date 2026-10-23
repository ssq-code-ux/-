<template>
	<view class="page">
		<view v-if="data" class="wrap">
			<!-- 答题卡：系统自动判分分数卡 -->
			<view class="card score-card" v-if="isSheet && sheet">
				<view class="score-row">
					<view class="score-ring" :class="scoreClass(sheet.review ? sheet.review.score : 0)">
						<text class="score-num">{{ sheet.gotScore }}</text>
						<text class="score-unit">/ {{ sheet.fullScore }} 分</text>
					</view>
					<view class="score-side">
						<text class="pass-tag">全对 {{ sheet.rightCount }} / {{ sheet.total }} 题</text>
						<text v-if="sheet.review" class="score-tip">换算百分制 {{ sheet.review.score }} 分</text>
						<text v-else class="score-tip">系统已自动判分</text>
					</view>
				</view>
			</view>

			<!-- 分数卡 -->
			<view class="card score-card" v-else>
				<view v-if="review" class="score-row">
					<view class="score-ring" :class="scoreClass(review.score)">
						<text class="score-num">{{ review.score }}</text>
						<text class="score-unit">分</text>
					</view>
					<view class="score-side">
						<text v-if="review.needRedo" class="redo-tag">🔁 需重做</text>
						<text v-else class="pass-tag">✅ 已通过</text>
						<text class="score-tip">{{ review.score >= 80 ? '非常棒，继续保持！' : (review.score >= 60 ? '不错，还有提升空间' : '别灰心，再练练') }}</text>
					</view>
				</view>
				<empty-state v-else text="老师还未批改" />
			</view>

			<!-- 我的作答 -->
			<view class="card" v-if="submit && !isSheet">
				<view class="card-title">📝 我的作答</view>
				<view v-if="submit.textAnswer" class="pre">{{ submit.textAnswer }}</view>
				<view v-if="submitImages.length" class="thumbs">
					<image v-for="(u, i) in submitImages" :key="i" class="thumb" :src="u" mode="widthFix" @click="preview(submitImages, i)" />
				</view>
				<view v-for="u in submitDocs" :key="u" class="doc-row" @click="openDoc(u)">
					<text class="doc-name">📄 {{ fileName(u) }}</text>
					<text class="doc-open">打开</text>
				</view>
				<view v-if="!submit.textAnswer && !submitImages.length && !submitDocs.length" class="muted">未填写内容</view>
			</view>

			<!-- 老师评语 -->
			<view class="card" v-if="review && (review.comment || review.voiceUrl)">
				<view class="card-title">💬 老师评语</view>
				<view v-if="review.comment" class="pre comment">{{ review.comment }}</view>
				<view v-if="review.voiceUrl" class="voice-row" @click="playVoice">
					<text class="voice-icon">{{ playing ? '⏸' : '🔊' }}</text>
					<text class="voice-text">{{ playing ? '播放中…点击暂停' : '点击播放语音评语' }}</text>
				</view>
			</view>

			<!-- 答题卡逐题结果 -->
			<view v-for="(g, gi) in resultGroups" :key="'g' + gi" class="card sec">
				<view v-if="g.sectionType !== 'standalone'" class="sec-head">
					<text class="sec-title">{{ g.title || sectionTypeLabel(g.sectionType) }}</text>
					<text class="sec-badge">{{ sectionTypeLabel(g.sectionType) }}</text>
				</view>
				<!-- 看错题时能回去重读原文，默认收起不占屏幕 -->
				<view v-if="g.passage" class="passage-bar" @click="toggleResultPassage(gi)">
					<text>📄 材料原文</text>
					<text class="passage-toggle">{{ passageOpen[gi] ? '收起 ▲' : '展开 ▼' }}</text>
				</view>
				<view v-if="g.passage && passageOpen[gi]" class="passage-text">{{ g.passage }}</view>
				<view v-if="g.wordBank && g.wordBank.length" class="bank">
					<text class="bank-label">备选词</text>
					<text v-for="(w, wi) in g.wordBank" :key="wi" class="bank-tag">{{ w }}</text>
				</view>

				<view v-for="it in g.items" :key="it.questionId" class="r-card" :class="it.correct === 1 ? 'r-right' : (it.correct === 0 ? 'r-wrong' : 'r-pending')">
					<view class="r-head">
						<text class="r-seq">{{ it.seq }}</text>
						<text class="r-type">{{ questionTypeLabel(it.questionType) }}</text>
						<text class="r-flag">{{ it.correct === 1 ? '✔ 全对' : (it.correct === 0 ? '✖ 错了' : '⏳ 待老师批改') }} {{ it.gotScore }}/{{ it.score }} 分</text>
					</view>
					<view class="pre r-content">{{ it.content }}</view>
					<view class="r-row"><text class="r-label">我的答案</text><text :class="{ 'r-mine-wrong': it.correct === 0 }">{{ it.studentAnswer || '（未作答）' }}</text></view>
					<view class="r-row"><text class="r-label">正确答案</text><text class="r-std">{{ it.standardAnswer }}</text></view>
					<view v-if="it.analysis" class="r-row"><text class="r-label">解析</text><text>{{ it.analysis }}</text></view>
					<view v-if="it.aiComment" class="r-row"><text class="r-label">点评</text><text>{{ it.aiComment }}</text></view>
					<view v-if="it.knowledgePoint" class="r-row"><text class="r-label">知识点</text><text>{{ it.knowledgePoint }}</text></view>
				</view>
			</view>
		</view>
		<view v-else-if="loading" class="card"><view class="muted">加载中…</view></view>
	</view>
</template>

<script>
	import { reviewResult, getHomework, answerSheetResult } from '../../api/index.js';
	import { mediaUrl, splitFiles, isImageUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return { id: null, data: null, loading: false, playing: false, audio: null, isSheet: false, sheet: null, passageOpen: {} };
		},
		computed: {
			review() { return (this.data && this.data.review) || null; },
			submit() { return (this.data && this.data.submit) || null; },
			submitImages() { return splitFiles(this.submit && this.submit.fileUrl).filter(isImageUrl).map(mediaUrl); },
			submitDocs() { return splitFiles(this.submit && this.submit.fileUrl).filter((u) => !isImageUrl(u)); },
			/* 逐题结果按大题分组，材料从后端返回的 sections 里取 */
			resultGroups() {
				const d = this.sheet;
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
			}
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			this.id = opt && opt.id;
			this.load();
		},
		onUnload() { if (this.audio) { try { this.audio.destroy(); } catch (e) {} this.audio = null; } },
		methods: {
			scoreClass(s) { return s >= 80 ? 'score-high' : (s >= 60 ? 'score-mid' : 'score-low'); },
			async load() {
				this.loading = true;
				try {
					const hw = await getHomework(this.id).catch(() => null);
					this.isSheet = !!(hw && hw.type === 'answer_sheet');
					this.data = (await reviewResult(this.id)) || null;
					if (this.isSheet && this.data && this.data.submit) {
						this.sheet = await answerSheetResult(this.data.submit.id).catch(() => null);
					}
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
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
			toggleResultPassage(gi) {
				const open = Object.assign({}, this.passageOpen);
				open[gi] = !open[gi];
				this.passageOpen = open;
			},
			fileName(u) { const s = String(u || ''); return decodeURIComponent(s.split('/').pop() || '文档'); },
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			openDoc(u) {
				uni.showLoading({ title: '下载中' });
				uni.downloadFile({
					url: mediaUrl(u),
					success: (r) => { uni.hideLoading(); uni.openDocument({ filePath: r.tempFilePath, showMenu: true, fail: () => uni.showToast({ title: '该格式暂不支持预览', icon: 'none' }) }); },
					fail: () => { uni.hideLoading(); uni.showToast({ title: '下载失败', icon: 'none' }); }
				});
			},
			playVoice() {
				const url = this.review && this.review.voiceUrl;
				if (!url) return;
				if (!this.audio) {
					this.audio = uni.createInnerAudioContext();
					this.audio.src = mediaUrl(url);
					this.audio.onEnded(() => { this.playing = false; });
					this.audio.onError(() => { this.playing = false; uni.showToast({ title: '语音播放失败', icon: 'none' }); });
				}
				if (this.playing) { this.audio.pause(); this.playing = false; }
				else { this.audio.play(); this.playing = true; }
			}
		}
	};
</script>

<style scoped>
	.wrap { padding-bottom: 40rpx; }
	.score-row { display: flex; align-items: center; gap: 32rpx; }
	.score-ring { width: 160rpx; height: 160rpx; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; flex-shrink: 0; }
	.score-high { background: radial-gradient(circle, #ffffff 55%, #dcfce7 56%); border: 8rpx solid #22c55e; }
	.score-mid { background: radial-gradient(circle, #ffffff 55%, #fef9c3 56%); border: 8rpx solid #eab308; }
	.score-low { background: radial-gradient(circle, #ffffff 55%, #fee2e2 56%); border: 8rpx solid #ef4444; }
	.score-num { font-size: 56rpx; font-weight: 800; color: #1f2d52; line-height: 1; }
	.score-unit { font-size: 22rpx; color: #909399; margin-top: 4rpx; }
	.score-side { flex: 1; min-width: 0; }
	.redo-tag { display: inline-block; font-size: 24rpx; color: #e11d48; background: #fdecef; border-radius: 999rpx; padding: 6rpx 20rpx; font-weight: 600; }
	.pass-tag { display: inline-block; font-size: 24rpx; color: #16a34a; background: #dcfce7; border-radius: 999rpx; padding: 6rpx 20rpx; font-weight: 600; }
	.score-tip { display: block; margin-top: 14rpx; font-size: 25rpx; color: #6b7280; line-height: 1.5; }
	.pre { font-size: 28rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.comment { color: #4b5563; }
	.thumbs { display: flex; flex-direction: column; gap: 14rpx; margin-top: 12rpx; }
	.thumb { width: 100%; border-radius: 12rpx; background: #f2f4f8; }
	.doc-row { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; padding: 18rpx 20rpx; background: #f8fafc; border-radius: 12rpx; }
	.doc-name { flex: 1; min-width: 0; font-size: 25rpx; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.doc-open { flex-shrink: 0; margin-left: 16rpx; font-size: 24rpx; color: #6366f1; font-weight: 600; }
	.voice-row { margin-top: 16rpx; display: flex; align-items: center; gap: 16rpx; padding: 20rpx 24rpx; border-radius: 16rpx; background: linear-gradient(120deg, #6366f1, #38bdf8); }
	.voice-icon { font-size: 40rpx; }
	.voice-text { font-size: 26rpx; color: #ffffff; font-weight: 600; }

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
	.r-pending { border-left-color: #eab308; background: #fefce8; }
	.r-head { display: flex; align-items: center; gap: 12rpx; }
	.r-seq { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #6366f1; color: #ffffff; font-size: 22rpx; font-weight: 700; }
	.r-type { font-size: 21rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 3rpx 12rpx; }
	.r-flag { margin-left: auto; font-size: 21rpx; color: #4b5563; font-weight: 600; }
	.r-content { margin-top: 12rpx; font-size: 26rpx; color: #303133; }
	.r-row { margin-top: 10rpx; font-size: 25rpx; color: #4b5563; line-height: 1.7; }
	.r-label { color: #909399; margin-right: 10rpx; }
	.r-mine-wrong { color: #e11d48; }
	.r-std { color: #16a34a; font-weight: 600; }
</style>
