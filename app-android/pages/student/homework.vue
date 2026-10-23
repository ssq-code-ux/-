<template>
	<view class="page">
		<!-- 逾期提醒条 -->
		<view v-if="overdueItems.length" class="overdue-bar">
			<text class="od-icon">⏰</text>
			<view class="od-body">
				<text class="od-title">有 {{ overdueItems.length }} 份作业已逾期</text>
				<text class="od-sub">请尽快提交，别让作业拖太久哦</text>
			</view>
		</view>

		<!-- 状态筛选 -->
		<view v-if="subjectsInList.length" class="chips subj-chips">
			<text class="chip subj-chip" :class="{ on: subjectFilter === null }" @click="subjectFilter = null">全部学科</text>
			<text v-for="s in subjectsInList" :key="s" class="chip subj-chip" :class="{ on: subjectFilter === s }" @click="subjectFilter = subjectFilter === s ? null : s">{{ s }}</text>
		</view>

		<view class="chips">
			<text class="chip" :class="{ on: filter === null }" @click="filter = null">全部 {{ subjectScoped.length }}</text>
			<text class="chip c-pending" :class="{ on: filter === '未提交' }" @click="toggle('未提交')">未提交 {{ counts.pending }}</text>
			<text class="chip c-redo" :class="{ on: filter === '需重做' }" @click="toggle('需重做')">需重做 {{ counts.redo }}</text>
			<text class="chip c-submitted" :class="{ on: filter === '已提交' }" @click="toggle('已提交')">已提交 {{ counts.submitted }}</text>
			<text class="chip c-graded" :class="{ on: filter === '已批改' }" @click="toggle('已批改')">已批改 {{ counts.graded }}</text>
		</view>

		<view class="list">
			<template v-for="grp in bySubject" :key="grp.subject">
			<view class="subj-head"><text class="subj-name">{{ grp.subject }}</text><text class="subj-cnt">{{ grp.items.length }} 份</text></view>
			<view v-for="h in grp.items" :key="h.id" class="card hcard" :class="{ 'hcard-overdue': isOverdue(h) }">
				<view class="row-between">
					<text class="htitle">{{ h.title }}</text>
					<status-tag :type="statusTag(h.status)" :text="h.status" />
				</view>
				<view class="hcontent">{{ h.content || '暂无文字内容' }}</view>
				<view class="hmeta">
					<text class="htag">{{ h.type === 'answer_sheet' ? '📄 答题卡' : (h.subject || '作业') }}</text>
					<text class="hdeadline" :class="{ late: isOverdue(h) }">📅 {{ h.deadline ? fmtTime(h.deadline) : '无截止时间' }}</text>
				</view>
				<view class="ops">
					<text class="mini-btn" @click="goDetail(h)">详情</text>
					<text v-if="canSubmit(h) && h.type !== 'answer_sheet'" class="mini-btn mini-btn-primary" @click="goSubmit(h)">{{ h.status === '需重做' ? '重新提交' : '提交作业' }}</text>
					<text v-if="canSubmit(h) && h.type === 'answer_sheet'" class="mini-btn mini-btn-primary" @click="goPaper(h)">✍️ 立即作答</text>
					<text v-if="h.status === '已批改' || (h.type === 'answer_sheet' && h.status === '已提交')" class="mini-btn mini-btn-success" @click="goReview(h)">批改结果</text>
				</view>
</view>
				</template>

				<empty-state v-if="!loading && !displayed.length" text="暂无匹配的作业" />
			<load-more :status="loading ? 'loading' : ''" />
		</view>
	</view>
</template>

