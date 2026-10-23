<template>
	<view class="page">
		<view class="sub-bar">{{ subject || '学科待确认' }} · {{ grade || '年级未填' }}</view>

		<view class="list">
			<view v-for="a in apps" :key="a.id" class="card acard">
				<view class="ahead">
					<image v-if="a.profile && a.profile.photoUrl" class="photo" :src="mediaUrl(a.profile.photoUrl)" mode="aspectFill" @click="previewOne(mediaUrl(a.profile.photoUrl))" />
					<view v-else class="photo photo-empty">照片</view>
					<view class="ainfo">
						<view class="row" style="gap:12rpx;flex-wrap:wrap;">
							<text class="aname">{{ (a.profile && a.profile.name) || a.teacherName || '老师' }}</text>
							<status-tag :type="statusTag(a.status)" :text="statusText(a.status)" />
						</view>
						<view class="abrief muted" v-if="briefLine1(a)">{{ briefLine1(a) }}</view>
						<view class="abrief muted" v-if="briefLine2(a)">{{ briefLine2(a) }}</view>
					</view>
				</view>

				<view class="agrid" v-if="gridCells(a).length">
					<view v-for="(c, i) in gridCells(a)" :key="i" class="acell"><text class="acell-k">{{ c.k }}</text>{{ c.v }}</view>
				</view>
				<view v-if="a.profile && a.profile.teachAdvantage" class="ablock"><text class="acell-k">辅导优势：</text>{{ a.profile.teachAdvantage }}</view>
				<view v-if="a.profile && a.profile.otherAdvantage" class="ablock"><text class="acell-k">其他优势：</text>{{ a.profile.otherAdvantage }}</view>
				<view v-if="awardImgs(a).length" class="ablock">
					<text class="acell-k">奖项证明：</text>
					<view class="thumbs">
						<image v-for="(img, i) in awardImgs(a)" :key="i" class="thumb" :src="img" mode="aspectFill" @click="preview(awardImgs(a), i)" />
					</view>
				</view>
				<view v-if="a.message" class="amsg">附言：{{ a.message }}</view>
				<view class="atime muted">{{ fmtDate(a.createTime) }} 投递</view>

				<view v-if="a.status === 0" class="aops">
					<view class="btn btn-plain op" @click="review(a, false)">暂不合适</view>
					<view class="btn btn-primary op" @click="review(a, true)">✓ 通过这份简历</view>
				</view>
				<view v-else-if="a.status === 1" class="passed">✅ 已通过：请通过管理员电话/微信联系后续对接事宜</view>
			</view>

			<empty-state v-if="!loading && !apps.length" text="还没有老师投递，请耐心等待" />
		</view>
	</view>
</template>

