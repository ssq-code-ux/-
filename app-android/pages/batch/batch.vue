<template>
	<view class="page">
		<!-- 步骤条 -->
		<view class="steps">
			<view v-for="(s, i) in stepNames" :key="i" class="step" :class="{ done: step > i, on: step === i }">
				<text class="step-dot">{{ step > i ? '✓' : i + 1 }}</text>
				<text class="step-name">{{ s }}</text>
			</view>
		</view>

		<!-- 第一步：粘贴 -->
		<view v-if="step === 0" class="card">
			<view class="tip">把群里 / 朋友圈的多条家教信息整段粘进来，AI 会自动拆成一条条并归类字段；单次最多 50 条</view>
			<view class="fi">
				<text class="fl">单子类型</text>
				<view class="radio-group">
					<text class="radio" :class="{ on: jobType === 1 }" @click="jobType = 1">🏠 家教单</text>
					<text class="radio" :class="{ on: jobType === 2 }" @click="jobType = 2">🤝 合作单</text>
				</view>
			</view>
			<textarea class="textarea" v-model="text" maxlength="20000" :placeholder="textPlaceholder" />
			<view class="btn btn-primary full" @click="doParse">{{ parsing ? '解析中…' : '开始拆分' }}</view>
		</view>

		<!-- 第二步：逐条校对 -->
		<view v-else-if="step === 1">
			<view class="card">
				<view class="tip warn">AI 拆出 {{ rows.length }} 条，请逐条核对科目 / 地区 / 时薪；不对的直接改，改完再发布</view>
			</view>
			<view class="list">
				<view v-for="(row, idx) in rows" :key="idx" class="card rcard">
					<view class="row-between rhead">
						<text class="ridx">#{{ idx + 1 }}</text>
						<text class="mini-btn mini-btn-danger" @click="rows.splice(idx, 1)">删除</text>
					</view>
					<view class="fi-row">
						<view class="fi half"><text class="fl">科目</text><input class="input" v-model="row.subject" /></view>
						<view class="fi half"><text class="fl">年级</text><input class="input" v-model="row.grade" /></view>
					</view>
					<view class="fi-row">
						<view class="fi half"><text class="fl">省份</text><input class="input" v-model="row.province" /></view>
						<view class="fi half"><text class="fl">城市</text><input class="input" v-model="row.city" /></view>
					</view>
					<view class="fi"><text class="fl">详细地址</text><input class="input" v-model="row.address" /></view>
					<view class="fi"><text class="fl">上课频次</text><input class="input" v-model="row.frequency" /></view>
					<view class="fi-row">
						<view class="fi half"><text class="fl">时薪</text><input class="input" v-model="row.salary" type="digit" /></view>
						<view class="fi half"><text class="fl">学生性别</text><input class="input" v-model="row.studentGender" /></view>
					</view>
					<view class="fi"><text class="fl">要求老师</text><input class="input" v-model="row.expectGender" /></view>
					<view class="fi"><text class="fl">学生情况</text><textarea class="textarea sm" v-model="row.situation" /></view>
					<view class="fi"><text class="fl">对老师要求</text><textarea class="textarea sm" v-model="row.requirement" /></view>
					<view class="fi switch-fi">
						<text class="fl" style="margin-bottom:0;">缴费解锁</text>
						<switch :checked="row.payToApply === 1" color="#667eea" @change="row.payToApply = $event.detail.value ? 1 : 0" />
					</view>
				</view>
			</view>
			<view class="bar">
				<text class="btn btn-plain" @click="addRow">➕ 手动加一行</text>
				<text class="muted bar-tip">共 {{ rows.length }} 条待发（上限 50）</text>
			</view>
			<view class="bottom-bar">
				<view class="btn btn-plain" @click="step = 0">上一步</view>
				<view class="btn btn-primary" @click="doPublish">{{ publishing ? '发布中…' : '✓ 确认无误，批量发布' }}</view>
			</view>
		</view>

		<!-- 第三步：结果 -->
		<view v-else class="card">
			<view class="result-icon">{{ result.fail ? '⚠️' : '✅' }}</view>
			<view class="result-title">成功发布 {{ result.success || 0 }} 条<text v-if="result.fail"> ，失败 {{ result.fail }} 条</text></view>
			<view class="muted result-sub">发布成功的单子教师端立即可见</view>

			<view v-if="(result.jobs || []).length" class="done-tags">
				<text v-for="j in result.jobs" :key="j.id" class="done-tag">{{ j.jobNo }} · {{ j.subject }}</text>
			</view>

			<view v-if="(result.errors || []).length" class="err-list">
				<view v-for="(er, i) in result.errors" :key="i" class="err-row">
					<text class="err-idx">第 {{ (er.index != null ? er.index : 0) + 1 }} 条</text>
					<text class="err-sub">{{ er.subject || '' }} {{ er.city || '' }}</text>
					<text class="err-msg">{{ er.error }}</text>
				</view>
			</view>

			<view class="btn btn-primary full" @click="finish">完成</view>
		</view>
	</view>
</template>

