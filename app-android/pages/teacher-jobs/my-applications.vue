<template>
	<view class="page">
		<!-- 退费工单 -->
		<view class="card refund-block" v-if="refundTasks.length">
			<view class="card-title">💸 我的退费<text class="card-sub">{{ refundPending }} 笔待处理</text></view>
			<view v-for="t in refundTasks" :key="t.id" class="refund-item">
				<view class="row-between">
					<view class="row" style="gap:10rpx;flex-wrap:wrap;">
						<text class="rsubject">{{ t.subject || '家教单' }}</text>
						<status-tag :type="t.status === 1 ? 'success' : 'danger'" :text="t.status === 1 ? '已退费' : '待退费'" />
					</view>
				</view>
				<view class="muted rline">编号 {{ t.jobNo || '—' }}</view>
				<view class="muted rline">
					<text v-if="Number(t.amount) > 0">应退 {{ t.amount }} 元</text>
					<text v-else>免费单，无需退款</text>
					<text v-if="t.refundTime"> · {{ fmtDate(t.refundTime) }} 已办结</text>
					<text v-else> · {{ fmtDate(t.createTime) }} 登记</text>
				</view>
				<view v-if="t.reason" class="muted rline">原因：{{ t.reason }}</view>
				<view v-if="t.note" class="muted rline">管理端备注：{{ t.note }}</view>
			</view>
		</view>

		<!-- 投递记录 -->
		<view class="list">
			<view v-for="a in myApps" :key="a.id" class="card acard">
				<view class="row-between">
					<text class="asubject">{{ a.subject || '家教单' }}</text>
					<view class="row" style="gap:8rpx;flex-wrap:wrap;">
						<status-tag :type="appStatusTag(a.status)" :text="appStatusText(a.status)" />
						<status-tag v-if="a.status === 1 && a.trialStatus != null" :type="trialTag(a.trialStatus)" :text="trialText(a.trialStatus)" />
					</view>
				</view>
				<view class="muted aline">编号 {{ a.jobNo }}<text v-if="a.city"> · {{ a.province }} - {{ a.city }}</text><text v-if="a.salary"> · {{ a.salary }}元/h</text></view>
				<view class="muted aline">{{ fmtDate(a.createTime) }} 投递<text v-if="a.reviewTime"> · {{ fmtDate(a.reviewTime) }} 审核</text></view>
				<view v-if="a.message" class="amsg">附言：{{ a.message }}</view>

				<!-- 简历通过：家长联系方式 -->
				<view v-if="a.status === 1 && a.parentContact" class="contact">
					<view class="contact-title">📞 家长联系方式（简历已通过）</view>
					<view class="contact-line">
						{{ a.parentContact.contactName }} · <text class="tel-link" @click="callPhone(a.parentContact.phone)">{{ a.parentContact.phone }}</text>
						<text v-if="a.parentContact.wechat"> · 微信：{{ a.parentContact.wechat }}</text>
					</view>
					<view v-if="a.parentContact.grade || a.parentContact.studentLevel" class="contact-line muted">
						<text v-if="a.parentContact.grade">{{ a.parentContact.grade }}</text>
						<text v-if="a.parentContact.studentLevel"> · {{ a.parentContact.studentLevel }}</text>
					</view>
					<view class="contact-line muted">请尽快与家长沟通对接上课事宜</view>
				</view>

				<!-- 试课阶段 -->
				<view v-if="a.status === 1 && a.trialStatus === 0" class="trial-ops">
					<text class="muted trial-hint">试课结束后请确认结果：</text>
					<view class="trial-btns">
						<view class="btn btn-primary tbtn" @click="onTrial(a, true)">✓ 试课通过</view>
						<view class="btn btn-danger tbtn" @click="onTrial(a, false)">✗ 试课失败，退费</view>
					</view>
				</view>
				<view v-else-if="a.status === 1 && a.trialStatus === 1" class="trial-tip ok">🎉 试课通过，订单已完成</view>
				<view v-else-if="a.status === 1 && a.trialStatus === 2" class="trial-tip bad">试课失败已反馈管理端，等待退费处理</view>
				<view v-else-if="a.status === 1 && a.trialStatus === 3" class="trial-tip">退费已完成</view>
			</view>

			<empty-state v-if="!loading && !myApps.length" text="还没有投递记录" />
		</view>
	</view>
