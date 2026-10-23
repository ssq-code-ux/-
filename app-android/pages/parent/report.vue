<template>
	<view class="page">
		<view v-if="report" class="wrap">
			<view class="range">{{ report.rangeStart }} ~ {{ report.rangeEnd }}（最近 7 天）</view>

			<view class="grid">
				<view class="cell"><text class="c-icon">📝</text><text class="c-val">{{ report.newHomeworks || 0 }}</text><text class="c-label">新收到作业</text></view>
				<view class="cell"><text class="c-icon">📤</text><text class="c-val">{{ report.submitCount || 0 }}</text><text class="c-label">提交次数</text></view>
				<view class="cell"><text class="c-icon">✅</text><text class="c-val">{{ report.reviewedCount || 0 }}</text><text class="c-label">已获批改</text></view>
				<view class="cell">
					<text class="c-icon">💯</text><text class="c-val">{{ report.avgScore || 0 }}<text class="c-unit">分</text></text><text class="c-label">本周平均分</text>
					<text v-if="report.avgScoreLast" class="c-delta" :class="delta >= 0 ? 'up' : 'down'">{{ delta >= 0 ? '↑' : '↓' }} {{ absDelta }} vs 上周</text>
				</view>
				<view class="cell"><text class="c-icon">🔥</text><text class="c-val">{{ report.activeDays || 0 }}<text class="c-unit">/7天</text></text><text class="c-label">学习活跃天数</text></view>
				<view class="cell"><text class="c-icon">❌</text><text class="c-val">{{ report.newWordErrors || 0 }}</text><text class="c-label">测验新增错词</text></view>
				<view class="cell"><text class="c-icon">🧠</text><text class="c-val">{{ report.masteredWords || 0 }}</text><text class="c-label">掌握错词</text></view>
				<view class="cell" :class="{ warn: report.overdueHomeworks > 0 }"><text class="c-icon">⚠️</text><text class="c-val">{{ report.overdueHomeworks || 0 }}</text><text class="c-label">逾期未提交</text></view>
			</view>

			<!-- 本周总结 -->
			<view class="card ai-card">
				<view class="card-title">
					<text>💬 本周总结</text>
					<text class="ai-btn" @click="genSummary">{{ summary ? '🔄 重新生成' : '✨ 立即生成' }}</text>
				</view>
				<view v-if="aiLoading" class="muted ai-loading">AI 正在为 {{ childName }} 生成本周总结…</view>
				<rich-text v-else-if="summary" class="ai-text" :nodes="renderAdvice(summary)"></rich-text>
				<view v-else class="muted ai-loading">本周总结每周一自动更新，也可点右上角「立即生成」</view>
			</view>

			<view class="bottom-bar">
				<view class="btn btn-primary" @click="copyReport">📋 复制周报</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { parentChildWeeklyReport, parentChildAiContent, parentChildAiRefresh } from '../../api/index.js';

	export default {
		data() {
			return {
				childId: null,
				childName: '孩子',
				report: null,
				summary: '',
				aiLoading: false
			};
		},
		computed: {
			delta() {
				if (!this.report) return 0;
				return Math.round(((this.report.avgScore || 0) - (this.report.avgScoreLast || 0)) * 10) / 10;
			},
			absDelta() { return Math.abs(this.delta); }
		},
		onLoad(opt) {
			this.childId = opt.childId;
			this.childName = opt.name ? decodeURIComponent(opt.name) : '孩子';
			uni.setNavigationBarTitle({ title: this.childName + ' 的学习周报' });
			this.load();
		},
		methods: {
			async load() {
				uni.showLoading({ title: '加载中' });
				try {
					const [r, ai] = await Promise.all([
						parentChildWeeklyReport(this.childId),
						parentChildAiContent(this.childId, 'weekly_summary').catch(() => null)
					]);
					this.report = r || null;
					this.summary = (ai && ai.content) || '';
				} catch (e) { /* 已提示 */ } finally {
					uni.hideLoading();
				}
			},
			async genSummary() {
				if (this.aiLoading) return;
				this.aiLoading = true;
				this.summary = '';
				try {
					const res = await parentChildAiRefresh(this.childId, 'weekly_summary');
					this.summary = (res && res.content) || '';
					if (!this.summary) uni.showToast({ title: 'AI 总结生成失败，可稍后重试', icon: 'none' });
				} catch (e) {
					uni.showToast({ title: 'AI 总结生成失败', icon: 'none' });
				} finally {
					this.aiLoading = false;
				}
			},
			renderAdvice(text) {
				if (!text) return '';
				const esc = String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
				return esc.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>');
			},
			copyReport() {
				if (!this.report) return;
				const r = this.report;
				const lines = [
					`📖 ${this.childName} 的学习周报（${r.rangeStart} ~ ${r.rangeEnd}）`,
					`📝 新收到作业：${r.newHomeworks || 0} 份，已提交 ${r.submitCount || 0} 次`,
					`💯 本周平均分：${r.avgScore || 0} 分` + (r.avgScoreLast ? `（上周 ${r.avgScoreLast} 分，${this.delta >= 0 ? '↑' : '↓'} ${this.absDelta}）` : ''),
					`❌ 单词测验新增错词：${r.newWordErrors || 0} 个，掌握错词 ${r.masteredWords || 0} 个`,
					`🔥 学习活跃：${r.activeDays || 0}/7 天`
				];
				if (r.overdueHomeworks > 0) lines.push(`⚠️ 逾期未提交作业：${r.overdueHomeworks} 项`);
				if (this.summary) lines.push('', '💬 AI 点评：', this.summary.replace(/\*\*/g, ''));
				uni.setClipboardData({
					data: lines.join('\n'),
					success: () => uni.showToast({ title: '周报已复制，可粘贴分享', icon: 'none' })
				});
			}
		}
	};
