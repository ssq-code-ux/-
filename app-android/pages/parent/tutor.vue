<template>
	<view class="page">
		<view class="head">
			<view class="head-hi">🔍 找家教</view>
			<view class="head-sub">只需留下手机号，管理员审核后会发布到教师接单广场并电话与您确认；老师投递的简历会实时展示在这里。</view>
			<view class="pub-btn" hover-class="pub-btn-hover" @click="goForm">📝 发布需求</view>
		</view>

		<view class="list">
			<view v-for="d in demands" :key="d.id" class="card dcard" :class="{ del: d.deleteRequested === 1 }">
				<view class="row-between">
					<view class="row dtitle" style="flex-wrap:wrap;gap:10rpx;">
						<text class="dsubject">{{ d.subject || '学科待确认' }}</text>
						<text class="muted">{{ d.grade || '年级未填' }}<text v-if="d.studentGender"> · {{ d.studentGender }}</text></text>
					</view>
					<text v-if="d.salary" class="dsalary">{{ d.salary }}元/h</text>
				</view>

				<view class="dtags">
					<status-tag :type="d.status === 1 ? 'success' : 'warning'" :text="d.status === 1 ? '已发布' : '待管理员发布'" />
					<status-tag v-if="d.jobStatus" :type="d.jobStatus === '开放中' ? 'primary' : 'info'" :text="'家教单' + d.jobStatus" />
					<status-tag v-if="d.status === 1" type="info" :text="(d.applyCount || 0) + ' 位老师已投递'" />
					<status-tag v-if="d.deleteRequested === 1" type="danger" text="已申请删除，待确认" />
				</view>

				<view v-if="d.demandNo" class="dno muted">编号 {{ d.demandNo }}</view>
				<view v-if="d.studentLevel" class="dline">📖 学生程度：{{ d.studentLevel }}</view>
				<view v-if="d.otherRequire" class="dline">✅ 对老师要求：{{ d.otherRequire }}</view>

				<view class="dfoot">
					<text class="dtime muted">{{ fmtDate(d.createTime) }} 提交</text>
					<view class="dops">
						<text v-if="d.status === 1" class="mini-btn mini-btn-primary" @click="goApps(d)">📄 查看简历（{{ d.applyCount || 0 }}）</text>
						<text v-if="d.deleteRequested !== 1" class="mini-btn mini-btn-danger" @click="onDelete(d)">🗑 申请删除</text>
					</view>
				</view>
			</view>

			<empty-state v-if="!loading && !demands.length" text="还没有提交过需求，点上方「发布需求」开始吧" />
		</view>
	</view>
</template>

<script>
	import { myParentDemands, parentRequestDeleteDemand } from '../../api/index.js';
	import { fmtDate } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				demands: [],
				loading: false
			};
		},
		onShow() {
			if (!guardRole('parent')) return;
			this.load();
		},
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtDate,
			async load() {
				this.loading = true;
				try {
					const data = await myParentDemands({ page: 1, size: 50 });
					this.demands = data.records || data.list || [];
				} catch (e) { this.demands = []; } finally {
					this.loading = false;
				}
			},
			goForm() { uni.navigateTo({ url: '/pages/parent/demand-form' }); },
			goApps(d) { uni.navigateTo({ url: `/pages/parent/demand-apps?id=${d.id}&subject=${encodeURIComponent(d.subject || '')}&grade=${encodeURIComponent(d.grade || '')}` }); },
			onDelete(d) {
				uni.showModal({
					title: '申请删除需求',
					content: `确定申请删除这条找家教需求（${d.subject || '学科待确认'}）吗？提交后需管理员确认，确认通过后需求${d.jobId ? '及关联家教单' : ''}将被删除。`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await parentRequestDeleteDemand(d.id);
							uni.showToast({ title: '删除申请已提交', icon: 'success' });
							this.load();
						} catch (e) { /* 已提示 */ }
					}
				});
			}
		}
	};
</script>

<style scoped>
	.head { padding: 32rpx 32rpx 48rpx; background: linear-gradient(135deg, #f97316 0%, #ec4899 100%); border-bottom-left-radius: 36rpx; border-bottom-right-radius: 36rpx; position: relative; }
	.head-hi { font-size: 36rpx; font-weight: 700; color: #ffffff; }
	.head-sub { margin-top: 10rpx; font-size: 23rpx; color: rgba(255, 255, 255, 0.9); line-height: 1.6; padding-right: 40rpx; }
	.pub-btn { margin-top: 20rpx; display: inline-block; padding: 14rpx 34rpx; font-size: 26rpx; font-weight: 600; color: #f97316; background: #ffffff; border-radius: 40rpx; }
	.pub-btn-hover { opacity: 0.85; }

	.list { padding: 20rpx 24rpx 40rpx; margin-top: -20rpx; }
	.dcard { margin-bottom: 20rpx; border-left: 6rpx solid transparent; }
	.dcard.del { border-left-color: #e11d48; background: #fff5f6; }
	.dtitle { flex: 1; min-width: 0; }
	.dsubject { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.dsalary { flex-shrink: 0; padding: 6rpx 20rpx; border-radius: 999rpx; background: linear-gradient(135deg, #8b7cf6, #6c5ce7); color: #ffffff; font-weight: 700; font-size: 24rpx; }
	.dtags { margin-top: 14rpx; display: flex; gap: 10rpx; flex-wrap: wrap; }
	.dno { margin-top: 12rpx; font-family: Consolas, Monaco, monospace; }
	.dline { margin-top: 10rpx; font-size: 25rpx; color: #606266; line-height: 1.6; }
	.dfoot { margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx dashed #eef0f4; }
	.dtime { font-size: 22rpx; }
	.dops { display: flex; flex-wrap: wrap; gap: 4rpx; margin-top: 6rpx; }
</style>
