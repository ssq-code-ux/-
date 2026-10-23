<template>
	<view class="page">
		<view class="card">
			<view class="card-title">🔒 修改密码</view>
			<view class="tip">为了账号安全，请输入当前登录密码，再设置新密码（至少 6 位）。修改成功后请牢记新密码。</view>

			<view class="field">
				<text class="field-label">原密码</text>
				<input class="input" password v-model="form.oldPassword" placeholder="请输入当前登录密码" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">新密码</text>
				<input class="input" password v-model="form.newPassword" placeholder="至少 6 位" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">确认新密码</text>
				<input class="input" password v-model="form.confirmPassword" placeholder="再次输入新密码" placeholder-class="ph" />
			</view>

			<view class="btn btn-primary mt24" :class="{ 'btn-disabled': saving }" hover-class="btn-hover" @click="onSubmit">
				{{ saving ? '提交中…' : '确定修改' }}
			</view>
		</view>
	</view>
</template>

<script>
	import { changeMyPassword } from '../../api/index.js';

	export default {
		data() {
			return {
				form: { oldPassword: '', newPassword: '', confirmPassword: '' },
				saving: false
			};
		},
		methods: {
			async onSubmit() {
				if (this.saving) return;
				const f = this.form;
				if (!f.oldPassword) { uni.showToast({ title: '请输入原密码', icon: 'none' }); return; }
				if (!f.newPassword || String(f.newPassword).length < 6) { uni.showToast({ title: '新密码至少 6 位', icon: 'none' }); return; }
				if (f.newPassword !== f.confirmPassword) { uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' }); return; }
				this.saving = true;
				try {
					await changeMyPassword(f.oldPassword, f.newPassword);
					uni.showToast({ title: '密码修改成功', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 600);
				} catch (e) { /* 错误已由请求拦截器提示 */ } finally { this.saving = false; }
			}
		}
	};
</script>

<style scoped>
	.tip { font-size: 22rpx; color: #909399; line-height: 1.5; margin-bottom: 20rpx; }
	.field { margin-bottom: 20rpx; }
	.field-label { display: block; font-size: 24rpx; color: #4b5563; margin-bottom: 10rpx; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.ph { color: #b6bcc7; }
	.btn-hover { opacity: 0.85; }
</style>
