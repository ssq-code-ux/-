<template>
	<view class="page">
		<!-- 统计卡 -->
		<view class="stat-row">
			<view class="stat sc-blue">
				<text class="stat-val">{{ fmt0(analysis.submitRate) }}<text class="unit">%</text></text>
				<text class="stat-label">📤 作业提交率</text>
			</view>
			<view class="stat sc-green">
				<text class="stat-val">{{ fmt0(analysis.averageScore) }}<text class="unit">分</text></text>
				<text class="stat-label">💯 作业平均分</text>
			</view>
			<view class="stat sc-orange">
				<text class="stat-val">{{ errorTotal }}<text class="unit">个</text></text>
				<text class="stat-label">📋 测验错题</text>
			</view>
		</view>

		<!-- 逾期警示 -->
		<view v-if="overdueCount > 0" class="alert">⚠️ 有 {{ overdueCount }} 项作业已逾期未提交，请督促孩子尽快完成</view>

		<!-- 作业完成情况 -->
		<view class="card">
			<view class="card-title">📝 作业完成情况<text class="card-sub">最近 {{ homeworks.length }} 项</text></view>
			<scroll-view scroll-x class="tabs" :show-scrollbar="false">
				<view class="tabs-in">
					<text v-for="t in statusTabs" :key="t.v" class="tab" :class="{ on: statusFilter === t.v }" @click="statusFilter = t.v">{{ t.label }}</text>
				</view>
			</scroll-view>
			<view class="hw-list">
				<view v-for="h in filteredHomeworks" :key="h.id" class="hw" hover-class="hw-hover" @click="goHw(h)">
					<view class="hw-main">
						<view class="hw-title">{{ h.title }}</view>
						<view class="hw-time muted">截止 {{ fmtTime(h.deadline) }}</view>
					</view>
					<status-tag :type="hwTag(h)" :text="hwText(h)" />
				</view>
				<view v-if="!filteredHomeworks.length" class="muted hw-empty">暂无该状态的作业</view>
			</view>
		</view>

		<!-- 成绩趋势 -->
		<view class="card" v-if="scoreTrend.length">
			<view class="card-title">📈 最近已批改成绩趋势</view>
			<view class="bars">
				<view v-for="(p, i) in scoreTrend" :key="i" class="bar-col">
					<text class="bar-score">{{ p.score }}</text>
					<view class="bar" :style="{ height: barH(p.score) + 'rpx' }"></view>
					<text class="bar-label">{{ shortTitle(p.title) }}</text>
				</view>
			</view>
		</view>

		<!-- 学习建议 -->
		<view class="card ai-card">
			<view class="card-title">
				<text>✨ 学习建议 <text class="ai-badge">每日更新</text></text>
				<text class="ai-btn" @click="genAdvice">{{ aiAdvice ? '🔄 重新生成' : '✨ 立即生成' }}</text>
			</view>
			<view v-if="aiLoading" class="muted ai-loading">AI 正在结合 {{ childName }} 的最新学习数据生成，约 5~15 秒…</view>
			<view v-else-if="aiAdvice" class="ai-body">
				<rich-text class="ai-text" :nodes="renderAdvice(aiAdvice)"></rich-text>
				<view v-if="aiGenTime" class="muted ai-time">🕘 更新于 {{ fmtGenTime(aiGenTime) }}</view>
			</view>
			<view v-else-if="analysis.suggestions && analysis.suggestions.length" class="ai-body">
				<view class="muted ai-fallback">⚠️ 今日 AI 建议还没生成，以下为系统基础建议</view>
				<view v-for="(s, i) in analysis.suggestions" :key="i" class="sug">· {{ s }}</view>
			</view>
			<view v-else class="muted ai-loading">学习建议每天早上自动更新，也可点右上角「立即生成」</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-primary" @click="goReport">📖 查看学习周报</view>
		</view>
	</view>
</template>

