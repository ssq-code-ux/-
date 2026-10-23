<template>
	<view class="page">
		<view class="top-bar">
			<text class="tip">这里是你名下的学生，可直接布置作业或查看作业；点右上「添加学生」按姓名搜索并绑定。</text>
			<view class="btn btn-primary add-btn" @click="goAdd">➕ 添加学生</view>
		</view>

		<view class="list">
			<view v-for="s in list" :key="s.id" class="card scard">
				<view class="srow">
					<view class="avatar">{{ (s.realName || s.username || '?').slice(0, 1) }}</view>
					<view class="smain">
						<text class="sname">{{ s.realName || '未命名' }}</text>
						<text class="muted sacc">@{{ s.username }}</text>
					</view>
				</view>
				<view class="ops">
					<text class="mini-btn" @click="goProfile(s)">✏️ 编辑信息</text>
					<text class="mini-btn mini-btn-primary" @click="goHomework(s)">📝 布置作业</text>
					<text class="mini-btn mini-btn-success" @click="goList(s)">📄 查看作业</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="还没有学生，点上方添加" />
			<load-more :status="loading ? 'loading' : ''" />
		</view>
	</view>
</template>

<script>
	import { students } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() { return { list: [], loading: false }; },
		onShow() {
			if (!guardRole('teacher')) return;
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			async load() {
				this.loading = true;
				try { this.list = (await students()) || []; } catch (e) { this.list = []; } finally { this.loading = false; }
			},
			goAdd() { uni.navigateTo({ url: '/pages/teacher/add-student' }); },
			goProfile(s) { uni.navigateTo({ url: `/pages/teacher/student-profile?studentId=${s.id}&studentName=${encodeURIComponent(s.realName || s.username || '')}` }); },
			goHomework(s) { uni.navigateTo({ url: `/pages/teacher/homework-form?studentId=${s.id}&studentName=${encodeURIComponent(s.realName || s.username || '')}` }); },
			goList(s) { uni.navigateTo({ url: `/pages/teacher/homework?studentId=${s.id}` }); }
		}
	};
</script>

<style scoped>
	.top-bar { padding: 20rpx 24rpx 0; }
	.tip { display: block; font-size: 22rpx; color: #909399; line-height: 1.5; margin-bottom: 16rpx; }
	.add-btn { width: 100%; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.scard { margin-bottom: 20rpx; }
	.srow { display: flex; align-items: center; gap: 18rpx; }
	.avatar { width: 80rpx; height: 80rpx; border-radius: 50%; background: #e6fffa; color: #0d9488; font-size: 34rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
	.smain { display: flex; flex-direction: column; gap: 4rpx; }
	.sname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.sacc { font-size: 23rpx; }
	.ops { margin-top: 18rpx; display: flex; gap: 14rpx; flex-wrap: wrap; }
</style>
