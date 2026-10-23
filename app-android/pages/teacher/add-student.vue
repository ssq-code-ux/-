<template>
	<view class="page">
		<view class="card">
			<view class="tip">通过真实姓名搜索学生并绑定到自己名下；留空搜索则列出全部未绑定学生。</view>
			<search-bar v-model="keyword" placeholder="输入学生姓名筛选" @search="load" @clear="load" />
		</view>

		<view class="list">
			<view v-for="s in list" :key="s.id" class="card scard">
				<view class="row-between">
					<view class="smain">
						<text class="sname">{{ s.realName || '未命名' }}</text>
						<text class="muted sacc">@{{ s.username }}<text v-if="s.phone"> · {{ s.phone }}</text></text>
					</view>
					<status-tag v-if="s.teacherId === myId" type="success" text="已绑定" />
					<status-tag v-else-if="s.teacherId" type="warning" text="已绑定他人" />
					<status-tag v-else type="info" text="未绑定" />
				</view>
				<view class="ops" v-if="!s.teacherId">
					<text class="mini-btn mini-btn-primary" @click="doBind(s)">{{ bindingId === s.id ? '绑定中…' : '绑定' }}</text>
				</view>
				<view class="ops" v-else-if="s.teacherId === myId">
					<text class="mine">✓ 我的学生</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="未找到匹配的学生，或已全部绑定" />
			<load-more :status="loading ? 'loading' : ''" />
		</view>
	</view>
</template>

<script>
	import { searchStudents, bindStudent } from '../../api/index.js';
	import { getUser } from '../../utils/auth.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() { return { keyword: '', list: [], loading: false, bindingId: null, myId: null }; },
		onLoad() {
			const u = guardRole('teacher');
			if (!u) return;
			this.myId = u.id;
		},
		onShow() { if (this.myId != null) this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			async load() {
				this.loading = true;
				try {
					const name = String(this.keyword || '').trim();
					this.list = (await searchStudents(name || undefined)) || [];
				} catch (e) { this.list = []; } finally { this.loading = false; }
			},
			async doBind(s) {
				if (this.bindingId) return;
				this.bindingId = s.id;
				try {
					await bindStudent(s.id);
					uni.showToast({ title: '绑定成功', icon: 'success' });
					this.load();
				} catch (e) { /* 已提示 */ } finally { this.bindingId = null; }
			}
		}
	};
</script>

<style scoped>
	.tip { display: block; font-size: 22rpx; color: #909399; line-height: 1.5; margin-bottom: 16rpx; }
	.list { padding: 20rpx 24rpx 40rpx; }
	.scard { margin-bottom: 20rpx; }
	.smain { display: flex; flex-direction: column; gap: 4rpx; }
	.sname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.sacc { font-size: 23rpx; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; }
	.mine { font-size: 24rpx; color: #16a34a; }
</style>
