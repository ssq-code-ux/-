<template>
	<view class="page">
		<view class="card code-card">
			<view class="code-label">我的专属邀请码</view>
			<view class="code">{{ info.inviteCode || '—' }}</view>
			<view class="code-tip">同行在注册页填写这个码，邀请关系就记在您名下</view>
			<view class="btn btn-primary copy-btn" @click="copyInvite">📋 复制邀请码与链接</view>
		</view>

		<view class="card">
			<view class="card-title">👥 已邀请 <text class="hl">{{ info.invitedCount || 0 }}</text> 位老师入驻</view>
			<view v-if="loading" class="muted">加载中…</view>
			<template v-else>
				<view v-if="(info.invitedUsers || []).length" class="list">
					<view v-for="u in info.invitedUsers" :key="u.id" class="item">
						<view class="avatar">{{ (u.realName || u.username || '?').slice(0, 1) }}</view>
						<view class="item-main">
							<text class="item-name">{{ u.realName || u.username }}</text>
							<text class="muted item-meta">{{ roleText(u.role) }} · {{ fmtDate(u.createTime) }} 注册</text>
						</view>
					</view>
				</view>
				<empty-state v-else text="还没有邀请到同行，把邀请码发到同行群里试试" />
			</template>
		</view>
	</view>
</template>

<script>
	import { myInviteInfo } from '../../api/index.js';
	import { fmtDate, roleText } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';
	import { SITE_URL } from '../../config.js';

	export default {
		data() {
			return { info: {}, loading: false };
		},
		onLoad() {
			if (!guardRole('teacher')) return;
		},
		onShow() { this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtDate, roleText,
			async load() {
				this.loading = true;
				try { this.info = (await myInviteInfo()) || {}; } catch (e) { this.info = {}; } finally { this.loading = false; }
			},
			copyInvite() {
				const code = this.info.inviteCode;
				if (!code) { uni.showToast({ title: '邀请码还没生成，请稍后重试', icon: 'none' }); return; }
				const text = `我在「智能学习助手」平台接家教单，用我的邀请码 ${code} 注册就能一起接单：${SITE_URL}/login?invite=${code}`;
				uni.setClipboardData({ data: text, success: () => uni.showToast({ title: '邀请码与链接已复制', icon: 'none' }) });
			}
		}
	};
</script>

<style scoped>
	.code-card { text-align: center; background: linear-gradient(135deg, #14b8a6, #0ea5e9); }
	.code-label { font-size: 25rpx; color: rgba(255, 255, 255, 0.85); }
	.code { margin: 16rpx 0; font-size: 64rpx; font-weight: 800; letter-spacing: 6rpx; color: #ffffff; }
	.code-tip { font-size: 23rpx; color: rgba(255, 255, 255, 0.9); line-height: 1.6; }
	.copy-btn { margin-top: 26rpx; background: #ffffff; color: #0d9488; font-weight: 700; }

	.hl { color: #0d9488; font-size: 34rpx; font-weight: 800; }
	.list { margin-top: 10rpx; }
	.item { display: flex; align-items: center; gap: 18rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f2f4f8; }
	.item:last-child { border-bottom: none; }
	.avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #e6fffa; color: #0d9488; font-size: 30rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
	.item-main { display: flex; flex-direction: column; gap: 4rpx; }
	.item-name { font-size: 28rpx; color: #1f2d52; font-weight: 600; }
	.item-meta { font-size: 23rpx; }
</style>
