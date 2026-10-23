<template>
	<view class="page">
		<!-- 家长信息（不展示给教师端） -->
		<view class="card contact">
			<view class="apc-line">
				<text>家长：{{ parent.contactName || '—' }}</text>
				<text v-if="parent.phone" class="tel-link" @click="callPhone(parent.phone)"> 📞 {{ parent.phone }}</text>
				<text v-if="parent.wechat"> · 💬 {{ parent.wechat }}</text>
			</view>
			<view class="apc-line muted">家长信息不会展示给教师端</view>
		</view>

		<view class="card">
			<view class="fi">
				<text class="fl req">科目</text>
				<input class="input" v-model="form.subject" placeholder="如：数学 / 英语" maxlength="50" />
			</view>
			<view class="fi-row">
				<view class="fi half"><text class="fl">省份</text><input class="input" v-model="form.province" placeholder="如：河南" maxlength="50" /></view>
				<view class="fi half"><text class="fl">城市</text><input class="input" v-model="form.city" placeholder="如：郑州" maxlength="50" /></view>
			</view>
			<view class="fi"><text class="fl">详细地址</text><input class="input" v-model="form.address" maxlength="200" /></view>
			<view class="fi-row">
				<view class="fi half"><text class="fl">年级</text><input class="input" v-model="form.grade" maxlength="50" /></view>
				<view class="fi half"><text class="fl">时薪(元/h)</text><input class="input" v-model="form.salary" type="digit" /></view>
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
					<text class="muted switch-sub">开启后教师需先缴费、由管理员标记后才能投递</text>
				</view>
				<switch :checked="form.payToApply" color="#667eea" @change="form.payToApply = $event.detail.value" />
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">发布</view>
		</view>
	</view>
</template>

<script>
	import { adminParentDemandDetail, adminPublishParentDemand } from '../../api/index.js';

	const GENDER_VALUES = ['', '男孩', '女孩'];
	const EXPECT_VALUES = ['', '男', '女'];

	export default {
		data() {
			return {
				id: '',
				parent: {},
				form: { subject: '', province: '', city: '', address: '', grade: '', salary: '', studentGender: '', expectGender: '', frequency: '', situation: '', requirement: '', payToApply: true },
				genderLabels: ['不限', '男孩', '女孩'],
				expectLabels: ['不限', '男老师', '女老师'],
				genderIdx: 0,
				expectIdx: 0,
				submitting: false
			};
		},
		onLoad(opt) {
			this.id = opt.id || '';
			uni.setNavigationBarTitle({ title: '发布为家教单' });
			this.load();
		},
		methods: {
			async load() {
				if (!this.id) return;
				try {
					const row = await adminParentDemandDetail(this.id);
					if (!row) return;
					this.parent = { contactName: row.contactName, phone: row.phone, wechat: row.wechat };
					this.form.subject = row.subject || '';
					this.form.grade = row.grade || '';
					this.form.studentGender = row.studentGender || '';
					this.form.expectGender = row.expectGender || '';
					this.form.salary = row.salary != null ? row.salary : '';
					this.form.situation = row.studentLevel || '';
					this.form.requirement = row.otherRequire || '';
					this.form.payToApply = true;
					this.genderIdx = Math.max(0, GENDER_VALUES.indexOf(this.form.studentGender));
					this.expectIdx = Math.max(0, EXPECT_VALUES.indexOf(this.form.expectGender));
				} catch (e) { /* 已提示 */ }
			},
			onGenderChange(e) { this.genderIdx = Number(e.detail.value); this.form.studentGender = GENDER_VALUES[this.genderIdx]; },
			onExpectChange(e) { this.expectIdx = Number(e.detail.value); this.form.expectGender = EXPECT_VALUES[this.expectIdx]; },
			callPhone(phone) { if (phone) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} }); },
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				if (!String(this.form.subject || '').trim()) { uni.showToast({ title: '科目不能为空', icon: 'none' }); return; }
				this.submitting = true;
				try {
					const payload = { ...this.form, payToApply: this.form.payToApply ? 1 : 0 };
					const res = await adminPublishParentDemand(this.id, payload);
					uni.showToast({ title: `已发布${res && res.jobNo ? '（' + res.jobNo + '）' : ''}`, icon: 'none' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally {
					this.submitting = false;
				}
			}
		}
	};
</script>

<style scoped>
	.contact { background: #ecf5ff; }
	.apc-line { font-size: 26rpx; color: #2c5282; line-height: 1.7; }
	.fi { margin-bottom: 24rpx; }
	.fi-row { display: flex; gap: 20rpx; }
	.fi.half { flex: 1; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input, .picker-in { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.switch-fi { display: flex; align-items: center; justify-content: space-between; }
	.switch-txt { flex: 1; padding-right: 20rpx; }
	.switch-txt .fl { margin-bottom: 6rpx; }
	.switch-sub { font-size: 22rpx; line-height: 1.4; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
