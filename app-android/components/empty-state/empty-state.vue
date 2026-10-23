<template>
	<view class="empty">
		<text class="empty-icon">{{ icon }}</text>
		<text class="empty-text">{{ text }}</text>
	</view>
</template>

<script>
	export default {
		name: 'empty-state',
		props: {
			text: { type: String, default: '暂无数据' },
			icon: { type: String, default: '🗂️' }
		}
	}
</script>

<style scoped>
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 100rpx 0;
	}
	.empty-icon {
		font-size: 84rpx;
		opacity: 0.5;
	}
	.empty-text {
		margin-top: 18rpx;
		font-size: 26rpx;
		color: #a0a4ab;
	}
</style>
