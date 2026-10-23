<template>
	<view class="page">
		<!-- 待办概览：点任意一项即按该状态筛选，再点一次取消 -->
		<scroll-view scroll-x class="chips" :show-scrollbar="false">
			<view class="chips-in">
				<view v-for="c in chips" :key="c.v" class="chip" :class="{ on: view === c.v, hot: c.hot && c.num > 0 }" @click="toggleChip(c.v)">
					<text class="chip-num">{{ c.num }}</text>
					<text class="chip-label">{{ c.label }}</text>
				</view>
				<view class="chip income">
					<text class="chip-num">¥{{ todo.totalIncome || 0 }}</text>
					<text class="chip-label">缴费收入({{ todo.paidTeachers || 0 }})</text>
				</view>
			</view>
		</scroll-view>

		<search-bar v-model="keyword" placeholder="搜索科目 / 城市 / 编号" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="viewLabels" :value="viewIdx" @change="onViewPicker">
					<view class="pk-in">{{ viewLabels[viewIdx] }} ▾</view>
				</picker>
			</template>
		</search-bar>

		<view class="act-row">
			<view class="btn btn-primary act-btn" @click="goPublish">➕ 发布{{ typeName }}</view>
			<view class="btn btn-plain act-btn" @click="goBatch">⚡ 批量发布</view>
		</view>

		<view class="list">
			<view v-for="row in list" :key="row.id" class="card jcard">
				<view class="row-between">
					<view class="row jtitle" style="flex-wrap:wrap;">
						<text class="jsubject">{{ row.subject || '—' }}</text>
						<text class="jmono">{{ row.jobNo }}</text>
					</view>
					<view class="row">
						<status-tag :type="jobStatusTag(row)" :text="jobStatusText(row)" />
						<text v-if="row.refundPending" class="refund-tag" @click="goRefund(row)">待退费</text>
					</view>
				</view>

				<view class="jmeta">
					<text>📍 {{ region(row) }}</text>
					<text v-if="row.grade">· {{ row.grade }}</text>
					<text v-if="row.salary" class="salary">· {{ row.salary }} 元/h</text>
				</view>

				<view class="row jfoot">
					<text v-if="row.payToApply === 1" class="pay-tag">💰 需缴费</text>
					<text v-else class="muted">免费投递</text>
					<text class="apply-badge">投递 {{ row.applyCount || 0 }}</text>
					<text class="muted jtime">{{ fmtTime(row.createTime) }}</text>
				</view>

				<view class="ops">
					<text class="mini-btn" @click="goDetail(row)">详情</text>
					<text class="mini-btn mini-btn-primary" @click="goApps(row)">投递({{ row.applyCount || 0 }})</text>
					<text v-if="row.status === 1" class="mini-btn mini-btn-primary" @click="goEdit(row)">编辑</text>
					<text v-if="row.status === 1" class="mini-btn mini-btn-danger" @click="closeJob(row)">下架</text>
					<text v-else class="mini-btn mini-btn-success" @click="reopenJob(row)">重开</text>
					<text class="mini-btn mini-btn-danger" @click="deleteJob(row)">删除</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" :text="'暂无' + typeName + '，点上方发布'" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import {
		adminTutorJobs, adminTutorTodo, adminCloseTutorJob,
		adminReopenTutorJob, adminDeleteTutorJob
	} from '../../api/index.js';
	import { jobStatusText, jobStatusTag, fmtTime } from '../../utils/format.js';

	const VIEW_VALUES = ['', 'open', 'closed', 'trial'];

	export default {
		data() {
			return {
				type: 1,
				keyword: '',
				view: '',
				viewIdx: 0,
				viewLabels: ['全部状态', '开放中', '已关闭', '试课中'],
				list: [],
				total: 0,
				page: 1,
				size: 10,
				loading: false,
				todo: {}
			};
		},
		computed: {
			typeName() { return this.type === 2 ? '合作单' : '家教单'; },
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			},
			chips() {
				const t = this.todo || {};
				return [
					{ v: 'pendingApply', num: t.pendingApplications || 0, label: '待审核投递', hot: true },
					{ v: 'refund', num: t.trialFailed || 0, label: '试课失败待退费', hot: true },
					{ v: 'trial', num: t.trialInProgress || 0, label: '试课中' },
					{ v: 'done', num: t.trialSuccess || 0, label: '已完成' },
					{ v: 'open', num: t.openJobs || 0, label: '开放中' },
					{ v: '', num: '全部', label: '清除筛选' }
				];
			}
		},
		onLoad(opt) {
			this.type = Number(opt.type) === 2 ? 2 : 1;
			if (opt.keyword) this.keyword = decodeURIComponent(opt.keyword);
			uni.setNavigationBarTitle({ title: this.type === 2 ? '同行合作单' : '家教信息' });
		},
		onShow() {
			this.loadTodo();
			this.reload();
		},
		onPullDownRefresh() {
			Promise.all([this.loadTodo(), this.reload()]).finally(() => uni.stopPullDownRefresh());
		},
		onReachBottom() {
			if (!this.loading && this.list.length < this.total) this.load(this.page + 1);
		},
		methods: {
			jobStatusText, jobStatusTag, fmtTime,
			region(row) {
				const s = (row.province || '') + (row.city ? ' - ' + row.city : '');
				return s || '地点未填';
			},
			async loadTodo() {
				try { this.todo = await adminTutorTodo(); } catch (e) { /* 已提示 */ }
			},
			buildParams(page) {
				const p = { keyword: this.keyword, page, size: this.size, jobType: this.type };
				if (this.view) p.view = this.view;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await adminTutorJobs(this.buildParams(page));
					const rows = data.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.page = page;
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			onViewPicker(e) {
				this.viewIdx = Number(e.detail.value);
				this.view = VIEW_VALUES[this.viewIdx];
				this.reload();
			},
			toggleChip(v) {
				this.view = this.view === v ? '' : v;
				// 概览筛选与下拉互斥：概览选中时把下拉复位到"全部状态"
				this.viewIdx = VIEW_VALUES.indexOf(this.view);
				if (this.viewIdx < 0) this.viewIdx = 0;
				this.reload();
			},
			goPublish() { uni.navigateTo({ url: `/pages/job-form/job-form?type=${this.type}` }); },
			goBatch() { uni.navigateTo({ url: `/pages/batch/batch?type=${this.type}` }); },
			goDetail(row) { uni.navigateTo({ url: `/pages/job-detail/job-detail?id=${row.id}` }); },
			goApps(row) { uni.navigateTo({ url: `/pages/applications/applications?jobId=${row.id}` }); },
			goEdit(row) { uni.navigateTo({ url: `/pages/job-form/job-form?id=${row.id}&type=${this.type}` }); },
			goRefund(row) {
				uni.navigateTo({ url: `/pages/refunds/refunds?jobNo=${row.jobNo}`, fail: () => uni.showToast({ title: '退费工单即将上线', icon: 'none' }) });
			},
			closeJob(row) {
				uni.showModal({
					title: '下架' + this.typeName,
					content: `确定下架「${row.jobNo}（${row.subject}）」吗？下架后教师端不再展示。`,
					success: async (r) => {
						if (!r.confirm) return;
						try { await adminCloseTutorJob(row.id); uni.showToast({ title: '已下架', icon: 'success' }); this.reload(); this.loadTodo(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			reopenJob(row) {
				uni.showModal({
					title: '重新上架',
					content: `确定重新上架「${row.jobNo}（${row.subject}）」吗？旧的通过记录将被清空，单子重新接受投递。`,
					success: async (r) => {
						if (!r.confirm) return;
						try { await adminReopenTutorJob(row.id); uni.showToast({ title: '已重新上架', icon: 'success' }); this.reload(); this.loadTodo(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			deleteJob(row) {
				const appCount = row.applyCount || 0;
				uni.showModal({
					title: '删除' + this.typeName,
					content: `确定删除「${row.jobNo}（${row.subject}）」吗？将连带删除 ${appCount} 条投递记录与全部缴费记录，不可恢复。`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await adminDeleteTutorJob(row.id); uni.showToast({ title: '已删除', icon: 'success' }); this.reload(); this.loadTodo(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.chips { white-space: nowrap; background: #ffffff; }
	.chips-in { display: inline-flex; gap: 16rpx; padding: 20rpx 24rpx; }
	.chip {
		flex-shrink: 0;
		min-width: 130rpx;
		padding: 12rpx 20rpx;
		border-radius: 16rpx;
		background: #f2f4f8;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.chip.on { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
	.chip.on .chip-num, .chip.on .chip-label { color: #ffffff; }
	.chip-num { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.chip.hot .chip-num { color: #e11d48; }
	.chip.income .chip-num { color: #16a34a; }
	.chip-label { margin-top: 4rpx; font-size: 20rpx; color: #6b7280; }

	.act-row { display: flex; gap: 16rpx; padding: 4rpx 24rpx 0; }
	.act-btn { flex: 1; text-align: center; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.jcard { margin-bottom: 20rpx; }
	.jtitle { gap: 12rpx; }
	.jsubject { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.jmono { font-family: Consolas, Monaco, monospace; font-size: 22rpx; color: #a0a4ab; }
	.refund-tag {
		margin-left: 10rpx;
		font-size: 20rpx;
		color: #ffffff;
		background: #e11d48;
		border-radius: 8rpx;
		padding: 2rpx 10rpx;
	}
	.jmeta { margin-top: 14rpx; font-size: 26rpx; color: #4b5563; display: flex; flex-wrap: wrap; gap: 10rpx; }
	.jmeta .salary { color: #e11d48; font-weight: 600; }
	.jfoot { margin-top: 14rpx; gap: 16rpx; align-items: center; flex-wrap: wrap; }
	.pay-tag { font-size: 22rpx; color: #e11d48; background: rgba(225, 29, 72, 0.08); border-radius: 8rpx; padding: 2rpx 12rpx; }
	.apply-badge { font-size: 22rpx; color: #667eea; background: rgba(102, 126, 234, 0.1); border-radius: 8rpx; padding: 2rpx 12rpx; }
	.jtime { font-size: 22rpx; margin-left: auto; }
	.ops { margin-top: 18rpx; display: flex; gap: 14rpx; flex-wrap: wrap; }

	.pk { flex-shrink: 0; }
	.pk-in { font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
</style>
