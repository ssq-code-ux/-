<template>
	<view class="page">
		<view class="card">
			<view class="fi">
				<text class="fl req">科目</text>
				<input class="input" v-model="form.subject" placeholder="如：数学 / 英语" maxlength="50" />
			</view>
			<view class="fi">
				<text class="fl req">模板名称</text>
				<input class="input" v-model="form.name" placeholder="如：初中数学一对一" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">单子类型</text>
				<view class="radio-row">
					<view class="radio" :class="{ on: form.jobType === 1 }" @click="form.jobType = 1">家教单</view>
					<view class="radio" :class="{ on: form.jobType === 2 }" @click="form.jobType = 2">合作单</view>
				</view>
			</view>
			<view class="fi-row">
				<view class="fi half"><text class="fl">省份</text><input class="input" v-model="form.province" placeholder="如：河南" maxlength="50" /></view>
				<view class="fi half"><text class="fl">城市</text><input class="input" v-model="form.city" placeholder="如：郑州" maxlength="50" /></view>
			</view>
			<view class="fi"><text class="fl">详细地址</text><input class="input" v-model="form.address" maxlength="200" /></view>
			<view class="fi-row">
				<view class="fi half"><text class="fl">年级</text><input class="input" v-model="form.grade" maxlength="50" /></view>
				<view class="fi half"><text class="fl">默认时薪(元/h)</text><input class="input" v-model="form.salary" type="digit" /></view>
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
			<view class="fi"><text class="fl">上课频次</text><input class="input" v-model="form.frequency" placeholder="如：每周1次，每次2小时" maxlength="100" /></view>
			<view class="fi"><text class="fl">学生情况</text><textarea class="textarea" v-model="form.situation" maxlength="500" /></view>
			<view class="fi"><text class="fl">对老师要求</text><textarea class="textarea" v-model="form.requirement" maxlength="500" /></view>
			<view class="fi switch-fi">
				<view class="switch-txt">
					<text class="fl">缴费解锁</text>
					<text class="muted switch-sub">套用该模板时默认开启缴费解锁，发布时可再改</text>
				</view>
				<switch :checked="form.payToApply === 1" color="#667eea" @change="form.payToApply = $event.detail.value ? 1 : 0" />
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">{{ form.id ? '保存' : '创建模板' }}</view>
		</view>
	</view>
</template>

<script>
	import { adminJobTemplates, adminSaveJobTemplate } from '../../api/index.js';

	const GENDER_VALUES = ['', '男孩', '女孩'];
	const EXPECT_VALUES = ['', '男', '女'];

	export default {
		data() {
			return {
				form: { id: null, subject: '', name: '', jobType: 1, province: '', city: '', address: '', grade: '', salary: '', studentGender: '', expectGender: '', frequency: '', situation: '', requirement: '', payToApply: 1 },
				genderLabels: ['不限', '男孩', '女孩'],
				expectLabels: ['不限', '男老师', '女老师'],
				genderIdx: 0,
				expectIdx: 0,
				submitting: false
			};
		},
		onLoad(opt) {
			if (opt && opt.id) {
				uni.setNavigationBarTitle({ title: '编辑模板' });
				this.prefill(opt.id);
			} else {
				uni.setNavigationBarTitle({ title: '新增模板' });
			}
		},
		methods: {
			async prefill(id) {
				try {
					const data = await adminJobTemplates({});
					const row = (data.list || []).find((t) => String(t.id) === String(id));
					if (!row) return;
					Object.assign(this.form, {
						id: row.id, subject: row.subject || '', name: row.name || '', jobType: row.jobType || 1,
						province: row.province || '', city: row.city || '', address: row.address || '', grade: row.grade || '',
						salary: row.salary != null ? row.salary : '', studentGender: row.studentGender || '', expectGender: row.expectGender || '',
						frequency: row.frequency || '', situation: row.situation || '', requirement: row.requirement || '',
						payToApply: row.payToApply != null ? row.payToApply : 1
					});
					this.genderIdx = Math.max(0, GENDER_VALUES.indexOf(this.form.studentGender));
					this.expectIdx = Math.max(0, EXPECT_VALUES.indexOf(this.form.expectGender));
				} catch (e) { /* 已提示 */ }
			},
			onGenderChange(e) { this.genderIdx = Number(e.detail.value); this.form.studentGender = GENDER_VALUES[this.genderIdx]; },
			onExpectChange(e) { this.expectIdx = Number(e.detail.value); this.form.expectGender = EXPECT_VALUES[this.expectIdx]; },
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				if (!String(this.form.subject || '').trim()) { uni.showToast({ title: '科目不能为空', icon: 'none' }); return; }
				if (!String(this.form.name || '').trim()) { uni.showToast({ title: '模板名称不能为空', icon: 'none' }); return; }
				this.submitting = true;
				try {
					await adminSaveJobTemplate(this.form);
					uni.showToast({ title: '已保存', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
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
	.input, .picker-in { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.radio-row { display: flex; gap: 16rpx; }
	.radio { flex: 1; text-align: center; padding: 18rpx 0; border-radius: 12rpx; background: #f2f4f8; color: #4b5563; font-size: 28rpx; }
	.radio.on { background: #eef0ff; color: #667eea; border: 1rpx solid #667eea; font-weight: 600; }
	.switch-fi { display: flex; align-items: center; justify-content: space-between; }
	.switch-txt { flex: 1; padding-right: 20rpx; }
	.switch-txt .fl { margin-bottom: 6rpx; }
	.switch-sub { font-size: 22rpx; line-height: 1.4; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
