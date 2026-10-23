<template>
	<view class="page">
		<view class="top-bar">
			<text class="tip">作业布置、批改结果、截止与逾期提醒都会推到这里</text>
			<view v-if="unread > 0" class="read-all" hover-class="read-all-hover" @click="onMarkAll">全部已读 ({{ unread }})</view>
		</view>

		<view class="list">
			<view v-for="n in list" :key="n.id" class="card ncard" :class="{ unread: !n.isRead }" @click="onClick(n)">
				<view class="row-between">
					<view class="row ntitle-row">
						<text v-if="!n.isRead" class="dot"></text>
						<text class="ntitle">{{ n.title || '通知' }}</text>
					</view>
					<status-tag v-if="!n.isRead" type="danger" text="未读" />
				</view>
				<view class="ncontent">{{ n.content }}</view>
				<view class="ntime">{{ fmtTime(n.createTime) }}</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无通知" />
		</view>
	</view>
</template>

<script>
	import { myNotifications, notifyUnreadCount, notifyMarkRead, notifyMarkAllRead } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	export default {
		data() {
			return {
				list: [],
				unread: 0,
				loading: false
			};
		},
		onShow() { this.reload(); },
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtTime,
			async reload() {
				this.loading = true;
				try {
					const [rows, cnt] = await Promise.all([
						myNotifications().catch(() => []),
						notifyUnreadCount().catch(() => 0)
					]);
					this.list = rows || [];
					this.unread = cnt || 0;
				} finally {
					this.loading = false;
				}
			},
			async onClick(n) {
				if (n.isRead) return;
				try {
					await notifyMarkRead(n.id);
					n.isRead = 1;
					this.unread = Math.max(0, this.unread - 1);
				} catch (e) { /* 已提示 */ }
			},
			onMarkAll() {
				uni.showModal({
					title: '全部已读', content: '确定把所有通知标记为已读吗？',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await notifyMarkAllRead();
							this.list.forEach((n) => (n.isRead = 1));
							this.unread = 0;
							uni.showToast({ title: '已全部标记已读', icon: 'success' });
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.top-bar { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 24rpx; background: #ffffff; }
	.tip { flex: 1; font-size: 22rpx; color: #909399; line-height: 1.5; }
	.read-all { flex-shrink: 0; font-size: 24rpx; color: #667eea; background: rgba(102, 126, 234, 0.1); border-radius: 999rpx; padding: 8rpx 20rpx; }
	.read-all-hover { opacity: 0.7; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.ncard { margin-bottom: 16rpx; border-left: 6rpx solid transparent; }
	.ncard.unread { border-left-color: #e11d48; background: #fffafb; }
	.ntitle-row { gap: 10rpx; flex: 1; min-width: 0; }
	.dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #e11d48; flex-shrink: 0; }
	.ntitle { font-size: 28rpx; font-weight: 600; color: #1f2d52; }
	.ncontent { margin-top: 10rpx; font-size: 26rpx; color: #4b5563; line-height: 1.6; }
	.ntime { margin-top: 12rpx; font-size: 22rpx; color: #a0a4ab; }
</style>
