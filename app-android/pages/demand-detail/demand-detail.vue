<template>
	<view class="page">
		<!-- 家长联系方式 -->
		<view class="card contact">
			<view class="apc-title">👨‍👩‍👧 家长联系方式</view>
			<view class="apc-line">
				<text>{{ d.contactName || '—' }}</text>
				<text v-if="d.phone" class="tel-link" @click="callPhone(d.phone)"> 📞 {{ d.phone }}</text>
				<text v-else class="muted"> 📞 未留号码</text>
				<text v-if="d.wechat"> · 💬 {{ d.wechat }}</text>
			</view>
		</view>

		<view class="card">
			<view class="row-between">
				<text class="dno">{{ d.demandNo || '—' }}</text>
				<view class="row">
					<status-tag :type="d.status === 1 ? 'success' : 'warning'" :text="d.status === 1 ? '已发布' : '待发布'" />
					<status-tag v-if="d.deleteRequested === 1" type="danger" text="申请删除" />
				</view>
			</view>
			<view class="grid">
				<view class="cell"><text class="k">学科</text>{{ d.subject || '—' }}</view>
				<view class="cell"><text class="k">年级</text>{{ d.grade || '—' }}</view>
				<view class="cell"><text class="k">期望时薪</text>{{ d.salary ? d.salary + ' 元/h' : '面议' }}</view>
				<view class="cell"><text class="k">学生性别</text>{{ d.studentGender || '不限' }}</view>
				<view class="cell"><text class="k">期望老师</text>{{ d.expectGender ? d.expectGender + '老师' : '不限' }}</view>
				<view class="cell"><text class="k">提交时间</text>{{ fmtTime(d.createTime) || '—' }}</view>
				<view class="cell wide"><text class="k">学生情况</text>{{ d.studentLevel || '—' }}</view>
				<view class="cell wide"><text class="k">其他要求</text>{{ d.otherRequire || '—' }}</view>
				<view class="cell wide"><text class="k">发布时间</text>{{ fmtTime(d.publishTime) || '尚未发布' }}</view>
			</view>
		</view>

		<!-- 关联家教单 -->
		<view class="card" v-if="d.job">
			<view class="apc-title">🏫 关联家教单</view>
			<view class="apc-line">
				<text class="dno sm">{{ d.job.jobNo }}</text> · {{ d.job.subject || '—' }} · {{ d.job.status === 1 ? '开放中' : '已关闭' }}
			</view>
			<view class="apc-line muted">
				{{ (d.job.province || '') + (d.job.city ? ' - ' + d.job.city : '') || '地点未填' }}
				<text v-if="d.job.salary"> · {{ d.job.salary }} 元/h</text>
			</view>
		</view>

		<view class="bottom-bar">
			<view v-if="d.status === 0" class="btn btn-primary" @click="goPublish">发布为家教单</view>
			<view v-if="d.job && d.job.jobNo" class="btn btn-plain" @click="goJob">查看关联家教单</view>
		</view>
	</view>
</template>

<script>
	import { adminParentDemandDetail } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	export default {
		data() { return { id: '', d: {} }; },
		onLoad(opt) { this.id = opt.id || ''; this.load(); },
		methods: {
			fmtTime,
			async load() {
				if (!this.id) return;
				try { this.d = await adminParentDemandDetail(this.id) || {}; } catch (e) { /* 已提示 */ }
			},
			callPhone(phone) { if (phone) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} }); },
			goPublish() { uni.navigateTo({ url: `/pages/demand-publish/demand-publish?id=${this.id}` }); },
			goJob() {
				const jobNo = this.d.job && this.d.job.jobNo;
				if (jobNo) uni.navigateTo({ url: `/pages/jobs/jobs?type=1&keyword=${encodeURIComponent(jobNo)}` });
			}
		}
	};
</script>

<style scoped>
	.contact { background: #fdf6ec; }
	.apc-title { font-weight: 600; color: #b88230; font-size: 28rpx; margin-bottom: 10rpx; }
	.apc-line { font-size: 26rpx; color: #5c4827; line-height: 1.7; }
	.dno { font-family: Consolas, Monaco, monospace; font-size: 28rpx; font-weight: 700; color: #6c5ce7; }
	.dno.sm { font-size: 24rpx; }
	.grid { margin-top: 20rpx; display: flex; flex-wrap: wrap; }
	.cell { width: 50%; box-sizing: border-box; padding: 12rpx 8rpx; font-size: 26rpx; color: #303133; line-height: 1.5; }
	.cell.wide { width: 100%; }
	.cell .k { color: #909399; margin-right: 12rpx; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
