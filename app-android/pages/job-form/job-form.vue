<template>
	<view class="page">
		<view class="card">
			<!-- 套用模板：仅新建时 -->
			<view class="fi" v-if="!form.id && templateOptions.length">
				<text class="fl">套用模板</text>
				<picker mode="selector" :range="tplLabels" :value="tplIdx" @change="onTplChange">
					<view class="picker-in">{{ tplIdx >= 0 ? tplLabels[tplIdx] : '不用模板，直接填写 ▾' }}</view>
				</picker>
			</view>

			<!-- 单子类型 -->
			<view class="fi">
				<text class="fl">单子类型</text>
				<view class="radio-group">
					<text class="radio" :class="{ on: form.jobType === 1 }" @click="form.jobType = 1">🏠 家教单</text>
					<text class="radio" :class="{ on: form.jobType === 2 }" @click="form.jobType = 2">🤝 合作单</text>
				</view>
			</view>

			<view class="fi">
				<text class="fl req">科目</text>
				<input class="input" v-model="form.subject" placeholder="如：数学 / 英语" maxlength="50" />
			</view>

			<view class="fi-row">
				<view class="fi half">
					<text class="fl">省份</text>
					<input class="input" v-model="form.province" placeholder="如：江苏" maxlength="50" />
				</view>
				<view class="fi half">
					<text class="fl">城市</text>
					<input class="input" v-model="form.city" placeholder="如：南通" maxlength="50" />
				</view>
			</view>

			<view class="fi">
				<text class="fl">详细地址</text>
				<input class="input" v-model="form.address" placeholder="如：南通海门区、秀山新村" maxlength="200" />
			</view>

			<view class="fi-row">
				<view class="fi half">
					<text class="fl">年级</text>
					<input class="input" v-model="form.grade" placeholder="如：初三" maxlength="50" />
				</view>
				<view class="fi half">
					<text class="fl">时薪(元/h)</text>
					<input class="input" v-model="form.salary" type="digit" placeholder="如：150" />
				</view>
			</view>

			<view class="fi-row">
				<view class="fi half">
					<text class="fl">学生性别</text>
					<picker mode="selector" :range="genderLabels" :value="genderIdx" @change="onGenderChange">
						<view class="picker-in">{{ genderLabels[genderIdx] }}</view>
					</picker>
				</view>
				<view class="fi half">
					<text class="fl">要求老师</text>
					<picker mode="selector" :range="expectLabels" :value="expectIdx" @change="onExpectChange">
						<view class="picker-in">{{ expectLabels[expectIdx] }}</view>
					</picker>
				</view>
			</view>

			<view class="fi">
				<text class="fl">上课频次</text>
				<input class="input" v-model="form.frequency" placeholder="如：每周1次，每次2小时" maxlength="100" />
			</view>

			<view class="fi">
				<text class="fl">学生情况</text>
				<textarea class="textarea" v-model="form.situation" placeholder="如：补基础，提优" maxlength="500" />
			</view>

			<view class="fi">
				<text class="fl">对老师要求</text>
				<textarea class="textarea" v-model="form.requirement" placeholder="如：教学经验丰富，善于引导" maxlength="500" />
			</view>

			<view class="fi switch-fi">
				<view class="switch-txt">
					<text class="fl">缴费解锁</text>
					<text class="muted switch-sub">开启后教师需先缴费、由管理员标记后才能投递</text>
				</view>
				<switch :checked="form.payToApply" color="#667eea" @change="onPayChange" />
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">{{ form.id ? '保存' : '发布' }}</view>
		</view>
	</view>
</template>

