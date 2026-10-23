<template>
	<view class="page">
		<view class="sum-bar">
			<text class="sum-txt">老师反馈试课失败后，订单已自动重开、投递已清空，退费统一在这里办理</text>
			<text class="sum-pending" :class="{ hot: pending > 0 }">待退费 {{ pending }}</text>
		</view>

		<search-bar v-model="query.keyword" placeholder="搜订单号 / 老师姓名 / 电话" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="statusLabels" :value="statusIdx" @change="onStatusPicker">
					<view class="pk-in">{{ statusLabels[statusIdx] }} ▾</view>
				</picker>
				<view class="only-btn" :class="{ on: query.status === 0 }" @click="onlyPending">只看待退费</view>
			</template>
		</search-bar>

		<view class="list">
			<view v-for="row in list" :key="row.id" class="card rcard" :class="{ 'row-pending': row.status === 0 }">
				<view class="row-between">
					<view class="row" style="flex-wrap:wrap;gap:10rpx;">
						<text class="rno">{{ row.jobNo || '—' }}</text>
						<status-tag :type="row.status === 1 ? 'success' : 'danger'" :text="row.status === 1 ? '已办结' : '待退费'" />
					</view>
					<text v-if="Number(row.amount) > 0" class="amount-danger">{{ row.amount }} 元</text>
					<text v-else class="muted">免费单</text>
				</view>

				<view class="rteacher">
					<text class="rname">{{ row.teacherName || '—' }}</text>
					<text v-if="row.teacherPhone" class="tel-link" @click="callPhone(row.teacherPhone)">📞 {{ row.teacherPhone }}</text>
					<text v-else class="muted">📞 —</text>
				</view>

				<view class="muted rreason" v-if="row.reason">退费原因：{{ row.reason }}</view>
				<view class="muted rreason" v-if="row.note">备注：{{ row.note }}</view>
				<view class="muted rtime">登记 {{ fmtTime(row.createTime) }}<text v-if="row.refundTime"> · 办结 {{ fmtTime(row.refundTime) }}</text></view>

				<view class="ops">
					<text v-if="row.status !== 1" class="mini-btn mini-btn-danger" @click="onSettle(row)">
						{{ Number(row.amount) > 0 ? '↩️ 办理退费' : '✓ 标记已处理' }}
					</text>
					<text v-if="row.jobNo" class="mini-btn" @click="goJob(row)">看单</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无退费工单" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { adminRefundTasks, adminSettleRefundTask } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	const STATUS_VALUES = [null, 0, 1];

	export default {
		data() {
			return {
				query: { keyword: '', status: null, page: 1, size: 10 },
				statusIdx: 0,
				statusLabels: ['全部状态', '待退费', '已办结'],
				list: [],
				total: 0,
				pending: 0,
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
		onLoad(opt) {
			if (opt && opt.jobNo) this.query.keyword = decodeURIComponent(opt.jobNo);
		},
		onShow() { this.reload(); },
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.query.page + 1); },
		methods: {
			fmtTime,
			buildParams(page) {
				const p = { page, size: this.query.size };
				if (this.query.keyword) p.keyword = this.query.keyword;
				if (this.query.status != null) p.status = this.query.status;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await adminRefundTasks(this.buildParams(page));
					const rows = data.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.pending = data.pending || 0;
					this.query.page = page;
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			onStatusPicker(e) {
				this.statusIdx = Number(e.detail.value);
				this.query.status = STATUS_VALUES[this.statusIdx];
				this.reload();
			},
			onlyPending() {
				const on = this.query.status === 0;
				this.query.status = on ? null : 0;
				this.statusIdx = on ? 0 : 1;
				this.reload();
			},
			callPhone(phone) { if (phone) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} }); },
			goJob(row) { uni.navigateTo({ url: `/pages/jobs/jobs?type=1&keyword=${encodeURIComponent(row.jobNo)}` }); },
			onSettle(row) {
				const teacher = row.teacherName || ('老师#' + row.teacherId);
				const hasMoney = Number(row.amount) > 0;
				uni.showModal({
					title: hasMoney ? '办理退费' : '标记已处理',
					editable: true,
					placeholderText: '备注（选填），如：微信转账已到账',
					content: hasMoney
						? `确认已向「${teacher}」退回 ${row.amount} 元？办结后金额清零，流水永久留档。`
						: `「${teacher}」这单没有缴费金额，确认后即把工单标记为已处理。`,
					success: async (r) => {
						if (!r.confirm) return;
						const note = String(r.content || '').trim() || (hasMoney ? '管理端办理退费' : '免费单，标记已处理');
						try {
							await adminSettleRefundTask(row.id, note);
							uni.showToast({ title: hasMoney ? '退费已办结' : '已标记处理', icon: 'none' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.sum-bar { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 24rpx; background: #fff; }
	.sum-txt { flex: 1; font-size: 22rpx; color: #909399; line-height: 1.5; }
	.sum-pending { flex-shrink: 0; font-size: 26rpx; font-weight: 700; color: #6b7280; }
	.sum-pending.hot { color: #e11d48; }

	.list { padding: 12rpx 24rpx 40rpx; }
	.rcard { margin-bottom: 20rpx; border-left: 6rpx solid transparent; }
	.rcard.row-pending { border-left-color: #f59e0b; background: #fffdf5; }
	.rno { font-family: Consolas, Monaco, monospace; font-size: 26rpx; font-weight: 700; color: #6c5ce7; }
	.rteacher { margin-top: 14rpx; font-size: 26rpx; color: #4b5563; display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; }
	.rname { font-weight: 700; color: #1f2d52; font-size: 28rpx; }
	.rreason { margin-top: 10rpx; font-size: 24rpx; line-height: 1.5; }
	.rtime { margin-top: 10rpx; font-size: 22rpx; }
	.ops { margin-top: 18rpx; display: flex; gap: 14rpx; flex-wrap: wrap; }

	.pk { flex-shrink: 0; }
	.pk-in { font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.only-btn { flex-shrink: 0; font-size: 24rpx; color: #4b5563; background: #eef1f7; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.only-btn.on { background: #fde8ec; color: #e11d48; font-weight: 600; }
</style>
