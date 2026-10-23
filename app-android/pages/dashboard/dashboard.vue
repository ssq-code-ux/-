<template>
	<view class="page">
		<!-- 用户总量概览 -->
		<view class="stat-row">
			<view class="stat-card grad-a">
				<view class="stat-value">{{ dash.totalUsers != null ? dash.totalUsers : '-' }}</view>
				<view class="stat-label">注册用户</view>
				<view class="stat-sub">
					老师 {{ rc.teacher || 0 }} · 学生 {{ rc.student || 0 }} · 家长 {{ rc.parent || 0 }}
				</view>
			</view>
			<view class="stat-card" :class="dash.bannedUsers > 0 ? 'grad-warn' : 'grad-b'">
				<view class="stat-value">{{ dash.bannedUsers || 0 }}</view>
				<view class="stat-label">封禁中账号</view>
				<view class="stat-sub">站内通知 {{ dash.totalNotifications || 0 }} 条</view>
			</view>
		</view>

		<!-- 家教业务概览 -->
		<view class="card">
			<view class="card-title">
				<text>🏫 家教业务概览</text>
				<text class="refresh" @click="loadAll">刷新</text>
			</view>
			<view class="biz-grid">
				<view class="biz-cell" :class="{ warn: todo.pendingDemands > 0 }" @click="go('/pages/demands/demands')">
					<text class="biz-num">{{ todo.pendingDemands || 0 }}</text>
					<text class="biz-label">待发布家长需求</text>
					<text class="biz-sub">已发布 {{ todo.publishedDemands || 0 }} 条</text>
				</view>
				<view class="biz-cell" :class="{ warn: todo.deleteRequests > 0 }" @click="go('/pages/demands/demands')">
					<text class="biz-num">{{ todo.deleteRequests || 0 }}</text>
					<text class="biz-label">删除申请待确认</text>
					<text class="biz-sub">家长提交，需处理</text>
				</view>
				<view class="biz-cell" :class="{ warn: todo.pendingApplications > 0 }" @click="go('/pages/jobs/jobs')">
					<text class="biz-num">{{ todo.pendingApplications || 0 }}</text>
					<text class="biz-label">待审核教师投递</text>
					<text class="biz-sub">开放中家教单 {{ todo.openJobs || 0 }} 个</text>
				</view>
				<view class="biz-cell" :class="{ warn: todo.trialInProgress > 0 }">
					<text class="biz-num">{{ todo.trialInProgress || 0 }}</text>
					<text class="biz-label">试课中订单</text>
					<text class="biz-sub">已完成 {{ todo.trialSuccess || 0 }} 个</text>
				</view>
				<view class="biz-cell" :class="{ warn: todo.trialFailed > 0 }" @click="go('/pages/refunds/refunds')">
					<text class="biz-num">{{ todo.trialFailed || 0 }}</text>
					<text class="biz-label">试课失败待退费</text>
					<text class="biz-sub">已下架家教单 {{ todo.closedJobs || 0 }} 个</text>
				</view>
				<view class="biz-cell income" @click="go('/pages/payments/payments')">
					<text class="biz-num">¥{{ todo.totalIncome || 0 }}</text>
					<text class="biz-label">缴费收入（未退费）</text>
					<text class="biz-sub">{{ todo.paidTeachers || 0 }} 位老师已缴费</text>
				</view>
			</view>
		</view>

		<!-- 最近 7 天注册趋势（纯 CSS 柱状图） -->
		<view class="card">
			<view class="card-title">📈 最近 7 天注册趋势</view>
			<view v-if="trend.length" class="trend">
				<view v-for="p in trend" :key="p.date" class="trend-col">
					<text class="trend-count">{{ p.count }}</text>
					<view class="trend-bar" :style="{ height: barHeight(p.count) + 'rpx' }" />
					<text class="trend-date">{{ shortDate(p.date) }}</text>
				</view>
			</view>
			<empty-state v-else text="暂无趋势数据" icon="📈" />
		</view>
	</view>
</template>

<script>
	import { adminDashboard, adminTutorTodo } from '../../api/index.js';

	export default {
		data() {
			return { dash: {}, todo: {} };
		},
		computed: {
			rc() { return this.dash.roleCount || {}; },
			trend() { return this.dash.registerTrend || []; }
		},
		onShow() { this.loadAll(); },
		onPullDownRefresh() { this.loadAll().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			async loadAll() {
				try {
					const [d, t] = await Promise.all([adminDashboard(), adminTutorTodo()]);
					this.dash = d || {};
					this.todo = t || {};
				} catch (e) { /* 拦截器已提示 */ }
			},
			/* 按 7 天最大值等比放大，最高 200rpx */
			barHeight(count) {
				const max = Math.max(1, ...this.trend.map((p) => p.count || 0));
				return Math.max(8, Math.round(((count || 0) / max) * 200));
			},
			/* 后端可能返回 'YYYY-MM-DD'，只展示月-日 */
			shortDate(d) {
				const s = String(d || '');
				return s.length >= 10 ? s.slice(5) : s;
			},
			go(url) {
				uni.navigateTo({ url, fail: () => uni.showToast({ title: '该模块即将上线', icon: 'none' }) });
			}
		}
	}
</script>

<style scoped>
	.stat-row {
		display: flex;
		gap: 20rpx;
		padding: 24rpx 24rpx 0;
	}
	.stat-card {
		flex: 1;
		border-radius: 20rpx;
		padding: 28rpx 24rpx;
		color: #ffffff;
		box-shadow: 0 8rpx 24rpx rgba(31, 45, 82, 0.12);
	}
	.grad-a { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
	.grad-b { background: linear-gradient(135deg, #38bdf8 0%, #667eea 100%); }
	.grad-warn { background: linear-gradient(135deg, #f97316 0%, #e11d48 100%); }
	.stat-value { font-size: 56rpx; font-weight: 800; line-height: 1.1; }
	.stat-label { margin-top: 8rpx; font-size: 26rpx; opacity: 0.95; }
	.stat-sub { margin-top: 10rpx; font-size: 20rpx; opacity: 0.8; }

	.refresh { font-size: 24rpx; color: #667eea; font-weight: 400; }

	.biz-grid { display: flex; flex-wrap: wrap; }
	.biz-cell {
		width: 50%;
		box-sizing: border-box;
		padding: 18rpx 12rpx;
		display: flex;
		flex-direction: column;
		border-radius: 16rpx;
	}
	.biz-cell.warn .biz-num { color: #e11d48; }
	.biz-cell.income .biz-num { color: #16a34a; }
	.biz-num { font-size: 44rpx; font-weight: 800; color: #1f2d52; }
	.biz-label { margin-top: 6rpx; font-size: 24rpx; color: #4b5563; }
	.biz-sub { margin-top: 4rpx; font-size: 20rpx; color: #a0a4ab; }

	.trend {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		height: 300rpx;
		padding-top: 20rpx;
	}
	.trend-col {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: flex-end;
		height: 100%;
	}
	.trend-count { font-size: 20rpx; color: #6b7280; margin-bottom: 6rpx; }
	.trend-bar {
		width: 34rpx;
		border-radius: 8rpx 8rpx 0 0;
		background: linear-gradient(180deg, #667eea 0%, #38bdf8 100%);
	}
	.trend-date { font-size: 18rpx; color: #a0a4ab; margin-top: 8rpx; }
</style>
