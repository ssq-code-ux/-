<template>
	<view class="page">
		<!-- 待办概览：点击直接筛选 -->
		<scroll-view scroll-x class="chips" :show-scrollbar="false">
			<view class="chips-in">
				<view class="chip" :class="{ on: query.deleteRequested === 1, hot: todo.deleteRequests > 0 }" @click="filterDelete">
					<text class="chip-num">{{ todo.deleteRequests || 0 }}</text>
					<text class="chip-label">删除申请待确认</text>
				</view>
				<view class="chip" :class="{ on: query.status === 0, hot: todo.pendingDemands > 0 }" @click="filterStatus(0)">
					<text class="chip-num">{{ todo.pendingDemands || 0 }}</text>
					<text class="chip-label">待发布需求</text>
				</view>
				<view class="chip" :class="{ on: query.status === 1 }" @click="filterStatus(1)">
					<text class="chip-num">{{ todo.publishedDemands || 0 }}</text>
					<text class="chip-label">已发布需求</text>
				</view>
				<view class="chip" :class="{ on: !query.status && query.deleteRequested !== 1 }" @click="clearFilter">
					<text class="chip-num">全部</text>
					<text class="chip-label">清除筛选</text>
				</view>
			</view>
		</scroll-view>

		<search-bar v-model="query.keyword" placeholder="搜订单号 / 家长称呼 / 电话 / 微信" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="statusLabels" :value="statusIdx" @change="onStatusPicker">
					<view class="pk-in">{{ statusLabels[statusIdx] }} ▾</view>
				</picker>
			</template>
		</search-bar>

		<view class="list">
			<view v-for="row in list" :key="row.id" class="card dcard" :class="{ 'row-del': row.deleteRequested === 1, 'row-pending': row.status === 0 && row.deleteRequested !== 1 }">
				<view class="row-between">
					<view class="row" style="flex-wrap:wrap;gap:10rpx;">
						<text class="dno">{{ row.demandNo || '—' }}</text>
						<status-tag :type="row.status === 1 ? 'success' : 'warning'" :text="row.status === 1 ? '已发布' : '待发布'" />
						<status-tag v-if="row.deleteRequested === 1" type="danger" text="申请删除" />
					</view>
				</view>

				<view class="dparent">
					<text class="dname">{{ row.contactName || '—' }}</text>
					<text v-if="row.phone" class="tel-link" @click="callPhone(row.phone)">📞 {{ row.phone }}</text>
					<text v-else class="muted">📞 未留号码</text>
					<text v-if="row.wechat" class="muted"> · 💬 {{ row.wechat }}</text>
				</view>

				<view class="dmeta">
					<text>{{ row.subject || '待确认' }}</text>
					<text v-if="row.grade">· {{ row.grade }}</text>
					<text v-if="row.salary" class="salary">· {{ row.salary }} 元/h</text>
				</view>
				<view class="muted dsub" v-if="row.studentLevel">📖 {{ row.studentLevel }}</view>
				<view class="muted dsub" v-if="row.studentGender || row.expectGender">
					👤 {{ row.studentGender || '性别不限' }}<text v-if="row.expectGender"> · 期望{{ row.expectGender }}老师</text>
				</view>
				<view class="muted dsub" v-if="row.otherRequire">✅ {{ row.otherRequire }}</view>

				<view class="djob" v-if="row.jobNo">
					<text class="muted">关联家教单</text>
					<text class="dno sm">{{ row.jobNo }}</text>
					<text class="muted">{{ row.jobStatus || '已关闭' }} · {{ row.applyCount || 0 }} 份投递</text>
				</view>

				<view class="muted dtime">提交于 {{ fmtTime(row.createTime) }}</view>

				<view class="ops">
					<text class="mini-btn" @click="goDetail(row)">详情</text>
					<text v-if="row.status === 0" class="mini-btn mini-btn-success" @click="goPublish(row)">发布家教单</text>
					<text v-if="row.jobNo" class="mini-btn mini-btn-primary" @click="goJob(row)">看单</text>
					<text class="mini-btn mini-btn-danger" @click="onDelete(row)">{{ row.deleteRequested === 1 ? '确认删除' : '删除' }}</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无家长需求" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { adminParentDemands, adminConfirmDeleteDemand, adminTutorTodo } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	const STATUS_VALUES = [null, 0, 1];

	export default {
		data() {
			return {
				query: { keyword: '', status: null, deleteRequested: null, page: 1, size: 10 },
				statusIdx: 0,
				statusLabels: ['全部状态', '待发布', '已发布'],
				list: [],
				total: 0,
				loading: false,
				todo: {}
			};
		},
		computed: {
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onShow() { this.loadTodo(); this.reload(); },
		onPullDownRefresh() { Promise.all([this.loadTodo(), this.reload()]).finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.query.page + 1); },
		methods: {
			fmtTime,
			async loadTodo() { try { this.todo = await adminTutorTodo(); } catch (e) { /* 已提示 */ } },
			buildParams(page) {
				const p = { page, size: this.query.size, status: this.query.status == null ? -1 : this.query.status };
				if (this.query.keyword) p.keyword = this.query.keyword;
				if (this.query.deleteRequested === 1) p.deleteRequested = 1;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await adminParentDemands(this.buildParams(page));
					const rows = data.records || data.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.query.page = page;
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			onStatusPicker(e) {
				this.statusIdx = Number(e.detail.value);
				this.query.status = STATUS_VALUES[this.statusIdx];
				this.query.deleteRequested = null;
				this.reload();
			},
			filterDelete() {
				const same = this.query.deleteRequested === 1;
				this.query.status = null;
				this.statusIdx = 0;
				this.query.deleteRequested = same ? null : 1;
				this.reload();
			},
			filterStatus(s) {
				const same = this.query.status === s && this.query.deleteRequested !== 1;
				this.query.status = same ? null : s;
				this.query.deleteRequested = null;
				this.statusIdx = same ? 0 : STATUS_VALUES.indexOf(s);
				this.reload();
			},
			clearFilter() {
				this.query.status = null;
				this.query.deleteRequested = null;
				this.statusIdx = 0;
				this.reload();
			},
			callPhone(phone) { if (phone) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} }); },
			goDetail(row) { uni.navigateTo({ url: `/pages/demand-detail/demand-detail?id=${row.id}` }); },
			goPublish(row) { uni.navigateTo({ url: `/pages/demand-publish/demand-publish?id=${row.id}` }); },
			goJob(row) { uni.navigateTo({ url: `/pages/jobs/jobs?type=1&keyword=${encodeURIComponent(row.jobNo)}` }); },
			onDelete(row) {
				const requested = row.deleteRequested === 1;
				uni.showModal({
					title: requested ? '确认家长删除申请' : '删除家长需求',
					content: requested
						? `家长「${row.contactName}」已申请删除该需求，确认后需求单${row.jobId ? '及关联家教单（含投递、缴费记录）' : ''}将被彻底删除，不可恢复。`
						: `确定删除该家长需求吗？${row.jobId ? '关联家教单（含投递、缴费记录）将一并删除，' : ''}不可恢复。`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminConfirmDeleteDemand(row.id);
							uni.showToast({ title: '已删除', icon: 'success' });
							this.reload();
							this.loadTodo();
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.chips { white-space: nowrap; background: #ffffff; }
	.chips-in { display: inline-flex; gap: 16rpx; padding: 20rpx 24rpx; }
	.chip { flex-shrink: 0; min-width: 150rpx; padding: 12rpx 20rpx; border-radius: 16rpx; background: #f2f4f8; display: flex; flex-direction: column; align-items: center; }
	.chip.on { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
	.chip.on .chip-num, .chip.on .chip-label { color: #ffffff; }
	.chip-num { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.chip.hot .chip-num { color: #e11d48; }
	.chip-label { margin-top: 4rpx; font-size: 20rpx; color: #6b7280; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.dcard { margin-bottom: 20rpx; border-left: 6rpx solid transparent; }
	.dcard.row-del { border-left-color: #e11d48; background: #fff5f6; }
	.dcard.row-pending { border-left-color: #f59e0b; }
	.dno { font-family: Consolas, Monaco, monospace; font-size: 26rpx; font-weight: 700; color: #6c5ce7; }
	.dno.sm { font-size: 24rpx; }
	.dparent { margin-top: 14rpx; font-size: 26rpx; color: #4b5563; display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; }
	.dname { font-weight: 700; color: #1f2d52; font-size: 28rpx; }
	.dmeta { margin-top: 12rpx; font-size: 26rpx; color: #303133; display: flex; flex-wrap: wrap; gap: 10rpx; }
	.dmeta .salary { color: #e11d48; font-weight: 600; }
	.dsub { margin-top: 8rpx; font-size: 24rpx; line-height: 1.5; }
	.djob { margin-top: 14rpx; padding-top: 14rpx; border-top: 1rpx dashed #eef0f4; font-size: 24rpx; display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; }
	.dtime { margin-top: 12rpx; font-size: 22rpx; }
	.ops { margin-top: 18rpx; display: flex; gap: 14rpx; flex-wrap: wrap; }

	.pk { flex-shrink: 0; }
	.pk-in { font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
</style>
