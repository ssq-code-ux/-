<template>
	<view class="page">
		<!-- Hero -->
		<view class="hero">
			<view class="hero-greet">{{ greeting }}，{{ realName }} 👋</view>
			<view class="hero-date">{{ todayText }}</view>
			<view v-if="pendingCount > 0" class="hero-tip">
				<text>📌 你还有 {{ pendingCount }} 份作业待完成<text v-if="overdueCount"> ，其中 {{ overdueCount }} 份已逾期</text></text>
				<view class="hero-btn" @click="go('/pages/student/homework')">去做作业 →</view>
			</view>
			<view v-else class="hero-tip ok">🎉 当前作业已全部完成，去复习词汇吧！</view>
		</view>

		<!-- 4 统计卡 -->
		<view class="stat-grid">
			<view class="stat-card sc-a" hover-class="stat-hover" @click="go('/pages/student/homework')">
				<text class="stat-num">{{ pendingCount }}</text>
				<text class="stat-label">待完成作业</text>
				<text v-if="overdueCount" class="stat-warn">⚠️ {{ overdueCount }} 逾期</text>
			</view>
			<view class="stat-card sc-b" hover-class="stat-hover" @click="go('/pages/student/homework')">
				<text class="stat-num">{{ submittedCount }}</text>
				<text class="stat-label">已提交待批改</text>
			</view>
			<view class="stat-card sc-c" hover-class="stat-hover" @click="go('/pages/student/vocabulary?tab=error')">
				<text class="stat-num">{{ unreviewedCount }}</text>
				<text class="stat-label">错题本</text>
			</view>
			<view class="stat-card sc-d" hover-class="stat-hover" @click="go('/pages/student/vocabulary?tab=review')">
				<text class="stat-num">{{ dueReviewCount }}</text>
				<text class="stat-label">复习本</text>
			</view>
		</view>

		<!-- 快捷入口 -->
		<view class="card">
			<view class="card-title">快捷入口</view>
			<view class="quick-grid">
				<view v-for="q in quicks" :key="q.url" class="quick-item" hover-class="stat-hover" @click="go(q.url)">
					<text class="quick-icon">{{ q.icon }}</text>
					<text class="quick-label">{{ q.label }}</text>
				</view>
			</view>
		</view>

		<!-- 最近作业 -->
		<view class="card">
			<view class="row-between">
				<view class="card-title">最近作业</view>
				<text class="more" @click="go('/pages/student/homework')">全部 →</text>
			</view>
			<view v-if="recentList.length" class="recent-list">
				<view v-for="h in recentList" :key="h.id" class="recent-item" @click="goHomeworkDetail(h)">
					<view class="recent-main">
						<text class="recent-title">{{ h.title }}</text>
						<text class="recent-meta">{{ h.type === 'answer_sheet' ? '📄 答题卡' : (h.subject || '作业') }} · {{ h.deadline ? fmtDate(h.deadline) + ' 截止' : '无截止' }}</text>
					</view>
					<status-tag :type="statusTag(h.status)" :text="h.status" />
				</view>
			</view>
			<empty-state v-else-if="!loading" text="暂无作业" />
		</view>
	</view>
</template>