<script>
	import { parentChildAnalysis, parentChildHomeworks, parentChildAiContent, parentChildAiRefresh } from '../../api/index.js';
	import { fmtTime } from '../../utils/format.js';

	export default {
		data() {
			return {
				childId: null,
				childName: '孩子',
				analysis: {},
				homeworks: [],
				statusFilter: 'all',
				statusTabs: [
					{ v: 'all', label: '全部' }, { v: 'overdue', label: '逾期' },
					{ v: '已批改', label: '已批改' }, { v: '已提交', label: '已提交' }, { v: '未提交', label: '未提交' }
				],
				aiAdvice: '',
				aiGenTime: null,
				aiLoading: false
			};
		},
		computed: {
			errorTotal() {
				const m = this.analysis.errorTypeCount || {};
				return Object.values(m).reduce((a, b) => a + (b || 0), 0);
			},
			overdueCount() { return this.homeworks.filter((h) => h.overdue).length; },
			scoreTrend() { return this.analysis.scoreTrend || []; },
			filteredHomeworks() {
				if (this.statusFilter === 'all') return this.homeworks;
				if (this.statusFilter === 'overdue') return this.homeworks.filter((h) => h.overdue);
				return this.homeworks.filter((h) => h.status === this.statusFilter);
			}
		},
		onLoad(opt) {
			this.childId = opt.id;
			this.childName = opt.name ? decodeURIComponent(opt.name) : '孩子';
			uni.setNavigationBarTitle({ title: this.childName + ' 的学习' });
		},
		onShow() { if (this.childId) this.loadAll(); },
		onPullDownRefresh() { this.loadAll().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtTime,
			fmt0(v) { return v == null || isNaN(v) ? 0 : Math.round(Number(v)); },
			async loadAll() {
				const [a, h] = await Promise.all([
					parentChildAnalysis(this.childId).catch(() => ({})),
					parentChildHomeworks(this.childId).catch(() => [])
				]);
				this.analysis = a || {};
				this.homeworks = h || [];
				this.aiAdvice = '';
				this.aiGenTime = null;
				this.loadAdvice();
			},
			async loadAdvice() {
				try {
					const res = await parentChildAiContent(this.childId, 'daily_advice');
					this.aiAdvice = (res && res.content) || '';
					this.aiGenTime = (res && res.genTime) || null;
				} catch (e) { /* 静默降级 */ }
			},
			async genAdvice() {
				if (this.aiLoading) return;
				this.aiLoading = true;
				this.aiAdvice = '';
				try {
					const res = await parentChildAiRefresh(this.childId, 'daily_advice');
					this.aiAdvice = (res && res.content) || '';
					this.aiGenTime = (res && res.genTime) || null;
					if (!this.aiAdvice) uni.showToast({ title: 'AI 服务暂不可用，已展示基础建议', icon: 'none' });
				} catch (e) {
					uni.showToast({ title: 'AI 服务暂不可用', icon: 'none' });
				} finally {
					this.aiLoading = false;
				}
			},
			hwTag(h) {
				if (h.overdue) return 'danger';
				if (h.status === '已批改') return 'success';
				if (h.status === '已提交') return 'primary';
				if (h.status === '需重做') return 'warning';
				return 'info';
			},
			hwText(h) { return h.overdue ? '已逾期' : (h.status || '未提交'); },
			barH(score) { return Math.max(12, Math.round((Number(score) || 0) / 100 * 180)); },
			shortTitle(t) { const s = String(t || ''); return s.length > 6 ? s.slice(0, 6) : s; },
			fmtGenTime(t) {
				if (!t) return '';
				const d = new Date(typeof t === 'number' ? t : String(t).replace(/-/g, '/'));
				if (isNaN(d.getTime())) return '';
				const p = (n) => String(n).padStart(2, '0');
				return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
			},
			renderAdvice(text) {
				if (!text) return '';
				const esc = String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
				return esc.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>');
			},
			goHw(h) {
				uni.navigateTo({ url: `/pages/parent/hw-detail?childId=${this.childId}&hwId=${h.id}` });
			},
			goReport() {
				uni.navigateTo({ url: `/pages/parent/report?childId=${this.childId}&name=${encodeURIComponent(this.childName)}` });
			}
		}
	};
</script>

<style scoped>
	.stat-row { display: flex; gap: 16rpx; padding: 24rpx 24rpx 0; }
	.stat { flex: 1; background: #ffffff; border-radius: 20rpx; padding: 24rpx 12rpx; text-align: center; box-shadow: 0 4rpx 20rpx rgba(31, 45, 82, 0.06); }
	.stat-val { display: block; font-size: 44rpx; font-weight: 800; }
	.stat-val .unit { font-size: 22rpx; font-weight: 400; color: #909399; margin-left: 2rpx; }
	.sc-blue .stat-val { color: #409eff; }
	.sc-green .stat-val { color: #67c23a; }
	.sc-orange .stat-val { color: #e6a23c; }
	.stat-label { display: block; margin-top: 8rpx; font-size: 22rpx; color: #909399; }

	.alert { margin: 20rpx 24rpx 0; padding: 18rpx 22rpx; border-radius: 16rpx; background: #fef0f0; color: #f56c6c; font-size: 24rpx; line-height: 1.5; }

	.tabs { white-space: nowrap; margin-bottom: 12rpx; }
	.tabs-in { display: inline-flex; gap: 12rpx; }
	.tab { flex-shrink: 0; padding: 8rpx 22rpx; border-radius: 999rpx; background: #f2f4f8; color: #6b7280; font-size: 24rpx; }
	.tab.on { background: linear-gradient(135deg, #f97316, #ec4899); color: #ffffff; }

	.hw-list { display: flex; flex-direction: column; }
	.hw { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f2f4f8; }
	.hw:last-child { border-bottom: none; }
	.hw-hover { opacity: 0.7; }
	.hw-main { flex: 1; min-width: 0; }
	.hw-title { font-size: 27rpx; color: #303133; font-weight: 500; }
	.hw-time { margin-top: 6rpx; }
	.hw-empty { text-align: center; padding: 30rpx 0; }

	.bars { display: flex; align-items: flex-end; gap: 16rpx; height: 260rpx; padding-top: 10rpx; }
	.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; }
	.bar-score { font-size: 20rpx; color: #f97316; font-weight: 600; margin-bottom: 6rpx; }
	.bar { width: 60%; border-radius: 8rpx 8rpx 0 0; background: linear-gradient(180deg, #f97316, #ec4899); }
	.bar-label { margin-top: 8rpx; font-size: 18rpx; color: #a0a4ab; }

	.ai-card { border: 1rpx solid #ffe0c7; }
	.ai-badge { font-size: 20rpx; color: #f97316; background: rgba(249, 115, 22, 0.12); border-radius: 999rpx; padding: 2rpx 12rpx; margin-left: 6rpx; }
	.ai-btn { font-size: 24rpx; color: #f97316; }
	.ai-loading { font-size: 24rpx; line-height: 1.6; padding: 10rpx 0; }
	.ai-body { background: linear-gradient(135deg, rgba(249, 115, 22, 0.07), rgba(236, 72, 153, 0.06)); border-left: 6rpx solid #f97316; border-radius: 12rpx; padding: 18rpx 20rpx; }
	.ai-text { font-size: 26rpx; color: #303133; line-height: 1.9; }
	.ai-time { margin-top: 10rpx; font-size: 20rpx; }
	.ai-fallback { margin-bottom: 8rpx; }
	.sug { font-size: 25rpx; color: #4b5563; line-height: 1.9; }

	.bottom-bar { padding: 10rpx 24rpx 40rpx; }
</style>