<script>
	import { adminParseTutorJobs, adminBatchPublishTutorJobs } from '../../api/index.js';

	const MAX_BATCH = 50;

	export default {
		data() {
			return {
				step: 0,
				stepNames: ['粘贴原始信息', '拆分并校对', '发布结果'],
				jobType: 1,
				text: '',
				// placeholder 必须单行且不含换行：uni-app 会把这个从不变更的常量静态内联进 wxml 的 placeholder 属性，字符串里的换行会变成裸换行，触发微信 unexpected character 报错
				textPlaceholder: '示例：南通海门初二数学，每周两次每次2小时，150/小时，需女老师；可一次粘贴多条，AI 会自动拆分',
				rows: [],
				parsing: false,
				publishing: false,
				result: {}
			};
		},
		onLoad(opt) {
			this.jobType = Number(opt.type) === 2 ? 2 : 1;
			uni.setNavigationBarTitle({ title: '批量发布' + (this.jobType === 2 ? '合作单' : '家教单') });
		},
		methods: {
			emptyRow() {
				return {
					subject: '', grade: '', province: '', city: '', address: '', studentGender: '',
					expectGender: '', frequency: '', salary: '', situation: '', requirement: '',
					payToApply: 1, jobType: this.jobType
				};
			},
			addRow() { this.rows.push(this.emptyRow()); },
			async doParse() {
				if (!this.text.trim()) { uni.showToast({ title: '请先粘贴家教信息', icon: 'none' }); return; }
				this.parsing = true;
				try {
					const list = await adminParseTutorJobs({ text: this.text, jobType: this.jobType });
					this.rows = (list || []).map((j) => ({
						...this.emptyRow(), ...j,
						jobType: this.jobType,
						payToApply: j.payToApply == null ? 1 : Number(j.payToApply)
					}));
					if (!this.rows.length) { uni.showToast({ title: 'AI 没识别出内容，换个格式试试', icon: 'none' }); return; }
					this.step = 1;
					uni.showToast({ title: `AI 拆出 ${this.rows.length} 条`, icon: 'none' });
				} catch (e) { /* 已提示 */ } finally {
					this.parsing = false;
				}
			},
			doPublish() {
				const rows = this.rows.filter((r) => r.subject && String(r.subject).trim());
				if (!rows.length) { uni.showToast({ title: '没有可发布的条目（科目必填）', icon: 'none' }); return; }
				if (rows.length > MAX_BATCH) { uni.showToast({ title: `单次最多 ${MAX_BATCH} 条`, icon: 'none' }); return; }
				uni.showModal({
					title: '批量发布',
					content: `确认发布这 ${rows.length} 条${this.jobType === 2 ? '同行合作单' : '家教单'}吗？发布后教师端立即可见。`,
					success: async (r) => {
						if (!r.confirm) return;
						this.publishing = true;
						try {
							const payload = rows.map((x) => ({ ...x, jobType: this.jobType, payToApply: Number(x.payToApply) === 0 ? 0 : 1 }));
							this.result = await adminBatchPublishTutorJobs(payload) || {};
							this.step = 2;
						} catch (e) { /* 已提示 */ } finally {
							this.publishing = false;
						}
					}
				});
			},
			finish() { uni.navigateBack(); }
		}
	};
</script>

<style scoped>
	.steps { display: flex; padding: 24rpx; gap: 8rpx; }
	.step { flex: 1; display: flex; flex-direction: column; align-items: center; }
	.step-dot {
		width: 48rpx; height: 48rpx; line-height: 48rpx; text-align: center;
		border-radius: 50%; background: #e5e7eb; color: #9ca3af; font-size: 24rpx; font-weight: 700;
	}
	.step.on .step-dot { background: #667eea; color: #fff; }
	.step.done .step-dot { background: #16a34a; color: #fff; }
	.step-name { margin-top: 8rpx; font-size: 20rpx; color: #6b7280; }
	.step.on .step-name { color: #667eea; font-weight: 600; }

	.tip { font-size: 24rpx; color: #6b7280; line-height: 1.6; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; margin-bottom: 20rpx; }
	.tip.warn { background: #fffbeb; color: #b45309; margin-bottom: 0; }

	.fi { margin-bottom: 20rpx; }
	.fi-row { display: flex; gap: 20rpx; }
	.fi.half { flex: 1; }
	.fl { display: block; font-size: 24rpx; color: #4b5563; margin-bottom: 10rpx; }
	.input { height: 74rpx; line-height: 74rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 18rpx; font-size: 26rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 240rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 18rpx; font-size: 26rpx; color: #303133; }
	.textarea.sm { height: 110rpx; }
	.radio-group { display: flex; gap: 16rpx; }
	.radio { flex: 1; text-align: center; height: 74rpx; line-height: 74rpx; background: #f5f7fa; border-radius: 12rpx; font-size: 26rpx; color: #6b7280; border: 2rpx solid transparent; }
	.radio.on { background: #eef2ff; color: #667eea; border-color: #667eea; font-weight: 600; }
	.switch-fi { display: flex; align-items: center; justify-content: space-between; }
	.full { width: 100%; text-align: center; margin-top: 8rpx; }

	.list { padding: 0 24rpx; }
	.rcard { margin-bottom: 20rpx; }
	.rhead { margin-bottom: 16rpx; }
	.ridx { font-size: 26rpx; font-weight: 700; color: #667eea; }
	.bar { display: flex; align-items: center; gap: 20rpx; padding: 0 24rpx 10rpx; }
	.bar-tip { font-size: 22rpx; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }

	.result-icon { font-size: 80rpx; text-align: center; }
	.result-title { margin-top: 16rpx; font-size: 34rpx; font-weight: 700; color: #1f2d52; text-align: center; }
	.result-sub { margin-top: 8rpx; font-size: 24rpx; text-align: center; }
	.done-tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 24rpx 0; }
	.done-tag { font-size: 22rpx; color: #16a34a; background: rgba(22, 163, 74, 0.1); border-radius: 8rpx; padding: 6rpx 14rpx; }
	.err-list { margin: 16rpx 0; }
	.err-row { padding: 14rpx 0; border-bottom: 1rpx solid #f0f2f5; }
	.err-idx { font-size: 24rpx; font-weight: 600; color: #e11d48; }
	.err-sub { font-size: 22rpx; color: #6b7280; margin-left: 10rpx; }
	.err-msg { display: block; font-size: 22rpx; color: #909399; margin-top: 4rpx; }
</style>