<script>
	import { adminTutorJobDetail, adminPublishTutorJob, adminUpdateTutorJob, adminJobTemplates } from '../../api/index.js';

	const GENDER_VALUES = ['', '男孩', '女孩'];
	const EXPECT_VALUES = ['', '男', '女'];
	const TPL_FIELDS = ['subject', 'grade', 'province', 'city', 'address', 'studentGender',
		'expectGender', 'frequency', 'salary', 'situation', 'requirement'];

	export default {
		data() {
			return {
				form: { id: null, jobNo: '', jobType: 1, subject: '', province: '', city: '', address: '', grade: '', salary: '', studentGender: '', expectGender: '', frequency: '', situation: '', requirement: '', payToApply: true },
				genderLabels: ['不限', '男孩', '女孩'],
				expectLabels: ['不限', '男老师', '女老师'],
				genderIdx: 0,
				expectIdx: 0,
				templateOptions: [],
				tplIdx: -1,
				pendingTplId: null,
				submitting: false
			};
		},
		computed: {
			tplLabels() {
				return this.templateOptions.map((t) => `${t.subject} · ${t.name}${t.jobType === 2 ? '（合作单）' : ''}`);
			}
		},
		onLoad(opt) {
			this.form.jobType = Number(opt.type) === 2 ? 2 : 1;
			if (opt.id) {
				uni.setNavigationBarTitle({ title: '编辑' + (this.form.jobType === 2 ? '合作单' : '家教单') });
				this.loadJob(opt.id);
			} else {
				uni.setNavigationBarTitle({ title: '发布' + (this.form.jobType === 2 ? '合作单' : '家教单') });
				if (opt.tplId) this.pendingTplId = opt.tplId;
				this.loadTemplates();
			}
		},
		methods: {
			async loadJob(id) {
				try {
					const row = await adminTutorJobDetail(id);
					if (!row) return;
					['id', 'jobNo', 'subject', 'province', 'city', 'address', 'grade', 'studentGender',
						'expectGender', 'frequency', 'salary', 'situation', 'requirement'].forEach((k) => {
						this.form[k] = row[k] != null ? row[k] : this.form[k];
					});
					this.form.payToApply = row.payToApply === 1;
					if (row.jobType) this.form.jobType = row.jobType;
					this.syncPickers();
				} catch (e) { /* 已提示 */ }
			},
			async loadTemplates() {
				try {
					const data = await adminJobTemplates({});
					this.templateOptions = data.list || [];
					if (this.pendingTplId != null) {
						const idx = this.templateOptions.findIndex((t) => String(t.id) === String(this.pendingTplId));
						if (idx >= 0) { this.tplIdx = idx; this.applyTpl(idx); }
						this.pendingTplId = null;
					}
				} catch (e) { /* 已提示 */ }
			},
			applyTpl(idx) {
				const tpl = this.templateOptions[idx];
				if (!tpl) return;
				TPL_FIELDS.forEach((k) => { this.form[k] = tpl[k] != null ? tpl[k] : ''; });
				this.form.payToApply = tpl.payToApply === 1;
				if (tpl.jobType) this.form.jobType = tpl.jobType;
				this.syncPickers();
			},
			syncPickers() {
				this.genderIdx = Math.max(0, GENDER_VALUES.indexOf(this.form.studentGender || ''));
				this.expectIdx = Math.max(0, EXPECT_VALUES.indexOf(this.form.expectGender || ''));
			},
			onGenderChange(e) { this.genderIdx = Number(e.detail.value); this.form.studentGender = GENDER_VALUES[this.genderIdx]; },
			onExpectChange(e) { this.expectIdx = Number(e.detail.value); this.form.expectGender = EXPECT_VALUES[this.expectIdx]; },
			onPayChange(e) { this.form.payToApply = e.detail.value; },
			onTplChange(e) {
				this.tplIdx = Number(e.detail.value);
				const tpl = this.templateOptions[this.tplIdx];
				if (!tpl) return;
				this.applyTpl(this.tplIdx);
				uni.showToast({ title: `已套用「${tpl.subject} · ${tpl.name}」`, icon: 'none' });
			},
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				if (!String(this.form.subject || '').trim()) { uni.showToast({ title: '科目不能为空', icon: 'none' }); return; }
				this.submitting = true;
				try {
					const payload = { ...this.form, payToApply: this.form.payToApply ? 1 : 0 };
					if (this.form.id) {
						await adminUpdateTutorJob(this.form.id, payload);
						uni.showToast({ title: '已保存', icon: 'success' });
					} else {
						await adminPublishTutorJob(payload);
						uni.showToast({ title: '发布成功', icon: 'success' });
					}
					setTimeout(() => uni.navigateBack(), 600);
				} catch (e) { /* 已提示 */ } finally {
					this.submitting = false;
				}
			}
		}
	};
</script>

<style scoped>
	.fi { margin-bottom: 24rpx; }
	.fi-row { display: flex; gap: 20rpx; }
	.fi.half { flex: 1; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input, .picker-in {
		height: 80rpx;
		line-height: 80rpx;
		background: #f5f7fa;
		border-radius: 12rpx;
		padding: 0 20rpx;
		font-size: 28rpx;
		color: #303133;
	}
	.picker-in { color: #303133; }
	.textarea {
		width: 100%;
		box-sizing: border-box;
		height: 140rpx;
		background: #f5f7fa;
		border-radius: 12rpx;
		padding: 16rpx 20rpx;
		font-size: 28rpx;
		color: #303133;
	}
	.radio-group { display: flex; gap: 16rpx; }
	.radio {
		flex: 1;
		text-align: center;
		height: 80rpx;
		line-height: 80rpx;
		background: #f5f7fa;
		border-radius: 12rpx;
		font-size: 26rpx;
		color: #6b7280;
		border: 2rpx solid transparent;
	}
	.radio.on { background: #eef2ff; color: #667eea; border-color: #667eea; font-weight: 600; }
	.switch-fi { display: flex; align-items: center; justify-content: space-between; }
	.switch-txt { flex: 1; padding-right: 20rpx; }
	.switch-txt .fl { margin-bottom: 6rpx; }
	.switch-sub { font-size: 22rpx; line-height: 1.4; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
