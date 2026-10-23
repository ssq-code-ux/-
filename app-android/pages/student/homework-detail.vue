<template>
	<view class="page">
		<view v-if="hw" class="wrap">
			<view class="card">
				<view class="row-between">
					<view class="title-lg">{{ hw.title }}</view>
					<status-tag :type="statusTag(hw.status)" :text="hw.status" />
				</view>
				<view class="meta">
					<text class="mtag">{{ hw.type === 'answer_sheet' ? '📄 答题卡' : (hw.subject || '作业') }}</text>
					<text class="mdeadline">📅 截止：{{ hw.deadline ? fmtTime(hw.deadline) : '无' }}</text>
				</view>
			</view>

			<view class="card" v-if="hw.content">
				<view class="card-title">📝 作业内容</view>
				<view class="pre">{{ hw.content }}</view>
			</view>

			<view class="card" v-if="images.length">
				<view class="card-title">🖼️ 附图</view>
				<view class="thumbs">
					<image v-for="(u, i) in images" :key="i" class="thumb" :src="u" mode="widthFix" @click="preview(images, i)" />
				</view>
			</view>

			<view class="card" v-if="hw.type === 'answer_sheet'">
				<view class="tip-box">📄 这是一份答题卡作业，在线作答提交后系统立即自动判分。</view>
			</view>

			<view class="card" v-if="!hw.content && !images.length">
				<empty-state text="暂无详细内容" />
			</view>

			<view v-if="canSubmit" class="bottom-bar">
				<view class="btn btn-primary" @click="goSubmit">{{ hw.status === '需重做' ? '重新提交' : '提交作业' }}</view>
			</view>
			<view v-else-if="hw.type === 'answer_sheet' && (hw.status === '未提交' || hw.status === '需重做')" class="bottom-bar">
				<view class="btn btn-primary" @click="goPaper">✍️ 前往作答</view>
			</view>
			<view v-else-if="hw.type === 'answer_sheet'" class="bottom-bar">
				<view class="btn btn-plain" @click="goReview">查看批改结果</view>
			</view>
		</view>
		<view v-else-if="loading" class="card"><view class="muted">加载中…</view></view>
	</view>
</template>

<script>
	import { getHomework } from '../../api/index.js';
	import { fmtTime, mediaUrl, splitFiles, isImageUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TAG = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };

	export default {
		data() {
			return { id: null, hw: null, loading: false };
		},
		computed: {
			images() { return splitFiles(this.hw && this.hw.fileUrl).filter(isImageUrl).map(mediaUrl); },
			canSubmit() { return this.hw && (this.hw.status === '未提交' || this.hw.status === '需重做') && this.hw.type !== 'answer_sheet'; }
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			this.id = opt && opt.id;
			this.load();
		},
		methods: {
			fmtTime,
			statusTag(s) { return TAG[s] || 'info'; },
			async load() {
				this.loading = true;
				try { this.hw = (await getHomework(this.id)) || null; } catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			goSubmit() { uni.navigateTo({ url: `/pages/student/homework-submit?id=${this.id}` }); },
			goPaper() { uni.navigateTo({ url: `/pages/student/answer-sheet?id=${this.id}` }); },
			goReview() { uni.navigateTo({ url: `/pages/student/homework-review?id=${this.id}` }); }
		}
	};
</script>

<style scoped>
	.wrap { padding-bottom: 40rpx; }
	.title-lg { flex: 1; min-width: 0; }
	.meta { margin-top: 14rpx; display: flex; align-items: center; gap: 16rpx; flex-wrap: wrap; }
	.mtag { font-size: 22rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.mdeadline { font-size: 22rpx; color: #909399; }
	.pre { font-size: 28rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.thumbs { display: flex; flex-direction: column; gap: 14rpx; margin-top: 8rpx; }
	.thumb { width: 100%; border-radius: 12rpx; background: #f2f4f8; }
	.tip-box { padding: 20rpx 22rpx; border-radius: 12rpx; background: #fff7ed; color: #c2410c; font-size: 25rpx; line-height: 1.6; }
	.bottom-bar { padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { width: 100%; text-align: center; }
</style>
