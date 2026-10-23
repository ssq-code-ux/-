<template>
	<view class="page">
		<view class="card">
			<view class="card-title">
				<text>📌 {{ d.subject || '家教单详情' }}</text>
				<text class="jmono">{{ d.jobNo }}</text>
			</view>

			<view class="row" style="flex-wrap:wrap;gap:12rpx;">
				<status-tag :type="jobStatusTag(d)" :text="jobStatusText(d)" />
				<text v-if="d.refundPending" class="refund-tag">试课失败待退费</text>
				<text v-if="d.payToApply === 1" class="pay-tag">💰 需缴费解锁</text>
				<text v-else class="free-tag">免费投递</text>
			</view>

			<view class="grid">
				<view class="cell"><text class="k">年级</text>{{ d.grade || '—' }}</view>
				<view class="cell"><text class="k">时薪</text>{{ d.salary ? d.salary + ' 元/h' : '—' }}</view>
				<view class="cell"><text class="k">投递数</text>{{ d.applyCount || 0 }}</view>
				<view class="cell"><text class="k">学生性别</text>{{ d.studentGender || '不限' }}</view>
				<view class="cell"><text class="k">要求老师</text>{{ d.expectGender ? d.expectGender + '老师' : '不限' }}</view>
				<view class="cell"><text class="k">发布时间</text>{{ fmtTime(d.createTime) || '—' }}</view>
				<view class="cell wide"><text class="k">上课频次</text>{{ d.frequency || '—' }}</view>
				<view class="cell wide"><text class="k">地点</text>{{ region }}</view>
				<view class="cell wide"><text class="k">学生情况</text>{{ d.situation || '—' }}</view>
				<view class="cell wide"><text class="k">要求</text>{{ d.requirement || '—' }}</view>
				<view class="cell wide" v-if="d.closeTime"><text class="k">关闭时间</text>{{ fmtTime(d.closeTime) }}</view>
				<view class="cell wide" v-if="d.approvedTeacherName">
					<text class="k">已通过老师</text>
					<text>{{ d.approvedTeacherName }}</text>
					<text v-if="d.approvedTeacherPhone" class="tel-link" @click="callPhone(d.approvedTeacherPhone)"> 📞 {{ d.approvedTeacherPhone }}</text>
				</view>
			</view>
		</view>

		<!-- 家长联系方式 -->
		<view class="card" v-if="d.parentContact">
			<view class="apc-title">📞 家长联系方式</view>
			<view class="apc-line">
				<text>{{ d.parentContact.contactName || '—' }}</text>
				<text v-if="d.parentContact.phone" class="tel-link" @click="callPhone(d.parentContact.phone)"> 📞 {{ d.parentContact.phone }}</text>
				<text v-if="d.parentContact.wechat"> · 💬 {{ d.parentContact.wechat }}</text>
			</view>
			<view class="apc-line muted" v-if="d.parentContact.grade || d.parentContact.studentLevel">
				<text v-if="d.parentContact.grade">{{ d.parentContact.grade }}</text>
				<text v-if="d.parentContact.subject"> · {{ d.parentContact.subject }}</text>
				<text v-if="d.parentContact.studentLevel"> · 学生情况：{{ d.parentContact.studentLevel }}</text>
			</view>
		</view>
		<view class="card" v-else>
			<text class="muted">该单为管理员手工发布，无关联家长</text>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-primary" @click="goApps">查看投递（{{ d.applyCount || 0 }}）</view>
		</view>
	</view>
</template>

<script>
	import { adminTutorJobDetail } from '../../api/index.js';
	import { jobStatusText, jobStatusTag, fmtTime } from '../../utils/format.js';

	export default {
		data() { return { id: '', d: {} }; },
		computed: {
			region() {
				const d = this.d;
				return (d.province || '') + (d.city ? ' - ' + d.city : '') + (d.address ? ' ' + d.address : '') || '—';
			}
		},
		onLoad(opt) {
			this.id = opt.id || '';
			this.load();
		},
		methods: {
			jobStatusText, jobStatusTag, fmtTime,
			async load() {
				if (!this.id) return;
				try { this.d = await adminTutorJobDetail(this.id) || {}; } catch (e) { /* 已提示 */ }
			},
			callPhone(phone) {
				if (!phone) return;
				uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} });
			},
			goApps() { uni.navigateTo({ url: `/pages/applications/applications?jobId=${this.id}` }); }
		}
	};
</script>

<style scoped>
	.jmono { font-family: Consolas, Monaco, monospace; font-size: 22rpx; color: #a0a4ab; font-weight: 400; }
	.refund-tag { font-size: 22rpx; color: #fff; background: #e11d48; border-radius: 8rpx; padding: 2rpx 12rpx; }
	.pay-tag { font-size: 22rpx; color: #e11d48; background: rgba(225, 29, 72, 0.08); border-radius: 8rpx; padding: 2rpx 12rpx; }
	.free-tag { font-size: 22rpx; color: #6b7280; background: #f2f4f8; border-radius: 8rpx; padding: 2rpx 12rpx; }

	.grid { margin-top: 20rpx; display: flex; flex-wrap: wrap; }
	.cell {
		width: 50%;
		box-sizing: border-box;
		padding: 12rpx 8rpx;
		font-size: 26rpx;
		color: #303133;
		line-height: 1.5;
	}
	.cell.wide { width: 100%; }
	.cell .k { color: #909399; margin-right: 12rpx; }

	.apc-title { font-weight: 600; color: #b88230; font-size: 28rpx; margin-bottom: 10rpx; }
	.apc-line { font-size: 26rpx; color: #5c4827; line-height: 1.7; }

	.bottom-bar { padding: 20rpx 24rpx 40rpx; }
</style>
