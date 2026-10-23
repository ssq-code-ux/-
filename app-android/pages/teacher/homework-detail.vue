<template>
	<view class="page">
		<view v-if="hw" class="wrap">
			<view class="card">
				<view class="title-lg">{{ hw.title }}</view>
				<view class="meta">
					<status-tag :type="statusTag(hw.status)" :text="hw.status || '未提交'" />
					<text class="muted" v-if="hw.type">{{ hw.type === 'answer_sheet' ? '答题卡' : hw.type }}</text>
				</view>
				<view class="muted line">截止：{{ hw.deadline ? fmtTime(hw.deadline) : '无截止时间' }}</view>
			</view>

			<view class="card">
				<view class="card-title">📋 作业内容</view>
				<view class="pre">{{ hw.content || '（无文字内容）' }}</view>
				<view v-if="attachImages.length" class="thumbs">
					<image v-for="(u, i) in attachImages" :key="i" class="thumb" :src="u" mode="aspectFill" @click="preview(attachImages, i)" />
				</view>
				<view v-for="(u, i) in attachOthers" :key="'a' + i" class="file-line" @click="openDoc(u)">📎 附件：{{ fileName(u) }}（点击打开）</view>
			</view>

			<!-- 答题卡：系统自动判分，可逐题复核调分 -->
			<view class="card" v-if="hw.type === 'answer_sheet'">
				<view class="muted">📄 这是答题卡作业，学生提交后系统已自动判分，可逐题复核调分。</view>
				<view v-if="hw.status !== '未提交'" class="btn btn-primary sheet-review-btn" @click="goSheetReview">📄 答题卡批改</view>
				<view v-else class="muted sheet-pending">学生还没提交，交卷后自动判分</view>
			</view>

			<!-- 学生提交与批改 -->
			<template v-else>
				<view class="card" v-for="sub in submitList" :key="sub.id">
					<view class="card-title">📤 学生提交<text class="card-sub">{{ fmtTime(sub.submitTime) }}</text></view>
					<view v-if="sub.textAnswer" class="pre answer">{{ sub.textAnswer }}</view>
					<view v-if="subImages(sub.fileUrl).length" class="thumbs">
						<image v-for="(u, i) in subImages(sub.fileUrl)" :key="i" class="thumb" :src="u" mode="aspectFill" @click="preview(subImages(sub.fileUrl), i)" />
					</view>
					<view v-for="(u, i) in subOthers(sub.fileUrl)" :key="'s' + i" class="file-line" @click="openDoc(u)">📄 {{ fileName(u) }}（点击打开）</view>
					<view v-if="!sub.textAnswer && !sub.fileUrl" class="muted">（无文字与图片内容）</view>

					<!-- 批改表单 -->
					<view class="review" v-if="reviewForms[sub.id]">
						<view class="rv-row">
							<text class="rv-label">分数</text>
							<input class="rv-score" type="number" v-model="reviewForms[sub.id].score" placeholder="分数" />
						</view>
						<textarea class="rv-comment" v-model="reviewForms[sub.id].comment" placeholder="评语（选填）" maxlength="500" />
						<view v-if="reviewForms[sub.id].voiceUrl" class="voice" @click="playVoice(sub.id, reviewForms[sub.id].voiceUrl)">
							<text>{{ playingId === sub.id ? '⏸ 停止语音评语' : '🎧 播放语音评语' }}</text>
						</view>
						<view class="rec-row">
							<view v-if="recordingId === sub.id" class="voice voice-rec" @click="stopRecord">
								<text>⏹ 录音中 {{ recSeconds }}s · 点击停止</text>
							</view>
							<view v-else class="voice voice-rec" @click="startRecord(sub.id)">
								<text>🎤 {{ reviewForms[sub.id].voiceUrl ? '重录语音评语' : '录制语音评语' }}</text>
							</view>
							<view v-if="reviewForms[sub.id].voiceUrl" class="voice voice-del" @click="dropVoice(sub.id)">
								<text>🗑 删除语音</text>
							</view>
						</view>
						<view class="rv-foot">
							<view class="rv-toggle">
								<text class="rv-label">打回重做</text>
								<switch :checked="reviewForms[sub.id].needRedo" color="#e11d48" style="transform:scale(0.8)" @change="reviewForms[sub.id].needRedo = $event.detail.value" />
							</view>
							<view class="btn btn-primary rv-btn" @click="doReview(sub.id)">{{ savingId === sub.id ? '提交中…' : '提交批改' }}</view>
						</view>
						<view class="muted rv-tip">🎤 语音评语小程序原生录制（mp3），学生端在批改结果页播放</view>
					</view>
				</view>

				<view class="card" v-if="!loading && !submitList.length">
					<empty-state text="学生还没提交，提交后可在这里直接批改" />
				</view>
			</template>
		</view>
		<view v-else-if="loading" class="card"><view class="muted">加载中…</view></view>
	</view>
