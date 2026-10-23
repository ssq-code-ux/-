<template>
	<view class="page">
		<view class="head">
			<view class="head-top">
				<view>
					<view class="head-hi">你好，{{ realName }}</view>
					<view class="head-role">教师端 · 智乐学</view>
				</view>
				<view class="head-acts">
					<view class="head-logout" hover-class="head-logout-hover" @click="goChangePwd">改密码</view>
					<view class="head-logout" hover-class="head-logout-hover" @click="onLogout">退出</view>
				</view>
			</view>
		</view>

		<view class="card menu-card">
			<view class="card-title">教学管理</view>
			<view class="menu-grid">
				<view v-for="m in teachMenus" :key="m.key" class="menu-item" hover-class="menu-item-hover" @click="go(m.url)">
					<view class="menu-icon-wrap"><text class="menu-icon">{{ m.icon }}</text></view>
					<text class="menu-label">{{ m.label }}</text>
				</view>
			</view>
		</view>

		<view class="card menu-card">
			<view class="card-title">家教接单</view>
			<view class="menu-grid">
				<view v-for="m in jobMenus" :key="m.key" class="menu-item" hover-class="menu-item-hover" @click="go(m.url)">
					<view class="menu-icon-wrap"><text class="menu-icon">{{ m.icon }}</text></view>
					<text class="menu-label">{{ m.label }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { logoutApi } from '../../api/index.js';
	import { getUser, clearUser } from '../../utils/auth.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				realName: '老师',
				teachMenus: [
					{ key: 'students', icon: '👥', label: '学生管理', url: '/pages/teacher/students' },
					{ key: 'homework', icon: '📝', label: '作业管理', url: '/pages/teacher/homework' },
					{ key: 'sheet', icon: '✨', label: 'AI 出卷', url: '/pages/teacher/answer-sheet-form' },
{ key: 'vocabulary', icon: '📚', label: '词汇管理', url: '/pages/teacher/vocabulary' },
						{ key: 'words', icon: '🎯', label: '背单词', url: '/pages/teacher/words' },
						{ key: 'resources', icon: '📁', label: '学习资料', url: '/pages/teacher/resources' },
					{ key: 'notify', icon: '🔔', label: '消息通知', url: '/pages/notifications/notifications' }
				],
				jobMenus: [
					{ key: 'jobs', icon: '📌', label: '接单广场', url: '/pages/teacher-jobs/jobs' },
					{ key: 'myjobs', icon: '📥', label: '我的投递', url: '/pages/teacher-jobs/my-applications' }
				]
			};
		},
		onShow() {
			const user = guardRole('teacher');
			if (!user) return;
			this.realName = user.realName || user.username || '老师';
		},
		methods: {
			go(url) {
				uni.navigateTo({ url, fail: () => uni.showToast({ title: '该模块建设中', icon: 'none' }) });
			},
			goChangePwd() { uni.navigateTo({ url: '/pages/change-password/change-password' }); },
			onLogout() {
				uni.showModal({
					title: '退出登录', content: '确定要退出当前账号吗？',
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
	};
</script>

<style scoped>
	.head { padding: 32rpx 32rpx 48rpx; background: linear-gradient(135deg, #14b8a6 0%, #0ea5e9 100%); border-bottom-left-radius: 36rpx; border-bottom-right-radius: 36rpx; }
	.head-top { display: flex; align-items: center; justify-content: space-between; }
	.head-hi { font-size: 38rpx; font-weight: 700; color: #ffffff; }
	.head-role { margin-top: 8rpx; font-size: 24rpx; color: rgba(255, 255, 255, 0.85); }
	.head-logout { padding: 10rpx 28rpx; font-size: 24rpx; color: #ffffff; border-radius: 30rpx; background: rgba(255, 255, 255, 0.2); }
	.head-logout-hover { background: rgba(255, 255, 255, 0.32); }
	.head-acts { display: flex; gap: 12rpx; align-items: center; }

	.menu-card { margin-top: -24rpx; }
	.menu-card + .menu-card { margin-top: 20rpx; }
	.menu-grid { display: flex; flex-wrap: wrap; }
	.menu-item { width: 33.33%; display: flex; flex-direction: column; align-items: center; padding: 28rpx 0; box-sizing: border-box; }
	.menu-item-hover { opacity: 0.7; }
	.menu-icon-wrap { position: relative; }
	.menu-icon { display: block; width: 96rpx; height: 96rpx; line-height: 96rpx; text-align: center; font-size: 46rpx; border-radius: 28rpx; background: linear-gradient(135deg, rgba(20, 184, 166, 0.14), rgba(14, 165, 233, 0.12)); }
	.menu-label { margin-top: 14rpx; font-size: 24rpx; color: #4b5563; }
</style>
