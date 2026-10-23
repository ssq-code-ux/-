<template>
	<view class="page">
		<!-- 选择学生 -->
		<view class="card pick-card">
			<text class="fl">选择学生</text>
			<picker mode="selector" :range="pickerRange" :value="selectedIdx" @change="onPick">
				<view class="picker-in">{{ pickerRange[selectedIdx] || '请选择学生' }} ▾</view>
			</picker>
			<view class="assign-row">
				<view class="btn btn-primary assign-btn" :class="{ 'btn-disabled': !selectedStudentId }" @click="goAssign">📝 布置作业</view>
				<view class="btn btn-ai assign-btn" :class="{ 'btn-disabled': !selectedStudentId }" @click="goSheet">✨ AI 出卷</view>
			</view>
		</view>

		<template v-if="queried">
			<!-- 统计条 -->
			<view class="stat-head">
				<view class="stat-stu">
					<text class="stat-name">{{ queried.realName }}</text>
					<text class="stat-acc" v-if="queried.username">@{{ queried.username }}</text>
				</view>
				<view class="stat-nums">
					<text>共 {{ list.length }} 份</text>
					<text>已交 {{ countSubmitted }}</text>
					<text>已批 {{ counts.graded }}</text>
				</view>
			</view>

			<!-- 状态筛选 -->
			<view v-if="subjectsInList.length" class="chips subj-chips">
				<text class="chip subj-chip" :class="{ on: subjectFilter === null }" @click="subjectFilter = null">全部学科</text>
				<text v-for="s in subjectsInList" :key="s" class="chip subj-chip" :class="{ on: subjectFilter === s }" @click="subjectFilter = subjectFilter === s ? null : s">{{ s }}</text>
			</view>

			<view class="chips">
				<text class="chip" :class="{ on: statusFilter === null }" @click="statusFilter = null">全部 {{ subjectScoped.length }}</text>
				<text class="chip c-pending" :class="{ on: statusFilter === '未提交' }" @click="toggle('未提交')">未提交 {{ counts.pending }}</text>
				<text class="chip c-submitted" :class="{ on: statusFilter === '已提交' }" @click="toggle('已提交')">已提交 {{ counts.submitted }}</text>
				<text class="chip c-graded" :class="{ on: statusFilter === '已批改' }" @click="toggle('已批改')">已批改 {{ counts.graded }}</text>
				<text class="chip c-redo" :class="{ on: statusFilter === '需重做' }" @click="toggle('需重做')">需重做 {{ counts.redo }}</text>
			</view>

			<view class="list">
				<view v-for="h in displayed" :key="h.id" class="card hcard">
					<view class="row-between">
						<text class="htitle">{{ h.title }}</text>
						<status-tag :type="statusTag(h.status)" :text="h.status" />
					</view>
					<view class="hcontent">{{ h.content || '暂无文字内容' }}</view>
					<view class="hmeta">
						<text class="htag">{{ h.type === 'answer_sheet' ? '📄 答题卡' : (h.type || '作业') }}{{ h.subject ? ' · ' + h.subject : '' }}</text>
						<text class="hdeadline">📅 {{ h.deadline ? fmtTime(h.deadline) : '无截止时间' }}</text>
					</view>
					<view class="ops">
						<text class="mini-btn" @click="goDetail(h)">详情</text>
						<text v-if="h.type !== 'answer_sheet'" class="mini-btn mini-btn-primary" @click="goEdit(h)">编辑</text>
						<text class="mini-btn mini-btn-danger" @click="onDelete(h)">删除</text>
						<text v-if="h.type === 'answer_sheet' && h.status !== '未提交'" class="mini-btn mini-btn-primary" @click="goSheetReview(h)">📄 答题卡批改</text>
					</view>
				</view>

				<empty-state v-if="!loading && !displayed.length" text="该学生暂无匹配的作业" />
				<load-more :status="loading ? 'loading' : ''" />
			</view>
		</template>

		<empty-state v-else-if="!loading" text="请先选择学生查看作业" />
	</view>
</template>

