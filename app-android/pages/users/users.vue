<template>
	<view class="page">
		<search-bar v-model="query.keyword" placeholder="搜索账号 / 姓名 / 手机号" @search="reload" @clear="reload">
			<template #extra>
				<picker class="pk" mode="selector" :range="roleLabels" :value="roleIdx" @change="onRoleChange">
					<view class="pk-in">{{ roleLabels[roleIdx] }} ▾</view>
				</picker>
				<picker class="pk" mode="selector" :range="statusLabels" :value="statusIdx" @change="onStatusChange">
					<view class="pk-in">{{ statusLabels[statusIdx] }} ▾</view>
				</picker>
			</template>
		</search-bar>

		<view class="list">
			<view v-for="u in list" :key="u.id" class="card ucard">
				<view class="row">
					<view class="row" style="flex-wrap:wrap;">
						<text class="uname">{{ u.realName || '—' }}</text>
						<text class="muted uaccount">@{{ u.username }}</text>
						<status-tag :type="roleTag(u.role)" :text="roleText(u.role)" />
						<status-tag :type="u.status === 1 ? 'success' : 'danger'" :text="u.status === 1 ? '正常' : '已封禁'" />
					</view>
				</view>

				<view class="kv">
					<text v-if="u.phone" class="tel-link" @click="callPhone(u.phone)">📞 {{ u.phone }}</text>
					<text v-else class="muted">未填手机号</text>
				</view>

				<view class="kv invite-line" v-if="u.inviteCode">
					<text class="muted">邀请码</text>
					<text class="icode">{{ u.inviteCode }}</text>
					<text class="icount" @click="openInvites(u)">已邀请 {{ u.invitedCount || 0 }} 人 ›</text>
					<text v-if="u.inviterName" class="muted">来自 {{ u.inviterName }}</text>
				</view>

				<view class="kv" v-if="u.role === 'student' && (u.teacherName || u.parentName)">
					<text class="muted">关联</text>
					<text v-if="u.teacherName" class="rel">老师：{{ u.teacherName }}</text>
					<text v-if="u.parentName" class="rel">家长：{{ u.parentName }}</text>
				</view>

				<view class="kv muted">注册于 {{ fmtTime(u.createTime) }}</view>

				<view class="ops">
					<text class="mini-btn" @click="resetPwd(u)">重置密码</text>
					<text class="mini-btn" :class="u.status === 1 ? 'mini-btn-danger' : 'mini-btn-success'" @click="toggleStatus(u)">
						{{ u.status === 1 ? '封禁' : '解封' }}
					</text>
					<text v-if="u.role !== 'admin'" class="mini-btn mini-btn-primary" @click="more(u)">更多 ▾</text>
				</view>
			</view>

			<empty-state v-if="!loading && !list.length" text="没有符合条件的用户" />
			<load-more :status="moreStatus" />
		</view>
	</view>
</template>

