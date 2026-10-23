<template>
	<view class="page">
		<view class="top-bar">
			<search-bar v-model="keyword" placeholder="搜索资料标题" @search="reload" @clear="reload">
				<template #extra>
					<picker mode="selector" :range="subjectRange" :value="subjectIdx" @change="onSubjectPick">
						<view class="subj-picker">{{ subjectRange[subjectIdx] || '全部学科' }} ▾</view>
					</picker>
					<picker mode="selector" :range="typeRange" :value="typeIdx" @change="onTypePick">
						<view class="type-picker">{{ typeRange[typeIdx] }} ▾</view>
					</picker>
				</template>
			</search-bar>
		</view>

		<view class="list">
			<view v-for="r in list" :key="r.id" class="card rcard" hover-class="rcard-hover" @click="goDetail(r)">
				<view class="row-between">
					<text class="rtitle">{{ r.title }}</text>
					<text class="rtag">{{ typeText(r.type) }}</text>
				</view>
				<view v-if="r.content" class="rcontent">{{ r.content.slice(0, 80) }}{{ r.content.length > 80 ? '...' : '' }}</view>
				<view v-else class="rhint">点击查看详情 →</view>
				<view v-if="r.teacherName" class="rteacher">🧑‍🏫 {{ r.teacherName }}</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无学习资料 📂" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { resourceList, subjectList } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	const TYPE = { grammar: '语法', reading: '阅读', listening: '听力', writing: '写作' };
	const TYPES = [
		{ value: '', label: '全部类型' },
		{ value: 'grammar', label: '语法' },
		{ value: 'reading', label: '阅读' },
		{ value: 'listening', label: '听力' },
		{ value: 'writing', label: '写作' }
	];

	export default {
		data() {
			return {
				keyword: '', subjects: [], subjectId: null, subjectIdx: 0,
				type: '', typeIdx: 0,
				list: [], total: 0, page: 1, size: 10, loading: false
			};
		},
		computed: {
			subjectRange() { return ['全部学科'].concat(this.subjects.map((s) => s.name)); },
			typeRange() { return TYPES.map((t) => t.label); },
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onShow() {
			if (!guardRole('student')) return;
			if (!this.subjects.length) this.loadSubjects();
			this.reload();
		},
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.page + 1); },
		methods: {
			typeText(t) { return TYPE[t] || t || '资料'; },
			async loadSubjects() {
				try { this.subjects = (await subjectList()) || []; } catch (e) { this.subjects = []; }
				// 默认选中「英语」学科
				if (this.subjectId == null) {
					const en = this.subjects.find((s) => s.name === '英语');
					if (en) { this.subjectId = en.id; this.subjectIdx = this.subjects.indexOf(en) + 1; }
				}
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const params = { page, size: this.size };
					const kw = String(this.keyword || '').trim();
					if (kw) params.keyword = kw;
					if (this.subjectId) params.subjectId = this.subjectId;
					if (this.type) params.type = this.type;
					const r = await resourceList(params);
					const rows = r.records || r.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = r.total || 0;
					this.page = page;
				} catch (e) { /* 已提示 */ } finally { this.loading = false; }
			},
			onSubjectPick(e) {
				const idx = Number(e.detail.value);
				this.subjectIdx = idx;
				this.subjectId = idx === 0 ? null : this.subjects[idx - 1].id;
				this.reload();
			},
			onTypePick(e) {
				const idx = Number(e.detail.value);
				this.typeIdx = idx;
				this.type = TYPES[idx].value;
				this.reload();
			},
			goDetail(r) { uni.navigateTo({ url: `/pages/student/resource-detail?id=${r.id}` }); }
		}
	};
</script>

<style scoped>
	.top-bar { padding: 4rpx 0 0; }
	.subj-picker { height: 60rpx; line-height: 60rpx; padding: 0 22rpx; border-radius: 999rpx; background: #eef2ff; color: #6366f1; font-size: 24rpx; }
	.type-picker { height: 60rpx; line-height: 60rpx; padding: 0 22rpx; border-radius: 999rpx; background: #e6fffa; color: #0d9488; font-size: 24rpx; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.rcard { margin-bottom: 20rpx; border-left: 6rpx solid #6366f1; }
	.rcard-hover { opacity: 0.85; }
	.rtitle { font-size: 30rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.rtag { flex-shrink: 0; font-size: 22rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.rcontent { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; }
	.rhint { margin-top: 10rpx; font-size: 24rpx; color: #909399; }
	.rteacher { margin-top: 12rpx; font-size: 22rpx; color: #909399; }
</style>