<script>
	import { parentDemandApplications, parentReviewApplication } from '../../api/index.js';
	import { fmtDate, mediaUrl, splitFiles } from '../../utils/format.js';

	const APP_STATUS_TEXT = { 0: '待处理', 1: '已通过', 2: '未通过' };
	const APP_STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'danger' };

	export default {
		data() {
			return {
				demandId: null,
				subject: '',
				grade: '',
				apps: [],
				loading: false
			};
		},
		onLoad(opt) {
			this.demandId = opt.id;
			this.subject = opt.subject ? decodeURIComponent(opt.subject) : '';
			this.grade = opt.grade ? decodeURIComponent(opt.grade) : '';
		},
		onShow() { this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			fmtDate, mediaUrl,
			statusText(s) { return APP_STATUS_TEXT[s] != null ? APP_STATUS_TEXT[s] : s; },
			statusTag(s) { return APP_STATUS_TAG[s] || 'info'; },
			async load() {
				this.loading = true;
				try {
					const data = await parentDemandApplications(this.demandId);
					this.apps = data.records || data.list || [];
				} catch (e) { this.apps = []; } finally {
					this.loading = false;
				}
			},
			briefLine1(a) {
				const p = a.profile || {};
				return [p.gender, p.age ? p.age + '岁' : '', p.school, p.major].filter(Boolean).join(' · ');
			},
			briefLine2(a) {
				const p = a.profile || {};
				return [p.gradeYear, p.teachYears ? '教龄' + p.teachYears : '', p.hometown].filter(Boolean).join(' · ');
			},
			gridCells(a) {
				const p = a.profile || {};
				const cells = [
					{ k: '接单科目', v: p.subjects }, { k: '可上课时间', v: p.availableTime },
					{ k: '长期授课', v: p.longTerm }, { k: '可教教材版本', v: p.textbookVer },
					{ k: '可辅导年级及科目', v: p.teachScope }, { k: '备有辅导教材', v: p.hasMaterial }
				];
				return cells.filter((c) => c.v);
			},
			awardImgs(a) {
				const p = a.profile || {};
				return splitFiles(p.awardImgs).map(mediaUrl);
			},
			preview(urls, idx) { uni.previewImage({ urls, current: idx }); },
			previewOne(url) { uni.previewImage({ urls: [url] }); },
			review(a, approved) {
				const name = (a.profile && a.profile.name) || a.teacherName || '该老师';
				const tip = approved
					? `确定通过「${name}」的简历吗？通过后该家教单将关闭，其他老师无法再投递。`
					: `确定标记「${name}」的简历为暂不合适吗？`;
				uni.showModal({
					title: approved ? '通过简历' : '暂不合适', content: tip,
					confirmColor: approved ? '#16a34a' : '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							const res = await parentReviewApplication(a.id, approved);
							a.status = approved ? 1 : 2;
							if (approved) this.showContact(res || {});
							else uni.showToast({ title: '已标记为暂不合适', icon: 'success' });
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			showContact(info) {
				const phone = info.adminPhone;
				const wechat = info.adminWechat;
				if (!phone && !wechat) {
					uni.showModal({ title: '🎉 简历已通过', content: '家教单已关闭。管理员暂未配置联系方式，请通过其他渠道联系管理员。', showCancel: false });
					return;
				}
				const lines = ['简历已通过，家教单已关闭。后续对接请联系管理员：'];
				if (phone) lines.push(`📞 电话：${phone}`);
				if (wechat) lines.push(`💬 微信：${wechat}`);
				uni.showModal({
					title: '🎉 简历已通过', content: lines.join('\n'),
					confirmText: phone ? '拨打管理员' : '我知道了',
					showCancel: !!phone, cancelText: '关闭',
					success: (r) => {
						if (phone && r.confirm) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} });
						if (wechat && (!phone || !r.confirm)) uni.setClipboardData({ data: wechat, success: () => uni.showToast({ title: '微信号已复制', icon: 'none' }) });
					}
				});
			}
		}
	};
</script>

<style scoped>
	.sub-bar { padding: 18rpx 24rpx; background: #ffffff; font-size: 24rpx; color: #6b7280; }
	.list { padding: 20rpx 24rpx 40rpx; }
	.acard { margin-bottom: 20rpx; }
	.ahead { display: flex; gap: 18rpx; }
	.photo { width: 130rpx; height: 165rpx; border-radius: 14rpx; background: #f2f3f8; flex-shrink: 0; }
	.photo-empty { display: flex; align-items: center; justify-content: center; font-size: 22rpx; color: #c0c4cc; }
	.ainfo { flex: 1; min-width: 0; }
	.aname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.abrief { margin-top: 6rpx; font-size: 24rpx; line-height: 1.5; }
	.agrid { margin-top: 16rpx; display: flex; flex-direction: column; gap: 8rpx; }
	.acell { font-size: 25rpx; color: #303133; line-height: 1.5; }
	.acell-k { color: #909399; margin-right: 8rpx; }
	.ablock { margin-top: 12rpx; font-size: 25rpx; color: #303133; line-height: 1.6; }
	.thumbs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 8rpx; }
	.thumb { width: 140rpx; height: 140rpx; border-radius: 10rpx; background: #f2f4f8; }
	.amsg { margin-top: 12rpx; font-size: 24rpx; color: #6b7280; background: #f5f7fa; border-radius: 10rpx; padding: 14rpx 16rpx; line-height: 1.6; }
	.atime { margin-top: 12rpx; font-size: 22rpx; }
	.aops { margin-top: 16rpx; display: flex; gap: 16rpx; }
	.aops .op { flex: 1; }
	.passed { margin-top: 16rpx; font-size: 24rpx; color: #16a34a; background: #f0f9eb; border-radius: 10rpx; padding: 14rpx 16rpx; line-height: 1.5; }
</style>
