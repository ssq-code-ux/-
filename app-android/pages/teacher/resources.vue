<template>
	<view class="page">
		<!-- 顶部：标题搜索 + 学科筛选 + 快捷操作 -->
		<view class="top-bar">
			<search-bar v-model="keyword" placeholder="按标题搜索资料" @search="reload" @clear="reload">
				<template #extra>
					<picker mode="selector" :range="subjectRange" :value="subjectIdx" @change="onSubjectPick">
						<view class="subj-picker">{{ subjectRange[subjectIdx] || '全部学科' }} ▾</view>
					</picker>
					<view v-if="!viewingTeacher" class="tb-btn" @click="goAdd">➕ 资料</view>
					<view v-if="!viewingTeacher" class="tb-btn" @click="promptAddSubject">➕ 学科</view>
					<view v-if="canDeleteSubject" class="tb-btn danger" @click="confirmDeleteSubject">删学科</view>
				</template>
			</search-bar>
		</view>

		<!-- 正在查看其他老师的共享资料 -->
		<view v-if="viewingTeacher" class="share-bar">
			<text class="share-txt">正在查看 {{ viewingTeacher.name }} 老师的共享资料</text>
			<view class="share-ops">
				<text class="mini-btn mini-btn-success" @click="copyAllFromTeacher">一键复制全部</text>
				<text class="mini-btn" @click="exitSharedView">← 返回我的</text>
			</view>
		</view>

		<!-- 搜索其他教师（仅在自己的资料库时显示） -->
		<view v-if="!viewingTeacher" class="card tsearch">
			<picker class="ts-pick" mode="selector" :range="teacherPickerRange" @change="onTeacherPick">
				<view class="ts-pick-in">📋 全部教师账号（点击选择查看其资料） ▾</view>
			</picker>
			<view class="ts-row">
				<input class="input ts-input" v-model="teacherKeyword" placeholder="搜索教师账号/姓名，看其共享资料" @confirm="doSearchTeachers" />
				<view class="ts-btn" @click="doSearchTeachers">搜索</view>
			</view>
			<view v-if="teacherResults.length" class="ts-results">
				<view v-for="t in teacherResults" :key="t.id" class="ts-item">
					<view class="ts-info">
						<text class="ts-name">{{ t.realName || t.username }}</text>
						<text class="ts-acc">@{{ t.username }}</text>
					</view>
					<text class="mini-btn mini-btn-primary" @click="viewTeacherResources(t)">查看其资料</text>
				</view>
			</view>
		</view>

		<!-- 资料列表 -->
		<view class="list">
			<view v-for="r in list" :key="r.id" class="card rcard">
				<view class="row-between">
					<text class="rtitle">{{ r.title }}</text>
					<text class="rtag">{{ typeText(r.type) }}</text>
				</view>
				<view v-if="r.content" class="rcontent">{{ r.content }}</view>
				<view class="ops">
					<text class="mini-btn" @click="goDetail(r)">详情</text>
					<template v-if="viewingTeacher">
						<text class="mini-btn mini-btn-success" @click="copyToMine(r)">复制到我的库</text>
					</template>
					<template v-else>
						<text class="mini-btn mini-btn-primary" @click="goEdit(r)">编辑</text>
						<text class="mini-btn mini-btn-danger" @click="onDelete(r)">删除</text>
					</template>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" :text="viewingTeacher ? '该老师暂无共享资料' : '暂无学习资料，点上方添加'" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import { resourceList, deleteResource, copyResource, copyAllResources, searchTeachers, subjectList, addSubjectApi, deleteSubjectApi } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	const TYPE = { grammar: '语法', reading: '阅读', listening: '听力', writing: '写作' };

	export default {
		data() {
			return {
				keyword: '', subjects: [], teacherSubjectId: null, subjectIdx: 0,
				list: [], total: 0, page: 1, size: 10, loading: false,
				viewingTeacher: null, teacherKeyword: '', teacherResults: [], allTeachers: []
			};
		},
		computed: {
			subjectRange() { return ['全部学科'].concat(this.subjects.map((s) => s.name)); },
			teacherPickerRange() { return this.allTeachers.map((t) => t.username + (t.realName ? '（' + t.realName + '）' : '')); },
			selectedSubjectName() { const s = this.subjects.find((x) => x.id === this.teacherSubjectId); return s ? s.name : ''; },
			canDeleteSubject() { return !this.viewingTeacher && this.teacherSubjectId && this.selectedSubjectName !== '英语'; },
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onShow() {
			if (!guardRole('teacher')) return;
			this.loadSubjects();
			this.loadAllTeachers();
			this.reload();
		},
		onPullDownRefresh() {
			Promise.all([this.loadSubjects(), this.reload()]).finally(() => uni.stopPullDownRefresh());
		},
		onReachBottom() { if (!this.loading && this.list.length < this.total) this.load(this.page + 1); },
		methods: {
			typeText(t) { return TYPE[t] || t || '资料'; },
			async loadSubjects() {
				try { this.subjects = (await subjectList()) || []; } catch (e) { this.subjects = []; }
				// 保持选中项与 teacherSubjectId 同步
				const idx = this.subjects.findIndex((s) => s.id === this.teacherSubjectId);
				this.subjectIdx = idx >= 0 ? idx + 1 : 0;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const params = { page, size: this.size };
					const kw = String(this.keyword || '').trim();
					if (kw) params.keyword = kw;
					if (this.teacherSubjectId) params.subjectId = this.teacherSubjectId;
					if (this.viewingTeacher) params.teacherId = this.viewingTeacher.id;
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
				this.teacherSubjectId = idx === 0 ? null : this.subjects[idx - 1].id;
				this.reload();
			},
			goAdd() {
				const sid = this.teacherSubjectId ? `?subjectId=${this.teacherSubjectId}` : '';
				uni.navigateTo({ url: `/pages/teacher/resource-form${sid}` });
			},
			goEdit(r) { uni.navigateTo({ url: `/pages/teacher/resource-form?id=${r.id}` }); },
			goDetail(r) { uni.navigateTo({ url: `/pages/teacher/resource-detail?id=${r.id}` }); },
			promptAddSubject() {
				uni.showModal({
					title: '添加学科', editable: true, placeholderText: '如：数学、物理',
					success: async (res) => {
						if (!res.confirm) return;
						const name = String(res.content || '').trim();
						if (!name) { uni.showToast({ title: '学科名称不能为空', icon: 'none' }); return; }
						try {
							await addSubjectApi(name);
							await this.loadSubjects();
							const s = this.subjects.find((x) => x.name === name);
							if (s) { this.teacherSubjectId = s.id; this.subjectIdx = this.subjects.findIndex((x) => x.id === s.id) + 1; }
							uni.showToast({ title: `已创建「${name}」`, icon: 'success' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			confirmDeleteSubject() {
				const name = this.selectedSubjectName;
				uni.showModal({
					title: '删除学科', content: `删除「${name}」后，该学科下的资料将自动归入默认"英语"，确定删除？`, confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await deleteSubjectApi(this.teacherSubjectId);
							this.teacherSubjectId = null; this.subjectIdx = 0;
							await this.loadSubjects();
							uni.showToast({ title: '已删除，资料归入英语', icon: 'none' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			async doSearchTeachers() {
				const kw = String(this.teacherKeyword || '').trim();
				if (!kw) { uni.showToast({ title: '请输入教师账号或姓名', icon: 'none' }); return; }
				try {
					this.teacherResults = (await searchTeachers(kw)) || [];
					if (!this.teacherResults.length) uni.showToast({ title: '未找到匹配的教师', icon: 'none' });
				} catch (e) { /* 已提示 */ }
			},
			async loadAllTeachers() {
				try { this.allTeachers = (await searchTeachers('')) || []; } catch (e) { this.allTeachers = []; }
			},
			onTeacherPick(e) {
				const idx = Number(e.detail.value);
				const t = this.allTeachers[idx];
				if (t) this.viewTeacherResources(t);
			},
			viewTeacherResources(t) {
				this.viewingTeacher = { id: t.id, name: t.realName || t.username };
				this.teacherResults = []; this.teacherKeyword = '';
				this.reload();
			},
			exitSharedView() { this.viewingTeacher = null; this.reload(); },
			copyToMine(row) {
				uni.showModal({
					title: '复制资料', content: `确定将「${row.title}」复制到自己的资料库？`,
					success: async (r) => {
						if (!r.confirm) return;
						try { await copyResource(row.id); uni.showToast({ title: '已复制到我的库', icon: 'success' }); this.exitSharedView(); } catch (e) { /* 已提示 */ }
					}
				});
			},
			copyAllFromTeacher() {
				uni.showModal({
					title: '一键复制全部', content: '确定将该教师的全部资料复制到自己的库？（已复制过的会自动跳过）',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							const n = await copyAllResources(this.viewingTeacher.id);
							uni.showToast({ title: `已复制 ${n} 条`, icon: 'success' });
							this.exitSharedView();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			onDelete(row) {
				uni.showModal({
					title: '删除资料', content: `确定删除「${row.title}」吗？`, confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await deleteResource(row.id); uni.showToast({ title: '已删除', icon: 'success' }); this.reload(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.top-bar { padding: 4rpx 0 0; }
	.subj-picker { height: 60rpx; line-height: 60rpx; padding: 0 22rpx; border-radius: 999rpx; background: #e6fffa; color: #0d9488; font-size: 24rpx; }
	.tb-btn { height: 60rpx; line-height: 60rpx; padding: 0 22rpx; border-radius: 999rpx; background: #f2f4f8; color: #4b5563; font-size: 24rpx; }
	.tb-btn.danger { background: #fdecef; color: #e11d48; }

	.share-bar { margin: 16rpx 24rpx 0; padding: 20rpx 24rpx; border-radius: 16rpx; background: linear-gradient(120deg, #0d9488, #14b8a6); color: #ffffff; }
	.share-txt { font-size: 26rpx; font-weight: 600; }
	.share-ops { margin-top: 14rpx; display: flex; gap: 16rpx; }
	.share-ops .mini-btn { background: rgba(255, 255, 255, 0.9); }

	.tsearch { margin: 16rpx 24rpx 0; }
	.ts-pick { display: block; margin-bottom: 16rpx; }
	.ts-pick-in { height: 76rpx; line-height: 76rpx; padding: 0 20rpx; border-radius: 12rpx; background: #e6fffa; color: #0d9488; font-size: 26rpx; font-weight: 600; text-align: center; }
	.ts-row { display: flex; gap: 16rpx; align-items: center; }
	.ts-input { flex: 1; height: 76rpx; line-height: 76rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 26rpx; color: #303133; }
	.ts-btn { flex-shrink: 0; height: 76rpx; line-height: 76rpx; padding: 0 30rpx; border-radius: 12rpx; background: #e6fffa; color: #0d9488; font-size: 26rpx; font-weight: 600; }
	.ts-results { margin-top: 16rpx; border-top: 1rpx dashed #eef0f4; padding-top: 8rpx; }
	.ts-item { display: flex; align-items: center; justify-content: space-between; padding: 14rpx 0; }
	.ts-info { display: flex; align-items: baseline; gap: 12rpx; min-width: 0; }
	.ts-name { font-size: 27rpx; color: #1f2d52; font-weight: 600; }
	.ts-acc { font-size: 22rpx; color: #909399; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.rcard { margin-bottom: 20rpx; }
	.rtitle { font-size: 30rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.rtag { flex-shrink: 0; font-size: 22rpx; color: #0d9488; background: #e6fffa; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.rcontent { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; align-items: center; flex-wrap: wrap; }
</style>