</template>

<script>
	import { getHomework, submits, getSubmitReview, review, upload } from '../../api/index.js';
	import { fmtTime, mediaUrl, splitFiles, isImageUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TAG = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };

	export default {
		data() {
			return {
				id: null, hw: null, submitList: [], reviewForms: {},
				loading: false, savingId: null, playingId: null, audio: null,
				recorder: null, recordingId: null, recSeconds: 0, recTimer: null
			};
		},
		computed: {
			attachImages() { return splitFiles(this.hw && this.hw.attachmentUrl).filter(isImageUrl).map(mediaUrl); },
			attachOthers() { return splitFiles(this.hw && this.hw.attachmentUrl).filter((u) => !isImageUrl(u)); }
		},
		onLoad(opt) {
			if (!guardRole('teacher')) return;
			this.id = opt && opt.id;
			const rec = uni.getRecorderManager();
			rec.onStop((res) => this.onRecordStop(res));
			this.recorder = rec;
			this.load();
		},
		onUnload() { this.stopVoice(); this.abortRecord(); },
		methods: {
			fmtTime,
			statusTag(s) { return TAG[s] || 'info'; },
			subImages(fileUrl) { return splitFiles(fileUrl).filter(isImageUrl).map(mediaUrl); },
			subOthers(fileUrl) { return splitFiles(fileUrl).filter((u) => !isImageUrl(u)); },
			async load() {
				this.loading = true;
				try {
					const d = await getHomework(this.id);
					this.hw = d || null;
					if (d && d.status !== '未提交' && d.type !== 'answer_sheet') {
						const subs = (await submits(this.id).catch(() => [])) || [];
						this.submitList = subs;
						const forms = {};
						for (const sub of subs) {
							const ex = await getSubmitReview(sub.id).catch(() => null);
							forms[sub.id] = {
								score: ex && ex.score != null ? ex.score : 80,
								comment: ex ? (ex.comment || '') : '',
								voiceUrl: ex ? (ex.voiceUrl || '') : '',
								needRedo: ex ? String(ex.needRedo) === 'true' : false
							};
						}
						this.reviewForms = forms;
					} else {
						this.submitList = [];
						this.reviewForms = {};
					}
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			goSheetReview() { uni.navigateTo({ url: `/pages/teacher/answer-sheet-review?id=${this.id}` }); },
			fileName(u) { const s = String(u || ''); return decodeURIComponent(s.split('/').pop() || '附件'); },
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			openDoc(u) {
				uni.showLoading({ title: '下载中' });
				uni.downloadFile({
					url: mediaUrl(u),
					success: (res) => {
						uni.hideLoading();
						uni.openDocument({ filePath: res.tempFilePath, showMenu: true, fail: () => uni.showToast({ title: '该格式暂不支持预览', icon: 'none' }) });
					},
					fail: () => { uni.hideLoading(); uni.showToast({ title: '下载失败', icon: 'none' }); }
				});
			},
			async doReview(subId) {
				const f = this.reviewForms[subId];
				if (!f || this.savingId) return;
				if (this.recordingId === subId) { uni.showToast({ title: '请先停止录音再提交', icon: 'none' }); return; }
				this.savingId = subId;
				try {
					await review({ submitId: subId, score: f.score, comment: f.comment, voiceUrl: f.voiceUrl || null, needRedo: f.needRedo ? 'true' : 'false' });
					uni.showToast({ title: f.needRedo ? '已打回重做' : '批改完成', icon: 'success' });
					this.load();
				} catch (e) { /* 已提示 */ } finally { this.savingId = null; }
			},
			playVoice(subId, voiceUrl) {
				if (this.playingId === subId) { this.stopVoice(); return; }
				this.stopVoice();
				if (!this.audio) {
					this.audio = uni.createInnerAudioContext();
					this.audio.onEnded(() => { this.playingId = null; });
					this.audio.onError(() => { this.playingId = null; uni.showToast({ title: '语音播放失败（格式可能不支持）', icon: 'none' }); });
				}
				this.audio.src = mediaUrl(voiceUrl);
				this.audio.play();
				this.playingId = subId;
			},
			stopVoice() { if (this.audio) this.audio.stop(); this.playingId = null; },
			/* ── 语音评语原生录制（mp3） ── */
			startRecord(subId) {
				if (this.recordingId || !this.recorder) return;
				const doStart = () => {
					this.recordingId = subId;
					this.recSeconds = 0;
					this.recTimer = setInterval(() => {
						this.recSeconds++;
						if (this.recSeconds >= 120) this.stopRecord(); // 到 2 分钟自动停
					}, 1000);
					this.recorder.start({ format: 'mp3', duration: 120000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 32000 });
				};
				uni.authorize({
					scope: 'scope.record',
					success: doStart,
					fail: () => {
						uni.showModal({
							title: '需要麦克风权限', content: '录制语音评语需要使用麦克风，请在设置中允许录音权限。',
							confirmText: '去设置',
							success: (r) => { if (r.confirm) uni.openSetting(); }
						});
					}
				});
			},
			stopRecord() { if (this.recordingId && this.recorder) this.recorder.stop(); },
			/* 离开页面时丢弃未完成的录音（不触发上传） */
			abortRecord() {
				if (this.recTimer) { clearInterval(this.recTimer); this.recTimer = null; }
				if (!this.recordingId) return;
				this.recordingId = null;
				if (this.recorder) this.recorder.stop();
			},
			async onRecordStop(res) {
				const subId = this.recordingId;
				if (this.recTimer) { clearInterval(this.recTimer); this.recTimer = null; }
				this.recordingId = null;
				if (!subId || !res || !res.tempFilePath) return; // abortRecord 或空录音
				const f = this.reviewForms[subId];
				if (!f) return;
				uni.showLoading({ title: '上传语音' });
				try {
					const r = await upload(res.tempFilePath);
					if (r && r.fileUrl) {
						f.voiceUrl = r.fileUrl;
						uni.showToast({ title: '语音评语已附上', icon: 'success' });
					}
				} catch (e) { /* 已提示 */ } finally { uni.hideLoading(); }
			},
			dropVoice(subId) {
				if (this.recordingId === subId) this.stopRecord();
				const f = this.reviewForms[subId];
				if (f) f.voiceUrl = '';
			}
		}
	};
</script>

<style scoped>
	.wrap { padding-bottom: 40rpx; }
	.meta { margin-top: 14rpx; display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; }
	.line { margin-top: 8rpx; }
	.pre { font-size: 27rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.answer { margin-top: 10rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 18rpx; }
	.thumbs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 14rpx; }
	.thumb { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #f2f4f8; }
	.file-line { margin-top: 12rpx; font-size: 25rpx; color: #0d9488; line-height: 1.5; }
	.sheet-review-btn { margin-top: 16rpx; text-align: center; }
	.sheet-pending { margin-top: 12rpx; font-size: 23rpx; color: #b0b4bb; }

	.review { margin-top: 20rpx; border-top: 1rpx dashed #eef0f4; padding-top: 20rpx; }
	.rv-row { display: flex; align-items: center; gap: 16rpx; }
	.rv-label { font-size: 26rpx; color: #4b5563; }
	.rv-score { width: 160rpx; height: 76rpx; line-height: 76rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.rv-comment { width: 100%; box-sizing: border-box; height: 130rpx; margin-top: 16rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; color: #303133; }
	.voice { margin-top: 16rpx; text-align: center; height: 72rpx; line-height: 72rpx; border-radius: 36rpx; background: linear-gradient(135deg, #14b8a6, #0ea5e9); color: #ffffff; font-size: 26rpx; }
	.rec-row { margin-top: 14rpx; display: flex; gap: 14rpx; }
	.voice-rec { margin-top: 0; flex: 1; background: linear-gradient(135deg, #6366f1, #8b5cf6); }
	.voice-del { margin-top: 0; flex-shrink: 0; padding: 0 28rpx; background: #f2f4f8; color: #e11d48; }
	.rv-foot { margin-top: 18rpx; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
	.rv-toggle { display: flex; align-items: center; gap: 4rpx; }
	.rv-btn { padding: 0 40rpx; height: 72rpx; line-height: 72rpx; font-size: 26rpx; }
	.rv-tip { margin-top: 12rpx; font-size: 21rpx; }
</style>