<script>
	import { homeworkList, overdueHomework } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const TAG = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };

	export default {
		data() {
			return { list: [], overdueItems: [], filter: null, subjectFilter: null, loading: false };
		},
		computed: {
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
					redo: this.subjectScoped.filter((h) => h.status === '需重做').length,
					submitted: this.subjectScoped.filter((h) => h.status === '已提交').length,
					graded: this.subjectScoped.filter((h) => h.status === '已批改').length
				};
			},
			displayed() {
				if (!this.filter) return this.subjectScoped;
				return this.subjectScoped.filter((h) => h.status === this.filter);
			},
			bySubject() {
				const order = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];
				const g = new Map();
				for (const h of this.displayed) {
					const s = h.subject || '英语';
					if (!g.has(s)) g.set(s, []);
					g.get(s).push(h);
				}
				return [...g.entries()]
					.sort((a, b) => {
						const ia = order.indexOf(a[0]); const ib = order.indexOf(b[0]);
						return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
					})
					.map(([subject, items]) => ({ subject, items }));
			}
		},
		onShow() {
			if (!guardRole('student')) return;
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtTime,
			statusTag(s) { return TAG[s] || 'info'; },
			isOverdue(h) {
				if (!h.deadline) return false;
				if (h.status !== '未提交' && h.status !== '需重做') return false;
				return new Date(String(h.deadline).replace(/-/g, '/')).getTime() < Date.now();
			},
			canSubmit(h) { return h.status === '未提交' || h.status === '需重做'; },
			toggle(s) { this.filter = this.filter === s ? null : s; },
			async load() {
				this.loading = true;
				try {
					this.list = (await homeworkList()) || [];
					this.overdueItems = (await overdueHomework().catch(() => [])) || [];
				} catch (e) { this.list = []; } finally { this.loading = false; }
			},
			goDetail(h) { uni.navigateTo({ url: `/pages/student/homework-detail?id=${h.id}` }); },
			goSubmit(h) { uni.navigateTo({ url: `/pages/student/homework-submit?id=${h.id}` }); },
			goReview(h) { uni.navigateTo({ url: `/pages/student/homework-review?id=${h.id}` }); },
			goPaper(h) { uni.navigateTo({ url: `/pages/student/answer-sheet?id=${h.id}` }); }
		}
	};
</script>

<style scoped>
	.overdue-bar { margin: 20rpx 24rpx 0; padding: 22rpx 24rpx; border-radius: 16rpx; background: linear-gradient(120deg, #ef4444, #f97316); color: #ffffff; display: flex; align-items: center; gap: 18rpx; }
	.od-icon { font-size: 44rpx; }
	.od-body { flex: 1; min-width: 0; }
	.od-title { display: block; font-size: 28rpx; font-weight: 700; }
	.od-sub { display: block; margin-top: 6rpx; font-size: 22rpx; opacity: 0.9; }

	.chips { display: flex; gap: 12rpx; flex-wrap: wrap; padding: 20rpx 24rpx 0; }
	.subj-chips { padding-top: 16rpx; }
	.subj-chip.on { background: #6366f1; }
	.chip { padding: 10rpx 22rpx; border-radius: 999rpx; background: #ffffff; color: #6b7280; font-size: 24rpx; border: 2rpx solid #eef0f4; }
	.chip.on { color: #ffffff; border-color: transparent; }
	.chip.c-pending.on { background: #909399; }
	.chip.c-redo.on { background: #e11d48; }
	.chip.c-submitted.on { background: #e6a23c; }
	.chip.c-graded.on { background: #16a34a; }
	.chip:not(.c-pending):not(.c-redo):not(.c-submitted):not(.c-graded).on { background: #6366f1; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.subj-head { display: flex; align-items: center; gap: 12rpx; margin: 8rpx 0 12rpx; }
	.subj-name { font-size: 28rpx; font-weight: 700; color: #1f2d52; padding-left: 14rpx; border-left: 6rpx solid #6366f1; }
	.subj-cnt { font-size: 22rpx; color: #909399; }
	.hcard { margin-bottom: 20rpx; }
	.hcard-overdue { border-left: 6rpx solid #ef4444; }
	.htitle { font-size: 30rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.hcontent { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
	.hmeta { margin-top: 12rpx; display: flex; gap: 20rpx; flex-wrap: wrap; }
	.htag { font-size: 22rpx; color: #6366f1; background: #eef2ff; border-radius: 8rpx; padding: 4rpx 14rpx; }
	.hdeadline { font-size: 22rpx; color: #909399; }
	.hdeadline.late { color: #ef4444; font-weight: 600; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; align-items: center; flex-wrap: wrap; }
</style>