<script>
	import {
		adminUsers, adminResetPassword, adminSetUserStatus, adminUpdateRole,
		adminUnbind, adminUserInvites, adminUpdatePassword, adminDeleteUser
	} from '../../api/index.js';
	import { roleText, roleTag, fmtTime } from '../../utils/format.js';

	const ROLE_VALUES = ['', 'teacher', 'student', 'parent', 'admin'];
	const STATUS_VALUES = ['', '1', '0'];

	export default {
		data() {
			return {
				query: { keyword: '', page: 1, size: 10 },
				roleIdx: 0,
				statusIdx: 0,
				roleLabels: ['全部角色', '老师', '学生', '家长', '管理员'],
				statusLabels: ['全部状态', '正常', '已封禁'],
				list: [],
				total: 0,
				loading: false
			};
		},
		computed: {
			moreStatus() {
				if (this.loading) return 'loading';
				if (this.list.length >= this.total) return 'noMore';
				return '';
			}
		},
		onLoad() { this.reload(); },
		onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()); },
		onReachBottom() {
			if (!this.loading && this.list.length < this.total) this.load(this.query.page + 1);
		},
		methods: {
			roleText, roleTag, fmtTime,
			buildParams(page) {
				const p = { page, size: this.query.size };
				if (this.query.keyword) p.keyword = this.query.keyword;
				const role = ROLE_VALUES[this.roleIdx];
				const status = STATUS_VALUES[this.statusIdx];
				if (role) p.role = role;
				if (status) p.status = status;
				return p;
			},
			reload() { return this.load(1); },
			async load(page) {
				this.loading = true;
				try {
					const data = await adminUsers(this.buildParams(page));
					const rows = data.list || [];
					this.list = page === 1 ? rows : this.list.concat(rows);
					this.total = data.total || 0;
					this.query.page = page;
				} catch (e) { /* 拦截器已提示 */ } finally {
					this.loading = false;
				}
			},
			onRoleChange(e) { this.roleIdx = Number(e.detail.value); this.reload(); },
			onStatusChange(e) { this.statusIdx = Number(e.detail.value); this.reload(); },
			callPhone(phone) {
				if (!phone) return;
				uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} });
			},
			resetPwd(u) {
				uni.showModal({
					title: '重置密码',
					content: `确定将「${u.realName || u.username}」的密码重置为随机新密码？`,
					success: async (r) => {
						if (!r.confirm) return;
						try {
							const newPwd = await adminResetPassword(u.id);
							uni.showModal({ title: '新密码', content: String(newPwd), showCancel: false });
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			toggleStatus(u) {
				const ban = u.status === 1;
				uni.showModal({
					title: ban ? '封禁账号' : '解封账号',
					content: ban ? `封禁后「${u.realName || u.username}」将无法登录，确定封禁？` : `确定解除「${u.realName || u.username}」的封禁状态？`,
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminSetUserStatus(u.id, ban ? 0 : 1);
							uni.showToast({ title: ban ? '已封禁' : '已解封', icon: 'success' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			more(u) {
				const items = ['调整角色', '修改密码'];
				const acts = ['role', 'password'];
				if (u.parentId) { items.push('解除家长绑定'); acts.push('parent'); }
				if (u.teacherId) { items.push('解除老师绑定'); acts.push('teacher'); }
				items.push('删除用户'); acts.push('delete');
				uni.showActionSheet({
					itemList: items,
					success: (res) => {
						const act = acts[res.tapIndex];
						if (act === 'role') this.changeRole(u);
						else if (act === 'password') this.changePassword(u);
						else if (act === 'delete') this.deleteUser(u);
						else this.unbind(u, act);
					}
				});
			},
			changePassword(u) {
				uni.showModal({
					title: '修改密码', editable: true, placeholderText: `为「${u.realName || u.username}」设置新密码（至少6位）`,
					success: async (r) => {
						if (!r.confirm) return;
						const pwd = String(r.content || '').trim();
						if (pwd.length < 6) { uni.showToast({ title: '新密码至少6位', icon: 'none' }); return; }
						try {
							await adminUpdatePassword(u.id, pwd);
							uni.showToast({ title: '密码已修改', icon: 'success' });
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			deleteUser(u) {
				uni.showModal({
					title: '删除用户', content: `确定删除「${u.realName || u.username}」吗？此操作不可恢复，将同时清除其绑定关系与登录态！`, confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminDeleteUser(u.id);
							uni.showToast({ title: '已删除', icon: 'success' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			changeRole(u) {
				const labels = ['老师', '学生', '家长'];
				const values = ['teacher', 'student', 'parent'];
				uni.showActionSheet({
					itemList: labels,
					success: (res) => {
						const role = values[res.tapIndex];
						uni.showModal({
							title: '调整角色',
							content: `确定将「${u.realName || u.username}」的角色改为${labels[res.tapIndex]}？`,
							success: async (r) => {
								if (!r.confirm) return;
								try {
									await adminUpdateRole(u.id, role);
									uni.showToast({ title: '已更新', icon: 'success' });
									this.reload();
								} catch (e) { /* 已提示 */ }
							}
						});
					}
				});
			},
			unbind(u, type) {
				uni.showModal({
					title: '解除绑定',
					content: `解绑后该学生可重新扫码绑定新的${type === 'parent' ? '家长' : '老师'}，原绑定关系清除。确定解绑？`,
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminUnbind(u.id, type);
							uni.showToast({ title: '已解绑', icon: 'success' });
							this.reload();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			openInvites(u) {
				const name = encodeURIComponent(u.realName || u.username || '');
				const code = u.inviteCode || '';
				uni.navigateTo({ url: `/pages/invites/invites?id=${u.id}&name=${name}&code=${code}` });
			}
		}
	};
</script>

<style scoped>
	.list { padding: 20rpx 24rpx 40rpx; }
	.ucard { margin-bottom: 20rpx; }
	.uname { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.uaccount { font-size: 24rpx; margin-left: 8rpx; }
	.kv { margin-top: 12rpx; font-size: 26rpx; color: #4b5563; display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; }
	.invite-line .muted { font-size: 24rpx; }
	.icode {
		font-family: Consolas, Monaco, monospace;
		letter-spacing: 2rpx;
		color: #6c5ce7;
		background: rgba(108, 92, 231, 0.1);
		border-radius: 8rpx;
		padding: 2rpx 12rpx;
		font-size: 24rpx;
	}
	.icount { color: #667eea; font-size: 24rpx; }
	.rel { font-size: 24rpx; color: #6b7280; }
	.ops { margin-top: 20rpx; display: flex; gap: 16rpx; flex-wrap: wrap; }

	.pk { flex-shrink: 0; }
	.pk-in {
		font-size: 24rpx;
		color: #4b5563;
		background: #eef1f7;
		border-radius: 999rpx;
		padding: 8rpx 20rpx;
	}
</style>
