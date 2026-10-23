<template>
	<view class="page">
		<view class="head">
			<view class="head-hi">👪 我的孩子</view>
			<view class="head-sub">点击孩子查看作业、成绩与学习周报；未绑定的先用孩子登录账号绑定</view>
			<view class="bind-btn" hover-class="bind-btn-hover" @click="openBind">➕ 绑定孩子</view>
		</view>

		<view class="list">
			<view v-for="c in children" :key="c.id" class="card ccard" hover-class="ccard-hover" @click="goChild(c)">
				<view class="avatar">{{ (c.realName || c.username || '孩').slice(0, 1) }}</view>
				<view class="cinfo">
					<view class="cname">{{ c.realName || c.username }}</view>
					<view class="cuser muted" v-if="c.username && c.realName">账号 {{ c.username }}</view>
					<view class="cgo">查看学习情况 ›</view>
				</view>
			</view>

			<empty-state v-if="!loading && !children.length" text="还没有绑定孩子，点上方「绑定孩子」" />
		</view>
	</view>
</template>

<script>
	import { parentChildren, parentBindChild } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				children: [],
				loading: false
			};
		},
		onShow() {
			if (!guardRole('parent')) return;
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			async load() {
				this.loading = true;
				try {
					this.children = (await parentChildren()) || [];
				} catch (e) { this.children = []; } finally {
					this.loading = false;
				}
			},
			goChild(c) {
				uni.navigateTo({ url: `/pages/parent/child-detail?id=${c.id}&name=${encodeURIComponent(c.realName || c.username || '')}` });
			},
			openBind() {
				uni.showModal({
					title: '绑定孩子',
					editable: true,
					placeholderText: '输入孩子的学生登录账号',
					success: async (r) => {
						if (!r.confirm) return;
						const username = String(r.content || '').trim();
						if (!username) { uni.showToast({ title: '账号不能为空', icon: 'none' }); return; }
						try {
							await parentBindChild(username);
							uni.showToast({ title: '绑定成功', icon: 'success' });
							this.load();
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.head { padding: 32rpx 32rpx 48rpx; background: linear-gradient(135deg, #f97316 0%, #ec4899 100%); border-bottom-left-radius: 36rpx; border-bottom-right-radius: 36rpx; position: relative; }
	.head-hi { font-size: 36rpx; font-weight: 700; color: #ffffff; }
	.head-sub { margin-top: 10rpx; font-size: 23rpx; color: rgba(255, 255, 255, 0.88); line-height: 1.6; padding-right: 180rpx; }
	.bind-btn { position: absolute; right: 32rpx; top: 36rpx; padding: 12rpx 26rpx; font-size: 25rpx; color: #ffffff; background: rgba(255, 255, 255, 0.24); border-radius: 30rpx; }
	.bind-btn-hover { background: rgba(255, 255, 255, 0.4); }

	.list { padding: 20rpx 24rpx 40rpx; margin-top: -24rpx; }
	.ccard { display: flex; align-items: center; gap: 20rpx; margin-bottom: 16rpx; }
	.ccard-hover { opacity: 0.85; }
	.avatar { width: 88rpx; height: 88rpx; border-radius: 50%; flex-shrink: 0; text-align: center; line-height: 88rpx; font-size: 38rpx; font-weight: 700; color: #ffffff; background: linear-gradient(135deg, #f97316, #ec4899); }
	.cinfo { flex: 1; min-width: 0; }
	.cname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.cuser { margin-top: 4rpx; }
	.cgo { margin-top: 8rpx; font-size: 24rpx; color: #f97316; }
</style>
