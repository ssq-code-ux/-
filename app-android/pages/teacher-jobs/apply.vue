<template>
	<view class="page">
		<!-- 目标单摘要 -->
		<view class="card sum">
			<text class="sum-subject">{{ subject || '家教单' }}</text>
			<text class="muted" v-if="province || city"> · {{ province }}{{ city ? ' - ' + city : '' }}</text>
			<text class="muted" v-if="salary"> · {{ salary }}元/h</text>
		</view>

		<!-- 接单须知 -->
		<view class="card">
			<view class="card-title">📋 接单须知</view>
			<view v-if="noticeLoading" class="muted">加载中…</view>
			<template v-else>
				<view v-if="!notice.noticeText && !(notice.noticeImgs || []).length" class="muted">管理员还未发布接单须知</view>
				<view v-if="notice.noticeText" class="notice-text">{{ notice.noticeText }}</view>
				<view v-if="(notice.noticeImgs || []).length" class="imgs">
					<image v-for="(img, i) in notice.noticeImgs" :key="i" class="nimg" :src="mediaUrl(img)" mode="aspectFill" @click="preview(notice.noticeImgs, i)" />
				</view>
			</template>
		</view>

		<!-- 需缴费未解锁：展示收款码 + 联系方式，不能投递 -->
		<view class="card pay" v-if="payBlocked">
			<view class="pay-warn">⚠️ 该家教单需缴费后才能投递</view>
			<view class="muted pay-desc">请按接单须知联系管理员沟通，扫描下方收款码完成缴费；管理员确认到账后会为您开通本单的投递权限。</view>
			<view v-if="notice.phone" class="line" @click="callPhone(notice.phone)">📞 管理员电话：<text class="tel-link">{{ notice.phone }}</text></view>
			<view v-if="notice.wechat" class="line" @click="copyWechat">💬 管理员微信：<text class="tel-link">{{ notice.wechat }}</text>（点击复制）</view>
			<view v-if="notice.payQrUrl" class="qr-wrap">
				<image class="qr" :src="mediaUrl(notice.payQrUrl)" mode="aspectFit" @click="previewOne(mediaUrl(notice.payQrUrl))" />
				<view class="muted qr-tip">收款码（点击可放大）</view>
			</view>
		</view>

		<!-- 可投递：简历校验 + 附言 -->
		<template v-else>
			<view class="card profile-warn" v-if="profileIncomplete">
				<view class="pw-text">请先完善简历（姓名、接单科目必填），投递时会自动附上简历</view>
				<view class="btn btn-primary pw-btn" @click="goProfile">📝 去完善简历</view>
			</view>
			<view class="card" v-else>
				<view class="card-title">📄 投递简历</view>
				<view class="muted tip">投递时会自动附上您的完整简历（含个人照）和联系电话，家长通过后管理员会与您联系</view>
				<textarea class="textarea" v-model="message" placeholder="附言：简单介绍自己的教学经验、可上课时间等（选填，500 字以内）" maxlength="500" />
				<view class="count muted">{{ message.length }}/500</view>
			</view>

			<view class="bottom-bar" v-if="!profileIncomplete">
				<view class="btn btn-plain" @click="cancel">取消</view>
				<view class="btn btn-primary" @click="submit">确认投递</view>
			</view>
		</template>
	</view>
</template>

<script>
	import { tutorJobNotice, myTutorProfile, applyTutorJob } from '../../api/index.js';
	import { mediaUrl } from '../../utils/format.js';

	export default {
		data() {
			return {
				jobId: null, needPay: false, paid: false, applied: false,
				subject: '', province: '', city: '', salary: '',
				notice: {}, noticeLoading: false,
				profile: {}, profileLoaded: false,
				message: '', submitting: false
			};
		},
		computed: {
			payBlocked() { return this.needPay && !this.paid && !this.applied; },
			profileIncomplete() { return !this.profile.name || !this.profile.subjects; }
		},
		onLoad(opt) {
			this.jobId = opt.jobId;
			this.needPay = opt.needPay === '1';
			this.paid = opt.paid === '1';
			this.applied = opt.applied === '1';
			this.subject = opt.subject ? decodeURIComponent(opt.subject) : '';
			this.province = opt.province ? decodeURIComponent(opt.province) : '';
			this.city = opt.city ? decodeURIComponent(opt.city) : '';
			this.salary = opt.salary || '';
			this.loadNotice();
			if (!this.payBlocked) this.loadProfile();
		},
		methods: {
			mediaUrl,
			async loadNotice() {
				this.noticeLoading = true;
				try { this.notice = (await tutorJobNotice()) || {}; } catch (e) { this.notice = {}; } finally { this.noticeLoading = false; }
			},
			async loadProfile() {
				try { this.profile = (await myTutorProfile()) || {}; } catch (e) { this.profile = {}; } finally { this.profileLoaded = true; }
			},
			preview(urls, idx) { uni.previewImage({ urls: urls.map(mediaUrl), current: idx }); },
			previewOne(url) { uni.previewImage({ urls: [url] }); },
			callPhone(p) { if (p) uni.makePhoneCall({ phoneNumber: String(p), fail: () => {} }); },
			copyWechat() { if (this.notice.wechat) uni.setClipboardData({ data: this.notice.wechat, success: () => uni.showToast({ title: '微信号已复制', icon: 'none' }) }); },
			goProfile() { uni.navigateTo({ url: '/pages/teacher-jobs/profile' }); },
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				this.submitting = true;
				try {
					await applyTutorJob(this.jobId, this.message.trim());
					uni.showToast({ title: '投递成功，等待审核', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally {
					this.submitting = false;
				}
			}
		}
	};
</script>

<style scoped>
	.sum { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4rpx; }
	.sum-subject { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.notice-text { white-space: pre-wrap; font-size: 27rpx; color: #303133; line-height: 1.9; background: #f0fdfa; border-radius: 12rpx; padding: 20rpx; }
	.imgs { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 16rpx; }
	.nimg { width: 200rpx; height: 200rpx; border-radius: 12rpx; background: #f2f4f8; }

	.pay { border: 1rpx solid #f3d19e; background: #fffdf7; }
	.pay-warn { font-size: 28rpx; font-weight: 700; color: #d97706; }
	.pay-desc { margin-top: 8rpx; line-height: 1.6; }
	.line { margin-top: 12rpx; font-size: 27rpx; color: #303133; }
	.qr-wrap { margin-top: 20rpx; text-align: center; }
	.qr { width: 360rpx; height: 360rpx; border-radius: 12rpx; background: #ffffff; }
	.qr-tip { margin-top: 8rpx; }

	.profile-warn { border: 1rpx solid #fde68a; background: #fffbeb; }
	.pw-text { font-size: 26rpx; color: #92400e; line-height: 1.6; }
	.pw-btn { margin-top: 18rpx; }
	.tip { line-height: 1.6; margin-bottom: 14rpx; }
	.textarea { width: 100%; box-sizing: border-box; height: 220rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.count { text-align: right; margin-top: 8rpx; font-size: 22rpx; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
