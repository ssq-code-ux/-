<template>
	<view class="page">
		<view v-if="hw" class="wrap">
			<view class="card">
				<view class="title-lg">{{ hw.title }}</view>
				<view class="meta">
					<status-tag :type="hwTag" :text="hwText" />
					<text class="muted" v-if="hw.type">类型 {{ hw.type }}</text>
				</view>
				<view class="muted line">截止：{{ fmtTime(hw.deadline) }}</view>
				<view class="muted line">布置：{{ fmtTime(hw.createTime) }}</view>
			</view>

			<!-- 作业内容 -->
			<view class="card">
				<view class="card-title">📋 作业内容</view>
				<view class="pre">{{ hw.content || '（教师未填写作业说明）' }}</view>
				<view v-if="attachImages.length" class="thumbs">
					<image v-for="(u, i) in attachImages" :key="i" class="thumb" :src="u" mode="aspectFill" @click="preview(attachImages, i)" />
				</view>
				<view v-for="(u, i) in attachOthers" :key="'a' + i" class="file-line" @click="openDoc(u)">📎 教师附件：{{ fileName(u) }}（点击打开）</view>
			</view>

			<!-- 孩子的提交 -->
			<view class="card">
				<view class="card-title">✍️ 孩子的提交</view>
				<template v-if="hw.submit">
					<view class="muted line">提交时间：{{ fmtTime(hw.submit.submitTime) }}</view>
					<view v-if="hw.submit.textAnswer" class="pre answer">{{ hw.submit.textAnswer }}</view>
					<view v-if="submitImages.length" class="thumbs">
						<image v-for="(u, i) in submitImages" :key="i" class="thumb" :src="u" mode="aspectFill" @click="preview(submitImages, i)" />
					</view>
					<view v-for="(u, i) in submitOthers" :key="'s' + i" class="file-line" @click="openDoc(u)">📄 {{ fileName(u) }}（点击打开）</view>
				</template>
				<empty-state v-else text="孩子还没有提交这份作业" />
			</view>

			<!-- 教师批改 -->
			<view class="card" v-if="hw.review">
				<view class="card-title">✅ 教师批改</view>
				<view class="row-between">
					<text class="score">得分：<text class="score-num">{{ hw.review.score == null ? '—' : hw.review.score }}</text> 分</text>
					<status-tag v-if="needRedo" type="warning" text="教师要求重做" />
				</view>
				<view class="muted line">批改于 {{ fmtTime(hw.review.reviewTime) }}</view>
				<view v-if="hw.review.comment" class="pre comment">💬 {{ hw.review.comment }}</view>
				<view v-if="hw.review.voiceUrl" class="voice" @click="playVoice">
					<text>{{ voicePlaying ? '⏸ 停止语音评语' : '🎧 播放老师语音评语' }}</text>
				</view>
			</view>
			<view class="card" v-else-if="hw.submit">
				<view class="muted">已提交，等待教师批改</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { parentChildHomeworkDetail } from '../../api/index.js';
	import { fmtTime, mediaUrl, splitFiles, isImageUrl } from '../../utils/format.js';

	export default {
		data() {
			return {
				childId: null,
				hwId: null,
				hw: null,
				voicePlaying: false,
				audio: null
			};
		},
		computed: {
			attachImages() { return splitFiles(this.hw && this.hw.attachmentUrl).filter(isImageUrl).map(mediaUrl); },
			attachOthers() { return splitFiles(this.hw && this.hw.attachmentUrl).filter((u) => !isImageUrl(u)); },
			submitImages() { return splitFiles(this.hw && this.hw.submit && this.hw.submit.fileUrl).filter(isImageUrl).map(mediaUrl); },
			submitOthers() { return splitFiles(this.hw && this.hw.submit && this.hw.submit.fileUrl).filter((u) => !isImageUrl(u)); },
			needRedo() { const r = this.hw && this.hw.review; return r && (String(r.needRedo) === 'true' || r.needRedo === '是'); },
			hwTag() {
				const h = this.hw; if (!h) return 'info';
				if (h.overdue) return 'danger';
				if (h.status === '已批改') return 'success';
				if (h.status === '已提交') return 'primary';
				if (h.status === '需重做') return 'warning';
				return 'info';
			},
			hwText() { const h = this.hw; if (!h) return ''; return h.overdue ? '已逾期' : (h.status || '未提交'); }
		},
		onLoad(opt) { this.childId = opt.childId; this.hwId = opt.hwId; this.load(); },
		onUnload() { this.stopVoice(); },
		methods: {
			fmtTime,
			async load() {
				try {
					this.hw = await parentChildHomeworkDetail(this.childId, this.hwId);
				} catch (e) { /* 已提示 */ }
			},
			fileName(u) { const s = String(u || ''); return decodeURIComponent(s.split('/').pop() || '附件'); },
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			openDoc(u) {
				const url = mediaUrl(u);
				uni.showLoading({ title: '下载中' });
				uni.downloadFile({
					url,
					success: (res) => {
						uni.hideLoading();
						uni.openDocument({
							filePath: res.tempFilePath, showMenu: true,
							fail: () => uni.showToast({ title: '该格式暂不支持预览', icon: 'none' })
						});
					},
					fail: () => { uni.hideLoading(); uni.showToast({ title: '下载失败', icon: 'none' }); }
				});
			},
			playVoice() {
				if (this.voicePlaying) { this.stopVoice(); return; }
				const url = mediaUrl(this.hw.review.voiceUrl);
				if (!this.audio) {
					this.audio = uni.createInnerAudioContext();
					this.audio.onEnded(() => { this.voicePlaying = false; });
					this.audio.onError(() => { this.voicePlaying = false; uni.showToast({ title: '语音播放失败（格式可能不支持）', icon: 'none' }); });
				}
				this.audio.src = url;
				this.audio.play();
				this.voicePlaying = true;
			},
			stopVoice() { if (this.audio) { this.audio.stop(); } this.voicePlaying = false; }
		}
	};
</script>

<style scoped>
	.wrap { padding-bottom: 40rpx; }
	.meta { margin-top: 14rpx; display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; }
	.line { margin-top: 8rpx; }
	.pre { font-size: 27rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.answer { margin-top: 10rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 18rpx; }
	.comment { margin-top: 10rpx; background: #f0f9eb; border-radius: 12rpx; padding: 16rpx 18rpx; }
	.thumbs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 14rpx; }
	.thumb { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #f2f4f8; }
	.file-line { margin-top: 12rpx; font-size: 25rpx; color: #667eea; line-height: 1.5; }
	.score { font-size: 28rpx; color: #303133; }
	.score-num { font-size: 40rpx; font-weight: 800; color: #f97316; }
	.voice { margin-top: 16rpx; text-align: center; height: 76rpx; line-height: 76rpx; border-radius: 38rpx; background: linear-gradient(135deg, #f97316, #ec4899); color: #ffffff; font-size: 27rpx; }
</style>