<script>
	import { homeworkList, errorStats } from '../../api/index.js';
	import { fmtDate } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TAG = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };

	export default {
		data() {
			return {
				realName: '同学', loading: false,
				homeworks: [], overdueCount: 0,
				unreviewedCount: 0, dueReviewCount: 0,
				quicks: [
					{ icon: '🃏', label: '记忆卡片', url: '/pages/student/flashcards' },
					{ icon: '🎯', label: '看词选意', url: '/pages/student/quiz' },
					{ icon: '📁', label: '学习资料', url: '/pages/student/resources' },
					{ icon: '📝', label: '我的作业', url: '/pages/student/homework' }
				]
			};
		},
		computed: {
			greeting() {
				const h = new Date().getHours();
				if (h < 6) return '夜深了';
				if (h < 12) return '早上好';
				if (h < 14) return '中午好';
				if (h < 18) return '下午好';
				return '晚上好';
			},
			todayText() {
				const d = new Date();
				const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()];
				return `${d.getMonth() + 1}月${d.getDate()}日 ${week}`;
			},
			pendingCount() { return this.homeworks.filter((h) => h.status === '未提交' || h.status === '需重做').length; },
			submittedCount() { return this.homeworks.filter((h) => h.status === '已提交').length; },
			recentList() {
				const order = { '需重做': 0, '未提交': 1, '已提交': 2, '已批改': 3 };
				return this.homeworks
					.slice()
					.sort((a, b) => (order[a.status] ?? 9) - (order[b.status] ?? 9))
					.slice(0, 5);
			}
		},
		onShow() {
			const user = guardRole('student');
			if (!user) return;
			this.realName = user.realName || user.username || '同学';
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtDate,
			statusTag(s) { return TAG[s] || 'info'; },
			async load() {
				this.loading = true;
				try {
					const [hw, err] = await Promise.allSettled([homeworkList(), errorStats()]);
					if (hw.status === 'fulfilled') {
						this.homeworks = hw.value || [];
						const now = Date.now();
						this.overdueCount = this.homeworks.filter((h) => (h.status === '未提交' || h.status === '需重做') && h.deadline && new Date(String(h.deadline).replace(/-/g, '/')).getTime() < now).length;
					}
					if (err.status === 'fulfilled' && err.value) {
						this.unreviewedCount = err.value.errorCount || 0;
						this.dueReviewCount = err.value.reviewCount || 0;
					}
				} finally { this.loading = false; }
			},
			goHomeworkDetail(h) { uni.navigateTo({ url: `/pages/student/homework-detail?id=${h.id}` }); },
			go(url) { uni.navigateTo({ url, fail: () => uni.showToast({ title: '该模块建设中', icon: 'none' }) }); }
		}
	};
</script>

<style scoped>
	.hero { margin: 0 0 20rpx; padding: 40rpx 32rpx 44rpx; background: linear-gradient(135deg, #6366f1 0%, #38bdf8 100%); border-bottom-left-radius: 36rpx; border-bottom-right-radius: 36rpx; }
	.hero-greet { font-size: 40rpx; font-weight: 700; color: #ffffff; }
	.hero-date { margin-top: 8rpx; font-size: 25rpx; color: rgba(255, 255, 255, 0.85); }
	.hero-tip { margin-top: 24rpx; padding: 20rpx 22rpx; border-radius: 18rpx; background: rgba(255, 255, 255, 0.18); color: #ffffff; font-size: 25rpx; line-height: 1.6; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
	.hero-tip.ok { justify-content: flex-start; }
	.hero-btn { flex-shrink: 0; padding: 10rpx 22rpx; border-radius: 999rpx; background: #ffffff; color: #4f46e5; font-size: 24rpx; font-weight: 600; }

	.stat-grid { display: flex; flex-wrap: wrap; padding: 0 16rpx; margin-top: -18rpx; }
	.stat-card { width: calc(50% - 16rpx); margin: 8rpx; box-sizing: border-box; padding: 28rpx 24rpx; border-radius: 20rpx; background: #ffffff; box-shadow: 0 6rpx 20rpx rgba(99, 102, 241, 0.08); display: flex; flex-direction: column; }
	.stat-hover { opacity: 0.82; }
	.stat-num { font-size: 52rpx; font-weight: 800; line-height: 1.1; }
	.stat-label { margin-top: 8rpx; font-size: 24rpx; color: #6b7280; }
	.stat-warn { margin-top: 8rpx; font-size: 21rpx; color: #e11d48; }
	.sc-a .stat-num { color: #6366f1; }
	.sc-b .stat-num { color: #f59e0b; }
	.sc-c .stat-num { color: #ef4444; }
	.sc-d .stat-num { color: #10b981; }

	.quick-grid { display: flex; flex-wrap: wrap; margin-top: 8rpx; }
	.quick-item { width: 25%; display: flex; flex-direction: column; align-items: center; padding: 16rpx 0; box-sizing: border-box; }
	.quick-icon { width: 88rpx; height: 88rpx; line-height: 88rpx; text-align: center; font-size: 44rpx; border-radius: 24rpx; background: linear-gradient(135deg, rgba(99, 102, 241, 0.14), rgba(56, 189, 248, 0.12)); }
	.quick-label { margin-top: 12rpx; font-size: 23rpx; color: #4b5563; }

	.more { font-size: 24rpx; color: #6366f1; }
	.recent-list { margin-top: 8rpx; }
	.recent-item { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 0; border-bottom: 1rpx solid #f0f2f6; }
	.recent-item:last-child { border-bottom: none; }
	.recent-main { flex: 1; min-width: 0; }
	.recent-title { display: block; font-size: 28rpx; font-weight: 600; color: #1f2d52; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.recent-meta { display: block; margin-top: 6rpx; font-size: 22rpx; color: #909399; }
</style>