<script>
	import { students, homeworkList, deleteHomework } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TAG = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };

	export default {
		data() {
			return {
				studentList: [], selectedIdx: 0, selectedStudentId: null,
				queried: null, list: [], statusFilter: null, subjectFilter: null, loading: false,
				pendingStudentId: null
			};
		},
		computed: {
			pickerRange() {
				return ['请选择学生'].concat(this.studentList.map((s) => `${s.realName || '未命名'} (@${s.username})`));
			},
			subjectsInList() {
				const order = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];
				const set = new Set(this.list.map((h) => h.subject || '英语'));
				return [...set].sort((a, b) => {
					const ia = order.indexOf(a); const ib = order.indexOf(b);
					return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
				});
			},
			subjectScoped() {
				if (!this.subjectFilter) return this.list;
				return this.list.filter((h) => (h.subject || '英语') === this.subjectFilter);
			},
			counts() {
				return {
					pending: this.subjectScoped.filter((h) => h.status === '未提交').length,
					submitted: this.subjectScoped.filter((h) => h.status === '已提交').length,
					graded: this.subjectScoped.filter((h) => h.status === '已批改').length,
					redo: this.subjectScoped.filter((h) => h.status === '需重做').length
				};
			},
			countSubmitted() { return this.subjectScoped.filter((h) => h.status !== '未提交').length; },
			displayed() {
				if (!this.statusFilter) return this.subjectScoped;
				return this.subjectScoped.filter((h) => h.status === this.statusFilter);
			}
		},
		onLoad(opt) { this.pendingStudentId = opt && opt.studentId ? opt.studentId : null; },
		onShow() {
			if (!guardRole('teacher')) return;
			this.loadStudents();
		},
		onPullDownRefresh() {
			const p = this.selectedStudentId ? this.query() : Promise.resolve();
			Promise.all([this.loadStudents(), p]).finally(() => uni.stopPullDownRefresh());
		},
		methods: {
			fmtTime,
			statusTag(s) { return TAG[s] || 'info'; },
			async loadStudents() {
				try {
					this.studentList = (await students()) || [];
				} catch (e) { this.studentList = []; }
				// 首次进入且带了 studentId：预选该学生
				if (this.pendingStudentId && !this.selectedStudentId) {
					const idx = this.studentList.findIndex((s) => String(s.id) === String(this.pendingStudentId));
					if (idx >= 0) { this.selectedIdx = idx + 1; this.selectedStudentId = this.studentList[idx].id; this.queried = this.studentList[idx]; }
					this.pendingStudentId = null;
				}
				// 已有选中学生：刷新其作业（布置/批改返回后能看到最新）
				if (this.selectedStudentId) this.query();
			},
			onPick(e) {
				const idx = Number(e.detail.value);
				this.selectedIdx = idx;
				if (idx === 0) { this.selectedStudentId = null; this.queried = null; this.list = []; this.statusFilter = null; this.subjectFilter = null; return; }
				const s = this.studentList[idx - 1];
				this.selectedStudentId = s.id;
				this.queried = s;
				this.statusFilter = null;
				this.subjectFilter = null;
				this.query();
			},
			async query() {
				if (!this.selectedStudentId) return;
				this.loading = true;
				try { this.list = (await homeworkList(this.selectedStudentId)) || []; this.subjectFilter = null; } catch (e) { this.list = []; } finally { this.loading = false; }
			},
			toggle(s) { this.statusFilter = this.statusFilter === s ? null : s; },
			goAssign() {
				if (!this.selectedStudentId) { uni.showToast({ title: '请先选择学生', icon: 'none' }); return; }
				uni.navigateTo({ url: `/pages/teacher/homework-form?studentId=${this.selectedStudentId}&studentName=${encodeURIComponent((this.queried && this.queried.realName) || '')}` });
			},
			goDetail(h) { uni.navigateTo({ url: `/pages/teacher/homework-detail?id=${h.id}` }); },
			goSheet() {
				if (!this.selectedStudentId) { uni.showToast({ title: '请先选择学生', icon: 'none' }); return; }
				uni.navigateTo({ url: `/pages/teacher/answer-sheet-form?studentId=${this.selectedStudentId}&studentName=${encodeURIComponent((this.queried && this.queried.realName) || '')}` });
			},
			goSheetReview(h) { uni.navigateTo({ url: `/pages/teacher/answer-sheet-review?id=${h.id}` }); },
			goEdit(h) { uni.navigateTo({ url: `/pages/teacher/homework-form?id=${h.id}&studentId=${h.studentId || this.selectedStudentId}` }); },
			onDelete(h) {
				uni.showModal({
					title: '删除作业', content: `确定删除「${h.title}」吗？相关提交和批改记录也将删除。`, confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await deleteHomework(h.id); uni.showToast({ title: '已删除', icon: 'success' }); this.query(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.pick-card { padding-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.picker-in { height: 84rpx; line-height: 84rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 24rpx; font-size: 28rpx; color: #303133; }
	.assign-btn { margin-top: 20rpx; width: 100%; text-align: center; }
	.assign-row { display: flex; gap: 16rpx; }
	.assign-row .assign-btn { flex: 1; margin-top: 20rpx; }
	.btn-ai { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; }

	.stat-head { margin: 20rpx 24rpx 0; padding: 22rpx 26rpx; border-radius: 16rpx; background: linear-gradient(120deg, #0d9488, #3b82f6); color: #ffffff; }
	.stat-stu { display: flex; align-items: baseline; gap: 12rpx; }
	.stat-name { font-size: 32rpx; font-weight: 700; }
	.stat-acc { font-size: 23rpx; opacity: 0.85; }
	.stat-nums { margin-top: 12rpx; display: flex; gap: 28rpx; font-size: 24rpx; opacity: 0.95; }

	.chips { display: flex; gap: 12rpx; flex-wrap: wrap; padding: 20rpx 24rpx 0; }
	.subj-chips { padding-top: 16rpx; }
	.subj-chip.on { background: #6366f1; }
	.chip { padding: 10rpx 22rpx; border-radius: 999rpx; background: #ffffff; color: #6b7280; font-size: 24rpx; border: 2rpx solid #eef0f4; }
	.chip.on { color: #ffffff; border-color: transparent; }
	.chip.c-pending.on { background: #909399; }
	.chip.c-submitted.on { background: #e6a23c; }
	.chip.c-graded.on { background: #16a34a; }
	.chip.c-redo.on { background: #e11d48; }
	.chip:not(.c-pending):not(.c-submitted):not(.c-graded):not(.c-redo).on { background: #0d9488; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.hcard { margin-bottom: 20rpx; }
	.htitle { font-size: 30rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.hcontent { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
	.hmeta { margin-top: 12rpx; display: flex; gap: 20rpx; flex-wrap: wrap; }
	.htag { font-size: 22rpx; color: #0d9488; background: #e6fffa; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.hdeadline { font-size: 22rpx; color: #909399; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; align-items: center; flex-wrap: wrap; }
	.defer { font-size: 21rpx; color: #b0b4bb; margin-left: auto; }
</style>
