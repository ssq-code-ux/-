<template>
	<view class="page">
		<view class="top-bar">
			<search-bar v-model="keyword" placeholder="搜索单词或释义" @search="reload" @clear="reload" />
			<view class="btn btn-primary add-btn" @click="goAdd">➕ 添加词汇</view>
		</view>

		<view class="list">
			<view v-for="v in list" :key="v.id" class="card vcard">
				<view class="row-between">
					<view class="vhead">
						<text class="vword">{{ v.word }}</text>
						<text class="speak-btn" @click="speak(v.word)">🔊</text>
						<text v-if="v.phonetic" class="v-phonetic">{{ v.phonetic }}</text>
						<status-tag v-if="v.bank" type="success" :text="v.bank" />
						<status-tag v-if="v.partOfSpeech" type="info" :text="v.partOfSpeech" />
					</view>
				</view>
				<view class="vtrans">{{ v.translation }}</view>
				<view v-if="extList(v.extensions).length" class="exts">
					<text class="ext-label">延伸词</text>
					<text v-for="(e, i) in extList(v.extensions)" :key="i" class="ext">{{ e }}</text>
				</view>
				<view class="ops">
					<text class="mini-btn mini-btn-primary" @click="goEdit(v)">编辑</text>
					<text class="mini-btn mini-btn-danger" @click="onDelete(v)">删除</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无词汇，点上方添加" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { vocabList, vocabSearch, deleteVocab } from '../../api/index.js';
	import { speakWord, stopSpeak } from '../../utils/speech.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return { keyword: '', list: [], total: 0, page: 1, size: 10, loading: false, loaded: false };
		},
		computed: {
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onShow() {
			if (!guardRole('teacher')) return;
			// 只在首次进入、或离开期间发生过增删改（dirty 标记）时才重新加载，
			// 单纯从详情/编辑页返回时复用内存里的列表，避免每次都重新请求。
			const dirty = uni.getStorageSync('teacher-vocab-dirty');
			if (!this.loaded || dirty) {
				if (dirty) uni.removeStorageSync('teacher-vocab-dirty');
				this.reload();
			}
		},
		onPullDownRefresh() { this.loaded = false; this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.page + 1); },
		onUnload() { stopSpeak(); },
		methods: {
			extList(s) { return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []; },
			speak(w) { speakWord(w); },
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const params = { page, size: this.size };
					const kw = String(this.keyword || '').trim();
					const r = kw ? await vocabSearch({ ...params, keyword: kw }) : await vocabList(params);
					const rows = r.records || r.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = r.total || 0;
					this.page = page;
					this.loaded = true;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			goAdd() { uni.navigateTo({ url: '/pages/teacher/vocab-form' }); },
			goEdit(v) { uni.navigateTo({ url: `/pages/teacher/vocab-form?data=${encodeURIComponent(JSON.stringify(v))}` }); },
			onDelete(v) {
				uni.showModal({
					title: '删除词汇', content: `确定删除「${v.word}」吗？`, confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await deleteVocab(v.id); uni.showToast({ title: '已删除', icon: 'success' }); this.reload(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.top-bar { padding: 20rpx 24rpx 0; }
	.add-btn { width: 100%; margin-top: 16rpx; text-align: center; }
	.list { padding: 20rpx 24rpx 40rpx; }
	.vcard { margin-bottom: 20rpx; }
	.vhead { display: flex; align-items: center; gap: 14rpx; }
	.vword { font-size: 34rpx; font-weight: 700; color: #1f2d52; }
	.v-phonetic { font-size: 24rpx; color: #909399; }
	.speak-btn { font-size: 30rpx; padding: 2rpx 8rpx; }
	.vtrans { margin-top: 10rpx; font-size: 27rpx; color: #606266; line-height: 1.6; }
	.exts { margin-top: 14rpx; display: flex; align-items: center; gap: 10rpx; flex-wrap: wrap; }
	.ext-label { font-size: 22rpx; color: #909399; }
	.ext { font-size: 22rpx; color: #0d9488; background: #e6fffa; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; }
</style>