</template>

<script>
	import { myTutorJobApplications, myRefundTasks, submitTrialResult } from '../../api/index.js';
	import { fmtDate } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const APP_STATUS_TEXT = { 0: '待审核', 1: '已通过', 2: '未通过' };
	const APP_STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'danger' };
	const TRIAL_TEXT = { 0: '试课中', 1: '订单已完成', 2: '试课失败待退费', 3: '已退费' };
	const TRIAL_TAG = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' };

	export default {
		data() {
			return {
				myApps: [], refundTasks: [], refundPending: 0, loading: false
			};
		},
		onShow() {
			if (!guardRole('teacher')) return;
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtDate,
			appStatusText(s) { return APP_STATUS_TEXT[s] != null ? APP_STATUS_TEXT[s] : s; },
			appStatusTag(s) { return APP_STATUS_TAG[s] || 'info'; },
			trialText(s) { return TRIAL_TEXT[s] || '—'; },
			trialTag(s) { return TRIAL_TAG[s] || 'info'; },
			async load() {
				this.loading = true;
				try {
					const [apps, refunds] = await Promise.all([
						myTutorJobApplications({ page: 1, size: 50 }).catch(() => ({ list: [] })),
						myRefundTasks().catch(() => ({ list: [], pending: 0 }))
					]);
					this.myApps = apps.list || apps.records || [];
					this.refundTasks = refunds.list || [];
					this.refundPending = refunds.pending || 0;
				} finally {
					this.loading = false;
				}
			},
			callPhone(p) { if (p) uni.makePhoneCall({ phoneNumber: String(p), fail: () => {} }); },
			onTrial(a, success) {
				uni.showModal({
					title: success ? '试课通过' : '试课失败，申请退费',
					content: success ? '确认试课已通过吗？确认后该订单将标记为完成。' : '确认试课失败吗？提交后将通知管理端为您办理退费，请耐心等待处理。',
					confirmColor: success ? '#16a34a' : '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await submitTrialResult(a.id, success);
							uni.showToast({ title: success ? '已确认试课通过' : '已反馈，等待退费', icon: 'success' });
							this.load();
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.refund-block { border: 1rpx solid #f3d19e; background: #fffdf7; }
	.refund-item { padding: 16rpx 0; border-bottom: 1rpx dashed #f0e0c0; }
	.refund-item:last-child { border-bottom: none; }
	.rsubject { font-size: 28rpx; font-weight: 700; color: #1f2d52; }
	.rline { margin-top: 6rpx; font-size: 24rpx; line-height: 1.5; }

	.list { padding: 0 0 40rpx; }
	.acard { margin-bottom: 20rpx; }
	.asubject { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.aline { margin-top: 8rpx; font-size: 24rpx; line-height: 1.6; }
	.amsg { margin-top: 10rpx; font-size: 24rpx; color: #6b7280; background: #f5f7fa; border-radius: 10rpx; padding: 14rpx 16rpx; line-height: 1.6; }

	.contact { margin-top: 14rpx; border: 1rpx solid #f3d19e; background: #fdf6ec; border-radius: 12rpx; padding: 16rpx 18rpx; }
	.contact-title { font-size: 25rpx; font-weight: 600; color: #b88230; margin-bottom: 6rpx; }
	.contact-line { font-size: 26rpx; color: #303133; line-height: 1.8; word-break: break-all; }
	.contact-line.muted { color: #a0a4ab; font-size: 24rpx; }

	.trial-ops { margin-top: 14rpx; }
	.trial-hint { font-size: 24rpx; }
	.trial-btns { display: flex; gap: 16rpx; margin-top: 12rpx; }
	.tbtn { flex: 1; height: 72rpx; line-height: 72rpx; font-size: 26rpx; text-align: center; }
	.trial-tip { margin-top: 14rpx; font-size: 25rpx; color: #6b7280; background: #f5f7fa; border-radius: 10rpx; padding: 14rpx 18rpx; }
	.trial-tip.ok { color: #529b2e; background: #f0f9eb; }
	.trial-tip.bad { color: #c45656; background: #fef0f0; }
</style>
