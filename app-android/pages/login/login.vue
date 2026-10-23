<template>
	<view class="login-page">
		<!-- 固定背景层：渐变 + 光晕，滚动时始终铺满视口 -->
		<view class="bg">
			<view class="login-glow login-glow--top" />
			<view class="login-glow login-glow--bottom" />
		</view>

		<view class="content">
			<view class="login-card">
				<view class="login-logo">📚</view>
				<view class="login-title">智乐学</view>

				<!-- 登录 / 注册 Tab -->
				<view class="auth-tabs">
					<view class="auth-tab" :class="{ 'auth-tab--active': tab === 'login' }" @click="tab = 'login'">登 录</view>
					<view class="auth-tab" :class="{ 'auth-tab--active': tab === 'register' }" @click="toRegister">注 册</view>
				</view>

				<!-- ============ 登录表单 ============ -->
				<block v-if="tab === 'login'">
					<view class="field">
						<text class="field-icon">👤</text>
						<input class="field-input" type="text" v-model="form.username" placeholder="请输入账号" placeholder-class="ph" />
					</view>
					<view class="field">
						<text class="field-icon">🔒</text>
						<input class="field-input" password v-model="form.password" placeholder="请输入密码" placeholder-class="ph" @confirm="doLogin" />
					</view>
					<view class="btn btn-primary login-btn" :class="{ 'btn-disabled': loading }" hover-class="btn-hover" @click="doLogin">
						{{ loading ? '登录中…' : '登 录' }}
					</view>
				</block>

				<!-- ============ 注册表单 ============ -->
				<block v-else>
					<!-- 身份选择 -->
					<view class="role-grid">
						<view class="role-card" :class="{ 'role-card--active': reg.role === 'student' }" @click="reg.role = 'student'">
							<text class="role-icon">🎓</text>
							<text class="role-name">我是学生</text>
						</view>
						<view class="role-card" :class="{ 'role-card--active': reg.role === 'teacher' }" @click="reg.role = 'teacher'">
							<text class="role-icon">👨‍🏫</text>
							<text class="role-name">我是老师</text>
						</view>
						<view class="role-card" :class="{ 'role-card--active': reg.role === 'parent' }" @click="reg.role = 'parent'">
							<text class="role-icon">👪</text>
							<text class="role-name">我是家长</text>
						</view>
					</view>

					<view class="field">
						<text class="field-icon">👤</text>
						<input class="field-input" type="text" v-model="reg.username" placeholder="设置用户名" placeholder-class="ph" />
					</view>
					<view class="field">
						<text class="field-icon">🔒</text>
						<input class="field-input" password v-model="reg.password" placeholder="设置密码（至少 4 位）" placeholder-class="ph" />
					</view>
					<view class="field">
						<text class="field-icon">🧑</text>
						<input class="field-input" type="text" v-model="reg.realName" placeholder="请输入真实姓名" placeholder-class="ph" />
					</view>
					<view class="field">
						<text class="field-icon">📱</text>
						<input class="field-input" type="number" v-model="reg.phone" placeholder="手机号（选填）" placeholder-class="ph" />
					</view>
					
					<view class="field">
						<text class="field-icon">🎫</text>
						<input class="field-input" type="text" v-model="reg.inviteCode" placeholder="邀请码（选填，由邀请您的老师提供）" placeholder-class="ph" />
					</view>

					<view class="btn btn-primary reg-btn" :class="{ 'btn-disabled': regLoading }" hover-class="btn-hover" @click="doRegister">
						{{ regLoading ? '注册中…' : '注 册' }}
					</view>
				</block>
			</view>
		</view>
	</view>
</template>

<script>
	import { login, register } from '../../api/index.js';
	import { getUser, setUser, isTokenExpired, tryRefreshToken } from '../../utils/auth.js';
	import { homePathForRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				tab: 'login',
				form: { username: '', password: '' },
				loading: false,
				reg: {
					username: '',
					password: '',
					realName: '',
					role: 'student',
					phone: '',
					inviteCode: ''
				},
				regLoading: false
			};
		},
		async onLoad() {
			// 冷启动免登录：Access 未过期直接进首页；已过期但本地有 RefreshToken 就静默续期再进，
			// 只有连 RefreshToken 都续不上（退出登录/被吊销）才留在登录页。这样“登录一次长期有效”。
			const user = getUser();
			if (!user) return;
			let session = user;
			if (!user.token || isTokenExpired(user.token)) {
				session = await tryRefreshToken();
			}
			if (session && session.token) {
				const hp = homePathForRole(session.role);
				if (hp) uni.reLaunch({ url: hp });
			}
		},
		methods: {
			async doLogin() {
				if (this.loading) return;
				if (!this.form.username || !this.form.password) {
					uni.showToast({ title: '请输入账号和密码', icon: 'none' });
					return;
				}
				this.loading = true;
				try {
					const user = await login({ username: this.form.username, password: this.form.password });
					const hp = user ? homePathForRole(user.role) : '';
					if (!hp) {
						uni.showModal({ title: '无法进入', content: '未识别的账号角色，请联系管理员', showCancel: false });
						return;
					}
					setUser(user);
					uni.showToast({ title: '登录成功', icon: 'success' });
					setTimeout(() => uni.reLaunch({ url: hp }), 500);
				} catch (e) {
					/* 错误已由请求拦截器提示 */
				} finally {
					this.loading = false;
				}
			},

			/* 切到注册页 */
			toRegister() {
				this.tab = 'register';
			},

			async doRegister() {
				if (this.regLoading) return;
				const r = this.reg;
				if (!r.username || !r.password || !r.realName) {
					uni.showToast({ title: '请填写用户名、密码和姓名', icon: 'none' });
					return;
				}
				if (String(r.password).length < 4) {
					uni.showToast({ title: '密码至少 4 位', icon: 'none' });
					return;
				}
				this.regLoading = true;
				try {
					await register({
						username: r.username,
						password: r.password,
						realName: r.realName,
						role: r.role,
						phone: r.phone || null,
						inviteCode: String(r.inviteCode || '').trim() || null
					});
					uni.showToast({ title: '注册成功，请登录', icon: 'success' });
					// 回填登录表单并切回登录页
					this.form.username = r.username;
					this.form.password = r.password;
					this.tab = 'login';
				} catch (e) {
					/* 错误已由请求拦截器提示 */
				} finally {
					this.regLoading = false;
				}
			}
		}
	}
