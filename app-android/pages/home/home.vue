<template>
	<view class="page">
		<!-- 渐变头：欢迎 + 退出 -->
		<view class="grad-header home-head">
			<view class="head-top">
				<view>
					<view class="head-hi">你好，{{ realName }}</view>
					<view class="head-role">管理员 · 英语家教管理端</view>
				</view>
				<view class="head-acts">
					<view class="head-logout" hover-class="head-logout-hover" @click="goChangePwd">改密码</view>
					<view class="head-logout" hover-class="head-logout-hover" @click="onLogout">退出</view>
				</view>
			</view>

			<!-- 待办概览条：一眼看到需要处理的环节 -->
			<view class="todo-strip">
				<view class="todo-cell" @click="go('/pages/demands/demands')">
					<text class="todo-num" :class="{ hot: todo.pendingDemands > 0 }">{{ todo.pendingDemands || 0 }}</text>
					<text class="todo-label">待发布需求</text>
				</view>
				<view class="todo-cell" @click="go('/pages/jobs/jobs')">
					<text class="todo-num" :class="{ hot: todo.pendingApplications > 0 }">{{ todo.pendingApplications || 0 }}</text>
					<text class="todo-label">待审核投递</text>
				</view>
				<view class="todo-cell" @click="go('/pages/refunds/refunds')">
					<text class="todo-num" :class="{ hot: todo.trialFailed > 0 }">{{ todo.trialFailed || 0 }}</text>
					<text class="todo-label">待退费</text>
				</view>
				<view class="todo-cell">
					<text class="todo-num">¥{{ todo.totalIncome || 0 }}</text>
					<text class="todo-label">缴费收入</text>
				</view>
			</view>
		</view>

		<!-- 九宫格菜单 -->
		<view class="card menu-card">
			<view class="card-title">管理功能</view>
			<view class="menu-grid">
				<view v-for="m in menus" :key="m.key" class="menu-item" hover-class="menu-item-hover" @click="go(m.url)">
					<view class="menu-icon-wrap">
						<text class="menu-icon">{{ m.icon }}</text>
						<text v-if="badge(m.key)" class="menu-badge">{{ badge(m.key) }}</text>
					</view>
					<text class="menu-label">{{ m.label }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { adminTutorTodo, logoutApi } from '../../api/index.js';
	import { getUser, clearUser } from '../../utils/auth.js';

	export default {
		data() {
			return {
				realName: '管理员',
				todo: {},
				menus: [
					{ key: 'dashboard', icon: '📊', label: '数据看板', url: '/pages/dashboard/dashboard' },
					{ key: 'users', icon: '👥', label: '用户管理', url: '/pages/users/users' },
					{ key: 'jobs', icon: '📌', label: '家教信息', url: '/pages/jobs/jobs?type=1' },
					{ key: 'partner', icon: '🤝', label: '同行合作单', url: '/pages/jobs/jobs?type=2' },
					{ key: 'demands', icon: '📋', label: '家长需求', url: '/pages/demands/demands' },
					{ key: 'refunds', icon: '💸', label: '退费工单', url: '/pages/refunds/refunds' },
					{ key: 'payments', icon: '🧾', label: '缴费流水', url: '/pages/payments/payments' },
					{ key: 'templates', icon: '📑', label: '科目模板', url: '/pages/templates/templates' },
					{ key: 'config', icon: '⚙️', label: '接单配置', url: '/pages/config/config' }
				]
			};
		},
		onShow() {
			const user = getUser();
			if (!user || !user.token || user.role !== 'admin') {
				uni.reLaunch({ url: '/pages/login/login' });
				return;
			}
			this.realName = user.realName || user.username || '管理员';
			this.loadTodo();
		},
		onPullDownRefresh() {
			this.loadTodo().finally(() => uni.stopPullDownRefresh());
		},
		methods: {
			async loadTodo() {
				try {
					this.todo = await adminTutorTodo();
				} catch (e) { /* 拦截器已提示 */ }
			},
			/* Tab 右上角红点：只提示"需要管理员动手"的环节 */
			badge(key) {
				const t = this.todo || {};
				let n = 0;
				if (key === 'jobs' || key === 'partner') n = t.pendingApplications || 0;
				else if (key === 'demands') n = (t.pendingDemands || 0) + (t.deleteRequests || 0);
				else if (key === 'refunds') n = t.trialFailed || 0;
				if (!n) return '';
				return n > 99 ? '99+' : String(n);
			},
			go(url) {
				uni.navigateTo({
					url,
					fail: () => uni.showToast({ title: '该模块即将上线', icon: 'none' })
				});
			},
			goChangePwd() { uni.navigateTo({ url: '/pages/change-password/change-password' }); },
			onLogout() {
				uni.showModal({
					title: '退出登录',
					content: '确定要退出当前管理员账号吗？',
					success: async (r) => {
						if (!r.confirm) return;
						const user = getUser();
						try { if (user && user.refreshToken) await logoutApi(user.refreshToken); } catch (e) { /* 忽略 */ }
						clearUser();
						uni.reLaunch({ url: '/pages/login/login' });
					}
				});
			}
		}
	}
</script>

<style scoped>
	.home-head {
		padding: 32rpx 32rpx 40rpx;
		border-bottom-left-radius: 36rpx;
		border-bottom-right-radius: 36rpx;
	}
	.head-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.head-hi { font-size: 38rpx; font-weight: 700; color: #ffffff; }
	.head-role { margin-top: 8rpx; font-size: 24rpx; color: rgba(255, 255, 255, 0.8); }
	.head-logout {
		padding: 10rpx 28rpx;
		font-size: 24rpx;
		color: #ffffff;
		border-radius: 30rpx;
		background: rgba(255, 255, 255, 0.18);
	}
	.head-logout-hover { background: rgba(255, 255, 255, 0.3); }
	.head-acts { display: flex; gap: 12rpx; align-items: center; }

	.todo-strip {
		display: flex;
		margin-top: 32rpx;
		padding: 24rpx 0;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.16);
	}
	.todo-cell {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.todo-num { font-size: 34rpx; font-weight: 700; color: #ffffff; }
	.todo-num.hot { color: #ffe08a; }
	.todo-label { margin-top: 6rpx; font-size: 20rpx; color: rgba(255, 255, 255, 0.82); }

	.menu-card { margin-top: -20rpx; }
	.menu-grid {
		display: flex;
		flex-wrap: wrap;
	}
	.menu-item {
		width: 33.33%;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 24rpx 0;
		box-sizing: border-box;
	}
	.menu-item-hover { opacity: 0.7; }
	.menu-icon-wrap { position: relative; }
	.menu-icon {
		display: block;
		width: 92rpx;
		height: 92rpx;
		line-height: 92rpx;
		text-align: center;
		font-size: 44rpx;
		border-radius: 26rpx;
		background: linear-gradient(135deg, rgba(102, 126, 234, 0.12), rgba(56, 189, 248, 0.1));
	}
	.menu-badge {
		position: absolute;
		top: -8rpx;
		right: -12rpx;
		min-width: 32rpx;
		height: 32rpx;
		line-height: 32rpx;
		padding: 0 8rpx;
		text-align: center;
		font-size: 20rpx;
		color: #ffffff;
		border-radius: 16rpx;
		background: #e11d48;
		box-sizing: border-box;
	}
	.menu-label { margin-top: 14rpx; font-size: 24rpx; color: #4b5563; }
</style>
