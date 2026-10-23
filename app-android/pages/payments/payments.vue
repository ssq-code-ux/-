<template>
	<view class="page">
		<view class="sum-bar">
			<view class="sum-cell">
				<text class="sum-label">累计缴费</text>
				<text class="sum-val amount-ok">¥{{ sum.paidTotal || 0 }}</text>
			</view>
			<view class="sum-cell">
				<text class="sum-label">累计退费</text>
				<text class="sum-val amount-danger">¥{{ sum.refundTotal || 0 }}</text>
			</view>
		</view>
		<view class="sum-tip">流水只追加不修改，删单也不会丢</view>

		<search-bar v-model="query.jobId" placeholder="按订单 ID 过滤" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="typeLabels" :value="typeIdx" @change="onTypePicker">
					<view class="pk-in">{{ typeLabels[typeIdx] }} ▾</view>
				</picker>
			</template>
		</search-bar>

		<view class="list">
			<view v-for="row in list" :key="row.id" class="card pcard">
				<view class="row-between">
					<view class="row" style="gap:12rpx;">
						<status-tag :type="row.type === 1 ? 'success' : 'danger'" :text="row.type === 1 ? '缴费' : '退费'" />
						<text class="pamount" :class="row.type === 1 ? 'amount-ok' : 'amount-danger'">
							{{ row.type === 1 ? '+' : '-' }}{{ row.amount }} 元
						</text>
					</view>
					<text class="pno">{{ row.jobNo || '—' }}</text>
				</view>
				<view class="pline">
					<text>老师：{{ row.teacherName || '—' }}</text>
					<text v-if="row.operatorName" class="muted"> · 办理人：{{ row.operatorName }}</text>
				</view>
				<view class="muted pline" v-if="row.note">备注：{{ row.note }}</view>
				<view class="muted ptime">{{ fmtTime(row.createTime) }}</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无流水" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { adminPaymentLogs } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	const TYPE_VALUES = [null, 1, 2];

	export default {
		data() {
			return {
				query: { jobId: '', type: null, page: 1, size: 10 },
				typeIdx: 0,
				typeLabels: ['全部类型', '缴费', '退费'],
				list: [],
				total: 0,
				sum: {},
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
		onShow() { this.reload(); },
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.query.page + 1); },
		methods: {
			fmtTime,
			buildParams(page) {
				const p = { page, size: this.query.size };
				if (this.query.jobId) p.jobId = this.query.jobId;
				if (this.query.type != null) p.type = this.query.type;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await adminPaymentLogs(this.buildParams(page));
					const rows = data.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.sum = { paidTotal: data.paidTotal, refundTotal: data.refundTotal };
					this.query.page = page;
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			onTypePicker(e) {
				this.typeIdx = Number(e.detail.value);
				this.query.type = TYPE_VALUES[this.typeIdx];
				this.reload();
			}
		}
	};
</script>

<style scoped>
	.sum-bar { display: flex; gap: 20rpx; padding: 24rpx 24rpx 0; }
	.sum-cell { flex: 1; background: #fff; border-radius: 18rpx; padding: 24rpx; display: flex; flex-direction: column; }
	.sum-label { font-size: 24rpx; color: #909399; }
	.sum-val { margin-top: 8rpx; font-size: 44rpx; font-weight: 800; }
	.sum-tip { padding: 12rpx 24rpx 0; font-size: 22rpx; color: #a0a4ab; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.pcard { margin-bottom: 20rpx; }
	.pamount { font-size: 32rpx; font-weight: 700; }
	.pno { font-family: Consolas, Monaco, monospace; font-size: 24rpx; color: #a0a4ab; }
	.pline { margin-top: 12rpx; font-size: 26rpx; color: #4b5563; }
	.ptime { margin-top: 10rpx; font-size: 22rpx; }

	.pk { flex-shrink: 0; }
	.pk-in { font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
</style>