</script>

<style scoped>
	.login-page {
		position: relative;
		min-height: 100vh;
	}
	/* 固定背景层 */
	.bg {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		overflow: hidden;
		background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
		z-index: 0;
	}
	.login-glow {
		position: absolute;
		border-radius: 50%;
		filter: blur(60px);
		opacity: 0.4;
	}
	.login-glow--top { top: -10%; left: -20%; width: 400rpx; height: 400rpx; background: rgba(64, 158, 255, 0.6); }
	.login-glow--bottom { bottom: -10%; right: -20%; width: 360rpx; height: 360rpx; background: rgba(240, 147, 251, 0.6); }

	/* 内容层：可随注册表单增高而自然滚动 */
	.content {
		position: relative;
		z-index: 1;
		min-height: 100vh;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 48rpx 32rpx;
		box-sizing: border-box;
	}

	.login-card {
		width: 620rpx;
		max-width: 100%;
		padding: 56rpx 48rpx 48rpx;
		border-radius: 32rpx;
		background: rgba(255, 255, 255, 0.94);
		box-shadow: 0 24rpx 60rpx rgba(0, 0, 0, 0.18);
		display: flex;
		flex-direction: column;
		align-items: center;
		box-sizing: border-box;
	}
	.login-logo {
		width: 120rpx;
		height: 120rpx;
		line-height: 120rpx;
		text-align: center;
		font-size: 60rpx;
		border-radius: 34rpx;
		background: linear-gradient(135deg, rgba(102, 126, 234, 0.16), rgba(56, 189, 248, 0.14));
	}
	.login-title { margin-top: 24rpx; font-size: 40rpx; font-weight: 700; color: #1f2d52; }

	/* Tab */
	.auth-tabs {
		display: flex;
		width: 100%;
		margin-top: 32rpx;
		border-bottom: 1rpx solid rgba(0, 0, 0, 0.06);
	}
	.auth-tab {
		flex: 1;
		text-align: center;
		padding: 18rpx 0;
		font-size: 30rpx;
		color: #909399;
		position: relative;
	}
	.auth-tab--active { color: #667eea; font-weight: 700; }
	.auth-tab--active::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: -1rpx;
		transform: translateX(-50%);
		width: 72rpx;
		height: 5rpx;
		border-radius: 4rpx;
		background: linear-gradient(90deg, #667eea, #38bdf8);
	}

	.field {
		display: flex;
		align-items: center;
		width: 100%;
		height: 88rpx;
		margin-top: 28rpx;
		padding: 0 24rpx;
		box-sizing: border-box;
		background: #f5f7fa;
		border-radius: 44rpx;
	}
	.field-icon { font-size: 28rpx; margin-right: 12rpx; }
	.field-input { flex: 1; font-size: 28rpx; color: #303133; }
	.ph { color: #a0a4ab; }
	.picker-text { line-height: 88rpx; }
	.picker-arrow { font-size: 30rpx; color: #a0a4ab; margin-left: 8rpx; }

	/* 身份选择卡片 */
	.role-grid {
		display: flex;
		width: 100%;
		gap: 16rpx;
		margin-top: 32rpx;
	}
	.role-card {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 22rpx 8rpx;
		border-radius: 20rpx;
		border: 2rpx solid #e4e7ed;
		background: rgba(245, 247, 250, 0.8);
	}
	.role-card--active {
		border-color: #667eea;
		background: rgba(102, 126, 234, 0.08);
	}
	.role-icon { font-size: 40rpx; }
	.role-name { margin-top: 10rpx; font-size: 24rpx; color: #606266; font-weight: 600; }
	.role-card--active .role-name { color: #667eea; }

	.login-btn { width: 100%; margin-top: 44rpx; }
	.reg-btn { width: 100%; margin-top: 40rpx; }
	.btn-hover { opacity: 0.9; }
</style>
