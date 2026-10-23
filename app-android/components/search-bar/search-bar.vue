<template>
	<view class="search-bar">
		<view class="sb-input-wrap">
			<text class="sb-icon">🔍</text>
			<input
				class="sb-input"
				type="text"
				:value="modelValue"
				:placeholder="placeholder"
				placeholder-class="sb-ph"
				confirm-type="search"
				@input="onInput"
				@confirm="$emit('search')"
			/>
			<text v-if="modelValue" class="sb-clear" @click="onClear">✕</text>
		</view>
		<!-- 额外的筛选项（picker 等）由使用方通过 slot 放进第二行 -->
		<view v-if="$slots.extra" class="sb-extra">
			<slot name="extra" />
		</view>
	</view>
</template>

<script>
	export default {
		name: 'search-bar',
		props: {
			modelValue: { type: String, default: '' },
			placeholder: { type: String, default: '搜索' }
		},
		emits: ['update:modelValue', 'search', 'clear'],
		methods: {
			onInput(e) {
				this.$emit('update:modelValue', e.detail.value);
			},
			onClear() {
				this.$emit('update:modelValue', '');
				this.$emit('clear');
			}
		}
	}
</script>

<style scoped>
	.search-bar {
		padding: 16rpx 24rpx;
		background: #ffffff;
	}
	.sb-input-wrap {
		display: flex;
		align-items: center;
		height: 68rpx;
		padding: 0 20rpx;
		background: #f2f4f8;
		border-radius: 34rpx;
	}
	.sb-icon { font-size: 26rpx; margin-right: 10rpx; opacity: 0.6; }
	.sb-input { flex: 1; font-size: 27rpx; color: #303133; }
	.sb-ph { color: #a0a4ab; }
	.sb-clear { font-size: 26rpx; color: #a0a4ab; padding: 0 6rpx; }
	.sb-extra {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 12rpx;
		margin-top: 16rpx;
	}
</style>
