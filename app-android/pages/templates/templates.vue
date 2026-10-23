<template>
	<view class="page">
		<view class="top-bar">
			<text class="tip">同一科目 + 同一单子类型只能有一个模板；发布家教单时可直接套用，再改细节即可。</text>
			<view class="btn btn-primary add-btn" @click="goAdd">➕ 新增模板</view>
		</view>

		<view class="list">
			<view v-for="row in list" :key="row.id" class="card tcard">
				<view class="row-between">
					<view class="row thead" style="flex-wrap:wrap;gap:10rpx;">
						<text class="tsubject">{{ row.subject }}</text>
						<text class="tname">{{ row.name }}</text>
					</view>
					<status-tag :type="row.jobType === 2 ? 'warning' : 'primary'" :text="row.jobType === 2 ? '合作单' : '家教单'" />
				</view>

				<view class="muted tdefault">{{ defaultLine(row) }}</view>
				<view class="muted tline" v-if="row.situation">📖 {{ row.situation }}</view>
				<view class="muted tline" v-if="row.requirement">✅ {{ row.requirement }}</view>

				<view class="row tfoot">
					<status-tag :type="row.payToApply === 1 ? 'danger' : 'info'" :text="row.payToApply === 1 ? '需缴费' : '免费'" />
					<text class="muted ttime">更新于 {{ fmtTime(row.updateTime) }}</text>
				</view>

				<view class="ops">
					<text class="mini-btn mini-btn-primary" @click="goEdit(row)">编辑</text>
					<text class="mini-btn mini-btn-success" @click="useNow(row)">用此模板发布</text>
					<text class="mini-btn mini-btn-danger" @click="onDelete(row)">删除</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="暂无模板，点上方新增" />
			<load-more :status="loading ? 'loading' : ''" />
		</view>
	</view>
</template>

<script>
	import { adminJobTemplates, adminDeleteJobTemplate } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	export default {
		data() { return { list: [], loading: false }; },
		onShow() { this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtTime,
			async load() {
				this.loading = true;
				try {
					const data = await adminJobTemplates({});
					this.list = data.list || [];
				} catch (e) { /* 已提示 */ } finally {
					this.loading = false;
				}
			},
			defaultLine(row) {
				const s = [row.grade, row.province, row.city, row.frequency, row.salary ? row.salary + '元/h' : ''].filter(Boolean).join(' · ');
				return s || '—';
			},
			goAdd() { uni.navigateTo({ url: '/pages/template-form/template-form' }); },
			goEdit(row) { uni.navigateTo({ url: `/pages/template-form/template-form?id=${row.id}` }); },
			useNow(row) { uni.navigateTo({ url: `/pages/job-form/job-form?type=${row.jobType || 1}&tplId=${row.id}` }); },
			onDelete(row) {
				uni.showModal({
					title: '删除模板',
					content: `确定删除科目「${row.subject}」的模板「${row.name}」吗？`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try { await adminDeleteJobTemplate(row.id); uni.showToast({ title: '已删除', icon: 'success' }); this.load(); } catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.top-bar { padding: 20rpx 24rpx 0; }
	.tip { display: block; font-size: 22rpx; color: #909399; line-height: 1.5; margin-bottom: 16rpx; }
	.add-btn { width: 100%; }

	.list { padding: 20rpx 24rpx 40rpx; }
	.tcard { margin-bottom: 20rpx; }
	.thead { gap: 12rpx; }
	.tsubject { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.tname { font-size: 26rpx; color: #4b5563; }
	.tdefault { margin-top: 12rpx; font-size: 24rpx; }
	.tline { margin-top: 8rpx; font-size: 24rpx; line-height: 1.5; }
	.tfoot { margin-top: 14rpx; gap: 16rpx; }
	.ttime { font-size: 22rpx; margin-left: auto; }
	.ops { margin-top: 16rpx; display: flex; gap: 14rpx; flex-wrap: wrap; }
</style>
