<template>
	<view class="page">
		<view class="card">
			<view class="card-title">📋 接单须知<text class="card-sub">投递前请仔细阅读</text></view>
			<view v-if="loading" class="muted">加载中…</view>
			<template v-else>
				<view v-if="!info.noticeText && !(info.noticeImgs || []).length" class="muted empty">管理员还未发布接单须知</view>
				<view v-if="info.noticeText" class="notice-text">{{ info.noticeText }}</view>
				<view v-if="(info.noticeImgs || []).length" class="imgs">
					<image v-for="(img, i) in info.noticeImgs" :key="i" class="nimg" :src="mediaUrl(img)" mode="aspectFill" @click="preview(info.noticeImgs, i)" />
				</view>
			</template>
		</view>

		<view class="card" v-if="info.phone || info.wechat">
			<view class="card-title">📮 管理员联系方式</view>
			<view v-if="info.phone" class="line" @click="callPhone(info.phone)">📞 电话：<text class="tel-link">{{ info.phone }}</text></view>
			<view v-if="info.wechat" class="line" @click="copyWechat">💬 微信：<text class="tel-link">{{ info.wechat }}</text>（点击复制）</view>
		</view>
	</view>
</template>

<script>
	import { tutorJobNotice } from '../../api/index.js';
	import { mediaUrl } from '../../utils/format.js';

	export default {
		data() {
			return { info: {}, loading: false };
		},
		onShow() { this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			mediaUrl,
			async load() {
				this.loading = true;
				try { this.info = (await tutorJobNotice()) || {}; } catch (e) { this.info = {}; } finally { this.loading = false; }
			},
			preview(urls, idx) { uni.previewImage({ urls: urls.map(mediaUrl), current: idx }); },
			callPhone(p) { if (p) uni.makePhoneCall({ phoneNumber: String(p), fail: () => {} }); },
			copyWechat() { if (this.info.wechat) uni.setClipboardData({ data: this.info.wechat, success: () => uni.showToast({ title: '微信号已复制', icon: 'none' }) }); }
		}
	};
</script>

<style scoped>
	.empty { padding: 40rpx 0; text-align: center; }
	.notice-text { white-space: pre-wrap; font-size: 27rpx; color: #303133; line-height: 1.9; background: #f0fdfa; border-radius: 12rpx; padding: 20rpx; }
	.imgs { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 16rpx; }
	.nimg { width: 200rpx; height: 200rpx; border-radius: 12rpx; background: #f2f4f8; }
	.line { font-size: 27rpx; color: #303133; line-height: 2; }
</style>