</script>

<style scoped>
	.wrap { padding: 24rpx 0 40rpx; }
	.range { text-align: center; font-size: 24rpx; color: #909399; margin-bottom: 20rpx; }
	.grid { display: flex; flex-wrap: wrap; gap: 16rpx; padding: 0 24rpx; }
	.cell { width: calc((100% - 32rpx) / 3); box-sizing: border-box; background: #ffffff; border-radius: 16rpx; padding: 22rpx 8rpx; display: flex; flex-direction: column; align-items: center; gap: 4rpx; box-shadow: 0 4rpx 20rpx rgba(31, 45, 82, 0.06); }
	.cell.warn { background: #fef0f0; }
	.cell.warn .c-val { color: #f56c6c; }
	.c-icon { font-size: 34rpx; }
	.c-val { font-size: 36rpx; font-weight: 800; color: #1f2d52; line-height: 1.2; }
	.c-unit { font-size: 20rpx; font-weight: 400; color: #909399; margin-left: 2rpx; }
	.c-label { font-size: 20rpx; color: #909399; }
	.c-delta { font-size: 18rpx; margin-top: 2rpx; }
	.c-delta.up { color: #67c23a; }
	.c-delta.down { color: #f56c6c; }

	.ai-card { margin-top: 24rpx; border: 1rpx solid #ffe0c7; }
	.ai-btn { font-size: 24rpx; color: #f97316; }
	.ai-loading { font-size: 24rpx; line-height: 1.6; padding: 10rpx 0; }
	.ai-text { display: block; font-size: 26rpx; color: #303133; line-height: 1.9; background: linear-gradient(135deg, rgba(249, 115, 22, 0.07), rgba(236, 72, 153, 0.06)); border-left: 6rpx solid #f97316; border-radius: 12rpx; padding: 18rpx 20rpx; }
	.bottom-bar { padding: 20rpx 24rpx 0; }
</style>
