<template>
	<view class="page">
		<view class="grad-header">
			<view class="title-lg">🎫 {{ owner }} 邀请的账号</view>
			<view v-if="code" class="head-sub">
				邀请码 <text class="icode">{{ code }}</text> · 已邀请 {{ list.length }} 人
			</view>
		</view>

		<view class="list">
			<view v-for="u in list" :key="u.id || u.username" class="card ucard">
				<view class="row-between">
					<view class="row" style="flex-wrap:wrap;">
						<text class="uname">{{ u.realName || '—' }}</text>
						<text class="muted uaccount">@{{ u.username }}</text>
					</view>
					<status-tag :type="roleTag(u.role)" :text="roleText(u.role)" />
				</view>
				<view class="kv">
					<text v-if="u.phone" class="tel-link" @click="callPhone(u.phone)">📞 {{ u.phone }}</text>
					<text v-else class="muted">未填手机号</text>
				</view>
				<view class="kv muted">注册于 {{ fmtTime(u.createTime) }}</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="这位老师还没有邀请到任何人" />
			<load-more :status="loading ? 'loading' : ''" />
		</view>
	</view>
</template>

<script>
	import { adminUserInvites } from '../../api/index.js';
	import { roleText, roleTag, fmtTime } from '../../utils/format.js';

	export default {
		data() {
			return { id: '', owner: '', code: '', list: [], loading: false };
		},
		onLoad(opt) {
			this.id = opt.id || '';
			this.owner = decodeURIComponent(opt.name || '');
			this.code = opt.code || '';
			if (this.owner) uni.setNavigationBarTitle({ title: `${this.owner} 的邀请名单` });
			this.load();
		},
		methods: {
			roleText, roleTag, fmtTime,
			async load() {
				if (!this.id) return;
				this.loading = true;
				try {
					this.list = (await adminUserInvites(this.id)) || [];
				} catch (e) { /* 拦截器已提示 */ } finally {
					this.loading = false;
				}
			},
			callPhone(phone) {
				if (!phone) return;
				uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} });
			}
		}
	};
</script>

<style scoped>
	.head-sub { margin-top: 12rpx; font-size: 26rpx; color: rgba(255, 255, 255, 0.9); }
	.icode {
		font-family: Consolas, Monaco, monospace;
		letter-spacing: 2rpx;
		background: rgba(255, 255, 255, 0.22);
		border-radius: 8rpx;
		padding: 2rpx 12rpx;
	}
	.list { padding: 20rpx 24rpx 40rpx; }
	.ucard { margin-bottom: 20rpx; }
	.uname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.uaccount { font-size: 24rpx; margin-left: 8rpx; }
	.kv { margin-top: 12rpx; font-size: 26rpx; color: #4b5563; }
</style>
