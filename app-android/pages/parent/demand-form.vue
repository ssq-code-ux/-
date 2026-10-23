<template>
	<view class="page">
		<view class="card">
			<view class="tip">只有手机号必填，其余可留空——管理员会电话跟您确认细节后再发布。</view>

			<view class="fi">
				<text class="fl req">手机号</text>
				<input class="input" v-model="form.phone" type="number" placeholder="老师确定后由管理员联系您" maxlength="20" />
			</view>
			<view class="fi">
				<text class="fl">微信号</text>
				<input class="input" v-model="form.wechat" placeholder="选填" maxlength="50" />
			</view>

			<view class="fold" @click="moreOpen = !moreOpen">
				<text class="fold-title">补充孩子情况（全部选填）</text>
				<text class="fold-arrow">{{ moreOpen ? '收起 ▴' : '展开 ▾' }}</text>
			</view>

			<view v-if="moreOpen">
				<view class="fi">
					<text class="fl">您的称呼</text>
					<view class="radio-group">
						<text class="radio" :class="{ on: form.contactName === '先生' }" @click="form.contactName = '先生'">先生</text>
						<text class="radio" :class="{ on: form.contactName === '女士' }" @click="form.contactName = '女士'">女士</text>
					</view>
				</view>
				<view class="fi">
					<text class="fl">辅导学科</text>
					<input class="input" v-model="form.subject" placeholder="想找什么学科的老师，如：数学、英语" maxlength="50" />
				</view>
				<view class="fi">
					<text class="fl">年级</text>
					<input class="input" v-model="form.grade" placeholder="如：初二、高三" maxlength="50" />
				</view>
				<view class="fi">
					<text class="fl">学生程度</text>
					<input class="input" v-model="form.studentLevel" placeholder="如：基础较弱，需从基础补起 / 成绩中上，拔高冲刺" maxlength="200" />
				</view>
				<view class="fi">
					<text class="fl">学生性别</text>
					<view class="radio-group">
						<text class="radio" :class="{ on: form.studentGender === '男生' }" @click="form.studentGender = '男生'">男生</text>
						<text class="radio" :class="{ on: form.studentGender === '女生' }" @click="form.studentGender = '女生'">女生</text>
					</view>
				</view>
				<view class="fi">
					<text class="fl">老师性别要求</text>
					<view class="radio-group">
						<text class="radio" :class="{ on: form.expectGender === '不限' }" @click="form.expectGender = '不限'">不限</text>
						<text class="radio" :class="{ on: form.expectGender === '男' }" @click="form.expectGender = '男'">男老师</text>
						<text class="radio" :class="{ on: form.expectGender === '女' }" @click="form.expectGender = '女'">女老师</text>
					</view>
				</view>
				<view class="fi">
					<text class="fl">其他要求</text>
					<textarea class="textarea" v-model="form.otherRequire" placeholder="对老师的其他要求，如：有耐心、能长期带、师范生优先等" maxlength="500" />
				</view>
				<view class="fi">
					<text class="fl">可提供时薪(元/h)</text>
					<input class="input" v-model="form.salary" type="digit" placeholder="如：80" maxlength="10" />
				</view>
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">提交需求</view>
		</view>
	</view>
</template>

<script>
	import { submitParentDemand } from '../../api/index.js';

	export default {
		data() {
			return {
				moreOpen: false,
				submitting: false,
				form: {
					contactName: '', phone: '', wechat: '',
					studentLevel: '', grade: '', studentGender: '', subject: '',
					expectGender: '', otherRequire: '', salary: ''
				}
			};
		},
		methods: {
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				const phone = String(this.form.phone || '').trim();
				if (!phone) { uni.showToast({ title: '请填写手机号', icon: 'none' }); return; }
				if (!/^\d{5,20}$/.test(phone)) { uni.showToast({ title: '手机号格式不正确', icon: 'none' }); return; }
				this.submitting = true;
				try {
					await submitParentDemand({
						...this.form,
						phone,
						salary: this.form.salary === '' || this.form.salary == null ? null : Number(this.form.salary)
					});
					uni.showToast({ title: '需求已提交', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally {
					this.submitting = false;
				}
			}
		}
	};
</script>

<style scoped>
	.tip { font-size: 23rpx; color: #909399; line-height: 1.6; background: #fff7ed; border-radius: 12rpx; padding: 16rpx 18rpx; margin-bottom: 24rpx; }
	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.radio-group { display: flex; gap: 16rpx; }
	.radio { flex: 1; text-align: center; height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; font-size: 26rpx; color: #6b7280; border: 2rpx solid transparent; }
	.radio.on { background: #fff2e8; color: #f97316; border-color: #f97316; font-weight: 600; }
	.fold { display: flex; align-items: center; justify-content: space-between; background: #f8f8fe; border-radius: 12rpx; padding: 20rpx; margin-bottom: 24rpx; }
	.fold-title { font-size: 25rpx; font-weight: 700; color: #f97316; }
	.fold-arrow { font-size: 24rpx; color: #909399; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
