<template>
	<view v-if="status" class="load-more">
		<text v-if="status === 'loading'" class="lm-text">加载中…</text>
		<text v-else-if="status === 'noMore'" class="lm-text">— 没有更多了 —</text>
	</view>
</template>

<script>
	export default {
		name: 'load-more',
		props: {
			/* '' 不显示 / 'loading' 加载中 / 'noMore' 没有更多 */
			status: { type: String, default: '' }
		}
	}
</script>

<style scoped>
	.load-more {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 28rpx 0 40rpx;
	}
	.lm-text {
		font-size: 24rpx;
		color: #a0a4ab;
	}
</style>
