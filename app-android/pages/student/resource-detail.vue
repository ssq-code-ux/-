<template>
	<view class="page">
		<view v-if="res" class="wrap">
			<view class="card">
				<view class="title-lg">{{ res.title }}</view>
				<view class="meta">
					<text class="rtag">{{ typeText(res.type) }}</text>
					<text v-if="res.teacherName" class="tname">🧑‍🏫 {{ res.teacherName }}</text>
				</view>
			</view>

			<view class="card" v-if="res.content">
				<view class="card-title">📝 文字内容</view>
				<view class="pre">{{ res.content }}</view>
			</view>

			<view class="card" v-if="images.length">
				<view class="card-title">🖼️ 图片</view>
				<view class="thumbs">
					<image v-for="(u, i) in images" :key="i" class="thumb" :src="u" mode="widthFix" @click="preview(images, i)" />
				</view>
			</view>

			<view class="card" v-if="docs.length">
				<view class="card-title">📄 文档</view>
				<view v-for="(u, i) in docs" :key="i" class="doc-row" @click="openDoc(u)">
					<text class="doc-name">{{ fileName(u) }}</text>
					<text class="doc-open">打开</text>
				</view>
				<text class="muted doc-tip">.doc 旧格式可能无法直接预览，建议下载后用对应应用打开</text>
			</view>

			<view class="card" v-if="!res.content && !images.length && !docs.length">
				<empty-state text="暂无内容" />
			</view>
		</view>
		<view v-else-if="loading" class="card"><view class="muted">加载中…</view></view>
	</view>
</template>

<script>
	import { getResource } from '../../api/index.js';
	import { mediaUrl, splitFiles, isImageUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TYPE = { grammar: '语法', reading: '阅读', listening: '听力', writing: '写作' };

	export default {
		data() {
			return { id: null, res: null, loading: false };
		},
		computed: {
			images() { return splitFiles(this.res && this.res.fileUrl).filter(isImageUrl).map(mediaUrl); },
			docs() { return splitFiles(this.res && this.res.fileUrl).filter((u) => !isImageUrl(u)); }
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			this.id = opt && opt.id;
			this.load();
		},
		methods: {
			typeText(t) { return TYPE[t] || t || '资料'; },
			async load() {
				this.loading = true;
				try { this.res = (await getResource(this.id)) || null; } catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			fileName(u) { const s = String(u || ''); return decodeURIComponent(s.split('/').pop() || '文档'); },
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			openDoc(u) {
				uni.showLoading({ title: '下载中' });
				uni.downloadFile({
					url: mediaUrl(u),
					success: (r) => {
						uni.hideLoading();
						uni.openDocument({ filePath: r.tempFilePath, showMenu: true, fail: () => uni.showToast({ title: '该格式暂不支持预览', icon: 'none' }) });
					},
					fail: () => { uni.hideLoading(); uni.showToast({ title: '下载失败', icon: 'none' }); }
				});
			}
		}
	};
</script>

<style scoped>
	.wrap { padding-bottom: 40rpx; }
	.meta { margin-top: 14rpx; display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; }
	.rtag { font-size: 22rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.tname { font-size: 22rpx; color: #909399; }
	.pre { font-size: 27rpx; color: #303133; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
	.thumbs { display: flex; flex-direction: column; gap: 14rpx; margin-top: 8rpx; }
	.thumb { width: 100%; border-radius: 12rpx; background: #f2f4f8; }
	.doc-row { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; padding: 18rpx 20rpx; background: #f8fafc; border-radius: 12rpx; }
	.doc-name { flex: 1; min-width: 0; font-size: 25rpx; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.doc-open { flex-shrink: 0; margin-left: 16rpx; font-size: 24rpx; color: #6366f1; font-weight: 600; }
	.doc-tip { display: block; margin-top: 14rpx; font-size: 21rpx; line-height: 1.5; }
</style>
