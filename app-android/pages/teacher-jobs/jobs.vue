<template>
	<view class="page">
		<!-- 快捷入口 -->
		<scroll-view scroll-x class="quick" :show-scrollbar="false">
			<view class="quick-in">
				<view class="qbtn" @click="go('/pages/teacher-jobs/profile')">📝 编辑简历</view>
				<view class="qbtn" @click="go('/pages/teacher-jobs/notice')">📋 接单须知</view>
				<view class="qbtn" @click="go('/pages/teacher-jobs/my-applications')">📄 我的投递</view>
				<view class="qbtn warn" @click="go('/pages/teacher-jobs/invite')">🎫 邀请同行</view>
			</view>
		</scroll-view>

		<search-bar v-model="query.keyword" placeholder="搜索城市 / 科目 / 编号" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="typeLabels" :value="typeIdx" @change="onTypePicker">
					<view class="pk-in">{{ typeLabels[typeIdx] }} ▾</view>
				</picker>
				<text class="total-tip">共 {{ total }} 单开放中</text>
			</template>
		</search-bar>

		<view class="list">
			<view v-for="job in list" :key="job.id" class="card jcard">
				<view class="row-between">
					<view class="row jtitle" style="flex-wrap:wrap;gap:10rpx;">
						<text class="jsubject">{{ job.subject }}</text>
					</view>
					<text v-if="job.salary" class="jsalary">{{ job.salary }}元/h</text>
				</view>
				<view class="muted jloc">📍 {{ job.province || '-' }}{{ job.city ? ' - ' + job.city : '' }} | {{ fmtDate(job.createTime) }}</view>

				<view class="jtags">
					<status-tag v-if="job.jobType === 2" type="warning" text="🤝 同行合作单" />
					<status-tag v-if="job.grade" type="info" :text="job.grade" />
					<status-tag v-if="job.studentGender" type="info" :text="job.studentGender" />
					<status-tag v-if="job.expectGender" type="warning" :text="job.expectGender + '老师'" />
					<status-tag v-if="job.frequency" type="info" :text="job.frequency" />
					<status-tag v-if="job.needPay" type="danger" text="💰 需缴费解锁" />
				</view>

				<view v-if="job.address" class="jline">📍 地址：{{ job.address }}</view>
				<view v-if="job.situation" class="jline">📖 情况：{{ job.situation }}</view>
				<view v-if="job.requirement" class="jline">✅ 要求：{{ job.requirement }}</view>

				<view class="jfoot">
					<view class="jfoot-l">
						<text class="jno">编号 {{ job.jobNo }}</text>
						<text class="muted jcount">{{ job.applyCount || 0 }} 人已投递</text>
						<status-tag v-if="job.needPay && job.paid" type="success" text="✓ 已缴费解锁" />
					</view>
					<view class="jops">
						<text class="mini-btn" @click="onShare(job)">📋 复制转发</text>
						<text v-if="!job.applied" class="mini-btn mini-btn-primary" @click="goApply(job)">投递简历</text>
						<status-tag v-else type="success" text="✓ 已投递" />
					</view>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无开放中的家教单，换个筛选条件试试" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { openTutorJobs, tutorJobShareText } from '../../api/index.js';
	import { fmtDate } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TYPE_VALUES = [null, 1, 2];

	export default {
		data() {
			return {
				query: { keyword: '', jobType: null, page: 1, size: 10 },
				typeIdx: 0,
				typeLabels: ['全部类型', '🏠 家教单', '🤝 合作单'],
				list: [],
				total: 0,
				loading: false
			};
		},
		computed: {
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onShow() {
			if (!guardRole('teacher')) return;
			this.reload();
		},
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.query.page + 1); },
		methods: {
			fmtDate,
			buildParams(page) {
				const p = { page, size: this.query.size };
				if (this.query.keyword) p.keyword = this.query.keyword;
				if (this.query.jobType) p.jobType = this.query.jobType;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await openTutorJobs(this.buildParams(page));
					const rows = data.list || data.records || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.query.page = page;
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			onTypePicker(e) {
				this.typeIdx = Number(e.detail.value);
				this.query.jobType = TYPE_VALUES[this.typeIdx];
				this.reload();
			},
			go(url) { uni.navigateTo({ url }); },
			goApply(job) {
				const q = `jobId=${job.id}&needPay=${job.needPay ? 1 : 0}&paid=${job.paid ? 1 : 0}&applied=${job.applied ? 1 : 0}` +
					`&subject=${encodeURIComponent(job.subject || '')}&province=${encodeURIComponent(job.province || '')}&city=${encodeURIComponent(job.city || '')}&salary=${job.salary || ''}`;
				uni.navigateTo({ url: `/pages/teacher-jobs/apply?${q}` });
			},
			async onShare(job) {
				uni.showLoading({ title: '生成中' });
				try {
					const data = await tutorJobShareText(job.id);
					uni.hideLoading();
					const text = (data && data.text) || '';
					if (!text) { uni.showToast({ title: '暂无转发文字', icon: 'none' }); return; }
					uni.setClipboardData({ data: text, success: () => uni.showToast({ title: '已复制，去微信粘贴给同行', icon: 'none' }) });
				} catch (e) { uni.hideLoading(); }
			}
		}
	};
</script>

<style scoped>
	.quick { white-space: nowrap; background: #ffffff; }
	.quick-in { display: inline-flex; gap: 14rpx; padding: 18rpx 24rpx; }
	.qbtn { flex-shrink: 0; padding: 12rpx 24rpx; border-radius: 999rpx; background: #eef2ff; color: #667eea; font-size: 25rpx; }
	.qbtn.warn { background: #fff7e6; color: #d97706; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.jcard { margin-bottom: 20rpx; }
	.jtitle { flex: 1; min-width: 0; }
	.jsubject { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.jsalary { flex-shrink: 0; padding: 6rpx 20rpx; border-radius: 999rpx; background: linear-gradient(135deg, #8b7cf6, #6c5ce7); color: #ffffff; font-weight: 700; font-size: 24rpx; }
	.jloc { margin-top: 8rpx; font-size: 23rpx; }
	.jtags { margin-top: 14rpx; display: flex; gap: 10rpx; flex-wrap: wrap; }
	.jline { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; }
	.jfoot { margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx dashed #eef0f4; display: flex; align-items: center; justify-content: space-between; gap: 12rpx; flex-wrap: wrap; }
	.jfoot-l { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
	.jno { font-family: Consolas, Monaco, monospace; font-size: 22rpx; color: #c0c4cc; }
	.jcount { font-size: 22rpx; }
	.jops { display: flex; align-items: center; gap: 4rpx; }

	.pk { flex-shrink: 0; }
	.pk-in { font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.total-tip { font-size: 22rpx; color: #909399; margin-left: auto; }
</style>
