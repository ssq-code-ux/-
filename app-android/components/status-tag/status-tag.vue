<template>
	<text class="tag" :class="'tag-' + type">{{ text }}</text>
</template>

<script>
	export default {
		name: 'status-tag',
		props: {
			/* primary / success / warning / danger / info */
			type: { type: String, default: 'info' },
			text: { type: String, default: '' }
		}
	}
</script>

<style scoped>
	.tag {
		display: inline-block;
		padding: 2rpx 16rpx;
		border-radius: 20rpx;
		font-size: 22rpx;
		line-height: 36rpx;
		white-space: nowrap;
	}
	.tag-primary { background: rgba(102, 126, 234, 0.12); color: #667eea; }
	.tag-success { background: rgba(22, 163, 74, 0.12); color: #16a34a; }
	.tag-warning { background: rgba(245, 158, 11, 0.14); color: #d97706; }
	.tag-danger { background: rgba(225, 29, 72, 0.12); color: #e11d48; }
	.tag-info { background: #eef0f4; color: #6b7280; }
</style>
