<template>
	<view class="page">
		<!-- 顶部词库选择 -->
		<view class="hero grad-header">
			<view class="hero-title">🎯 背单词</view>
			<view class="hero-sub">选择词库，开始记忆卡片、看词选意或复习错题</view>
			<picker class="bank-picker" mode="selector" :range="banks" :value="bankIndex" @change="onBankChange">
				<view class="bank-box">
					<text class="bank-label">当前词库</text>
					<text class="bank-name">{{ bank }} ▾</text>
				</view>
			</picker>
			<view class="hero-count">该词库共 <text class="hl">{{ total }}</text> 个单词</view>
		</view>

		<!-- 功能入口 -->
		<view class="entry-list">
			<view class="entry" @click="go('/pages/teacher/word-cards')">
				<view class="entry-icon i1">🃏</view>
				<view class="entry-body">
					<text class="entry-title">记忆卡片</text>
					<text class="entry-desc">翻转卡片记忆单词，标记认识 / 不熟</text>
				</view>
				<text class="entry-arrow">›</text>
			</view>

			<view class="entry" @click="go('/pages/teacher/word-quiz')">
				<view class="entry-icon i2">🎯</view>
				<view class="entry-body">
					<text class="entry-title">看词选意</text>
					<text class="entry-desc">看词选义 + 看义写词，两阶段检测</text>
				</view>
				<text class="entry-arrow">›</text>
			</view>

			<view class="entry" @click="go('/pages/teacher/word-errors')">
				<view class="entry-icon i3">📋</view>
				<view class="entry-body">
					<text class="entry-title">错题本</text>
					<text class="entry-desc">复习写错的单词，直到掌握</text>
				</view>
				<text class="entry-arrow">›</text>
			</view>
		</view>

		<view class="tip">提示：词库由教师端「词汇管理」维护，四六级词库可在新增单词时选择。</view>
	</view>
</template>

<script>
	import { vocabBanks, vocabList } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	const BANK_KEY = 'teacher-words-bank';

	export default {
		data() {
			return {
				banks: ['学生词库', '四级词库', '六级词库'],
				bankIndex: 0,
				total: 0
			};
		},
		computed: {
			bank() { return this.banks[this.bankIndex] || '学生词库'; }
		},
		async onLoad() {
			if (!guardRole('teacher')) return;
			try {
				const cached = uni.getStorageSync(BANK_KEY);
				if (cached) {
					const i = this.banks.indexOf(cached);
					if (i >= 0) this.bankIndex = i;
				}
			} catch (e) { /* 忽略 */ }
			await this.loadBanks();
			await this.loadCount();
		},
		onShow() { if (guardRole('teacher')) this.loadCount(); },
		methods: {
			async loadBanks() {
				try {
					const list = await vocabBanks();
					if (Array.isArray(list) && list.length) {
						this.banks = list;
						const cur = uni.getStorageSync(BANK_KEY);
						const i = list.indexOf(cur);
						if (i >= 0) this.bankIndex = i;
					}
				} catch (e) { /* 默认三个词库 */ }
			},
			async loadCount() {
				try {
					const r = await vocabList({ page: 1, size: 1, bank: this.bank });
					this.total = (r && r.total) || 0;
				} catch (e) { this.total = 0; }
			},
			onBankChange(e) {
				this.bankIndex = Number(e.detail.value) || 0;
				try { uni.setStorageSync(BANK_KEY, this.bank); } catch (err) { /* 忽略 */ }
				this.loadCount();
			},
			go(url) {
				try { uni.setStorageSync(BANK_KEY, this.bank); } catch (e) { /* 忽略 */ }
				uni.navigateTo({ url });
			}
		}
	};
</script>

<style scoped>
	.hero { padding: 44rpx 32rpx 40rpx; border-bottom-left-radius: 32rpx; border-bottom-right-radius: 32rpx; }
	.hero-title { font-size: 44rpx; font-weight: 800; }
	.hero-sub { margin-top: 10rpx; font-size: 25rpx; opacity: 0.9; }
	.bank-picker { display: block; margin-top: 26rpx; }
	.bank-box { display: flex; align-items: center; justify-content: space-between; background: rgba(255, 255, 255, 0.18); border-radius: 18rpx; padding: 22rpx 26rpx; }
	.bank-label { font-size: 25rpx; opacity: 0.9; }
	.bank-name { font-size: 30rpx; font-weight: 700; }
	.hero-count { margin-top: 20rpx; font-size: 24rpx; opacity: 0.92; }
	.hl { font-size: 30rpx; font-weight: 800; }

	.entry-list { padding: 24rpx; }
	.entry { display: flex; align-items: center; gap: 20rpx; background: #ffffff; border-radius: 20rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 20rpx rgba(31, 45, 82, 0.06); }
	.entry-icon { width: 88rpx; height: 88rpx; line-height: 88rpx; text-align: center; border-radius: 22rpx; font-size: 44rpx; flex-shrink: 0; }
	.i1 { background: #eef2ff; }
	.i2 { background: #e6fffa; }
	.i3 { background: #fdecef; }
	.entry-body { flex: 1; }
	.entry-title { display: block; font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.entry-desc { display: block; margin-top: 6rpx; font-size: 23rpx; color: #909399; }
	.entry-arrow { font-size: 44rpx; color: #c0c4cc; }
	.tip { padding: 0 32rpx 40rpx; font-size: 22rpx; color: #b0b4bb; line-height: 1.6; }
</style>