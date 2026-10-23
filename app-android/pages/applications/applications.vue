<template>
	<view class="page">
		<!-- 顶部提示 -->
		<view v-if="job.status === 0 && job.approvedTeacherId" class="alert alert-ok">
			该单已关闭：已通过一位老师的投递，不再接受投递
		</view>
		<view v-if="settledCount > 0" class="alert alert-warn">
			列表含 {{ settledCount }} 条试课失败 / 已退费的历史记录；待退费的请到「退费工单」统一办理
		</view>

		<!-- 家长联系方式 -->
		<view class="card" v-if="parentContact">
			<view class="apc-title">📞 该单家长联系方式</view>
			<view class="apc-line">
				<text>{{ parentContact.contactName || '—' }}</text>
				<text v-if="parentContact.phone" class="tel-link" @click="callPhone(parentContact.phone)"> 📞 {{ parentContact.phone }}</text>
				<text v-if="parentContact.wechat"> · 💬 {{ parentContact.wechat }}</text>
			</view>
		</view>

		<!-- 投递列表 -->
		<view class="list">
			<empty-state v-if="!appLoading && !appList.length" text="暂无投递" />

			<view v-for="row in appList" :key="row.id" class="card appcard">
				<view class="app-head">
					<image v-if="row.profile && row.profile.photoUrl" :src="row.profile.photoUrl" class="app-photo" mode="aspectFill" @click="preview(row.profile.photoUrl)" />
					<view v-else class="app-photo app-photo-empty">照片</view>
					<view class="app-head-info">
						<view class="app-name">
							<text class="aname">{{ (row.profile && row.profile.name) || row.teacherName || '老师' }}</text>
							<status-tag :type="appStatusTag(row)" :text="appStatusText(row)" />
							<status-tag v-if="row.status === 1 && row.trialStatus != null" :type="TRIAL_TAG[row.trialStatus]" :text="TRIAL_TEXT[row.trialStatus]" />
							<status-tag v-if="Number(row.paidAmount) > 0" type="danger" :text="'已缴 ' + row.paidAmount + '元'" />
						</view>
						<view class="app-brief">
							<text v-if="row.teacherPhone" class="tel-link" @click="callPhone(row.teacherPhone)">📞 {{ row.teacherPhone }}</text>
							<text v-else>📞 —</text>
							<text v-if="brief(row)">{{ brief(row) }}</text>
						</view>
						<view class="app-brief" v-if="brief2(row)">{{ brief2(row) }}</view>
					</view>
				</view>

				<view class="app-grid" v-if="gridCells(row).length">
					<view v-for="(c, i) in gridCells(row)" :key="i" class="app-cell">
						<text class="k">{{ c.k }}</text>{{ c.v }}
					</view>
				</view>
				<view v-if="row.profile && row.profile.teachAdvantage" class="app-block"><text class="k">辅导优势：</text>{{ row.profile.teachAdvantage }}</view>
				<view v-if="row.profile && row.profile.otherAdvantage" class="app-block"><text class="k">其他优势：</text>{{ row.profile.otherAdvantage }}</view>
				<view v-if="awardImgs(row).length" class="app-block">
					<text class="k">奖项证明：</text>
					<view class="award-thumbs">
						<image v-for="(img, i) in awardImgs(row)" :key="i" :src="img" class="award-thumb" mode="aspectFill" @click="preview(img)" />
					</view>
				</view>
				<view v-if="row.message" class="app-msg">附言：{{ row.message }}</view>
				<view class="app-time">{{ fmtTime(row.createTime) }} 投递</view>

				<view class="app-ops">
					<block v-if="row.trialStatus === 2">
						<text class="trial-tip">老师反馈试课失败，请尽快办理退费</text>
						<text v-if="Number(row.paidAmount) > 0" class="mini-btn mini-btn-danger" @click="onRefund(row)">办理退费({{ row.paidAmount }}元)</text>
						<text v-else class="mini-btn" @click="onRefund(row)">标记已处理</text>
					</block>
					<block v-else-if="row.trialStatus === 3">
						<text class="muted">✓ 试课退费已完成</text>
					</block>
					<block v-else-if="row.status === 0 && job.status === 1">
						<text class="mini-btn" @click="onMarkPaid(row)">💰 标记缴费</text>
						<text v-if="Number(row.paidAmount) > 0" class="mini-btn" @click="onRefund(row)">↩️ 退费</text>
						<text class="mini-btn mini-btn-danger" @click="onReview(row, false)">不通过</text>
						<text class="mini-btn mini-btn-primary" @click="onReview(row, true)">✓ 通过</text>
					</block>
					<block v-else-if="row.status === 0">
						<text class="mini-btn" @click="onMarkPaid(row)">💰 标记缴费</text>
						<text v-if="Number(row.paidAmount) > 0" class="mini-btn" @click="onRefund(row)">↩️ 退费</text>
						<text class="muted">单子已关闭，仅可管理缴费</text>
					</block>
				</view>
			</view>
		</view>

		<!-- 缴费解锁管理 -->
		<view class="card">
			<view class="row-between">
				<text class="card-title" style="margin-bottom:0;">💰 缴费解锁管理</text>
				<text class="mini-btn mini-btn-primary" @click="openGrant">➕ 开通缴费解锁</text>
			</view>
			<view class="muted pay-tip">需缴费的单：教师扫收款码缴费后，在此选中老师标记金额，即可开通其对本单的投递资格</view>
			<view v-if="payList.length" class="pay-list">
				<view v-for="p in payList" :key="p.teacherId" class="pay-row">
					<view class="pay-info">
						<text class="pay-name">{{ p.teacherName || ('老师#' + p.teacherId) }}</text>
						<text class="muted pay-sub">
							{{ Number(p.amount) > 0 ? ('已缴 ' + p.amount + ' 元 · ' + (fmtTime(p.paidTime) || '—')) : '已退清' }}
							<text v-if="p.refundTime"> · 退费 {{ fmtTime(p.refundTime) }}</text>
						</text>
					</view>
					<text v-if="Number(p.amount) > 0" class="mini-btn mini-btn-danger" @click="onRefundPayRow(p)">↩️ 退费</text>
				</view>
			</view>
			<view v-else class="muted">本单暂无缴费记录</view>
		</view>

		<!-- 开通缴费解锁面板 -->
		<view v-if="grantVisible" class="mask" @click="grantVisible = false">
			<view class="sheet" @click.stop>
				<view class="sheet-title">💰 开通缴费解锁</view>
				<view class="muted sheet-sub">{{ job.jobNo }} · {{ job.subject }}：开通后该老师即可投递本单</view>
				<view class="search-line">
					<input class="sheet-input" v-model="grantKeyword" placeholder="输入账号 / 姓名 / 手机号" confirm-type="search" @confirm="searchTeachers" />
					<text class="mini-btn mini-btn-primary" @click="searchTeachers">搜索</text>
				</view>
				<view v-if="grantSearching" class="muted sheet-hint">搜索中…</view>
				<scroll-view v-else-if="grantOptions.length" scroll-y class="teacher-list">
					<view v-for="t in grantOptions" :key="t.id" class="teacher-item" :class="{ active: grantTeacherId === t.id }" @click="grantTeacherId = t.id">
						<text class="t-name">{{ t.realName || t.username }}</text>
						<text class="muted t-sub">{{ t.username }}<text v-if="t.phone"> · 📞 {{ t.phone }}</text></text>
					</view>
				</scroll-view>
				<view v-if="grantTeacherId" class="amount-line">
					<text class="k">实缴金额</text>
					<input class="sheet-input" v-model="grantAmount" type="digit" placeholder="元，如：50 或 49.9" />
				</view>
				<view class="sheet-ops">
					<text class="btn btn-plain" @click="grantVisible = false">取消</text>
					<text class="btn btn-primary" @click="submitGrant">确认已缴费并开通</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import {
		adminTutorJobDetail, adminTutorJobApplications, adminReviewTutorJobApplication,
		adminMarkPaid, adminRefundPaid, adminJobPayments, adminUsers
	} from '../../api/index.js';
	import { fmtTime, TRIAL_TEXT, TRIAL_TAG } from '../../utils/format.js';

	const AMOUNT_RE = /^\d+(\.\d{1,2})?$/;

	export default {
		data() {
			return {
				jobId: '',
				job: {},
				appList: [],
				parentContact: null,
				payList: [],
				appLoading: false,
				TRIAL_TEXT, TRIAL_TAG,
				grantVisible: false,
				grantKeyword: '',
				grantOptions: [],
				grantSearching: false,
				grantTeacherId: null,
				grantAmount: ''
			};
		},
		computed: {
			settledCount() {
				return this.appList.filter((r) => r.trialStatus === 2 || r.trialStatus === 3).length;
			}
		},
		onLoad(opt) {
			this.jobId = opt.jobId || '';
			this.loadAll();
		},
		methods: {
			fmtTime,
			appStatusText(row) { return ['待处理', '已通过', '未通过'][row.status] || '—'; },
			appStatusTag(row) { return row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'; },
			brief(row) {
				const p = row.profile || {};
				return [p.gender, p.age ? p.age + '岁' : '', p.school, p.major, p.gradeYear].filter(Boolean).map((s) => ' · ' + s).join('');
			},
			brief2(row) {
				const p = row.profile || {};
				return [p.teachYears ? '教龄 ' + p.teachYears : '', p.hometown ? '籍贯 ' + p.hometown : ''].filter(Boolean).join(' · ');
			},
			gridCells(row) {
				const p = row.profile || {};
				const map = [
					['接单科目', p.subjects], ['可上课时间', p.availableTime], ['长期授课', p.longTerm],
					['可教教材版本', p.textbookVer], ['可辅导年级及科目', p.teachScope], ['备有辅导教材', p.hasMaterial]
				];
				return map.filter((m) => m[1]).map((m) => ({ k: m[0], v: m[1] }));
			},
			awardImgs(row) {
				const p = row.profile || {};
				if (!p.awardImgs) return [];
				return String(p.awardImgs).split(',').filter(Boolean);
			},
			preview(url) { if (url) uni.previewImage({ urls: [url] }); },
			callPhone(phone) { if (phone) uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} }); },

			async loadAll() {
				if (!this.jobId) return;
				this.appLoading = true;
				try {
					const detail = await adminTutorJobDetail(this.jobId);
					this.job = detail || {};
					uni.setNavigationBarTitle({ title: `投递(${this.job.jobNo || ''})` });
				} catch (e) { /* 已提示 */ }
				try {
					const data = await adminTutorJobApplications(this.jobId, { page: 1, size: 100 });
					this.appList = data.list || [];
					this.parentContact = data.parentContact || null;
				} catch (e) { /* 已提示 */ }
				this.loadPayList();
				this.appLoading = false;
			},
			async loadPayList() {
				try {
					const data = await adminJobPayments(this.jobId);
					this.payList = data.list || [];
				} catch (e) { /* 已提示 */ }
			},
			async reloadApps() {
				try {
					const data = await adminTutorJobApplications(this.jobId, { page: 1, size: 100 });
					this.appList = data.list || [];
				} catch (e) { /* 已提示 */ }
			},

			onReview(row, approved) {
				const teacher = (row.profile && row.profile.name) || row.teacherName || ('老师#' + row.teacherId);
				uni.showModal({
					title: approved ? '通过投递' : '不通过',
					content: approved
						? `确定通过「${teacher}」的投递吗？通过后该家教单将立即关闭，不再接受投递。`
						: `确定将「${teacher}」的投递标记为不通过吗？`,
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminReviewTutorJobApplication(row.id, approved);
							uni.showToast({ title: approved ? '已通过，该单已关闭' : '已标记不通过', icon: 'none' });
							this.job.status = approved ? 0 : this.job.status;
							if (approved) this.job.approvedTeacherId = row.teacherId;
							await this.reloadApps();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			onMarkPaid(row) {
				const teacher = (row.profile && row.profile.name) || row.teacherName || ('老师#' + row.teacherId);
				uni.showModal({
					title: '标记缴费',
					editable: true,
					placeholderText: '请输入实缴金额（元），如 50',
					content: `确认「${teacher}」已缴费`,
					success: async (r) => {
						if (!r.confirm) return;
						const amount = String(r.content || '').trim();
						if (!AMOUNT_RE.test(amount)) { uni.showToast({ title: '金额格式不正确', icon: 'none' }); return; }
						try {
							await adminMarkPaid(this.jobId, row.teacherId, { amount });
							uni.showToast({ title: `已标记缴费 ${amount} 元`, icon: 'none' });
							await this.reloadApps();
							this.loadPayList();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			onRefund(row) {
				const teacher = (row.profile && row.profile.name) || row.teacherName || ('老师#' + row.teacherId);
				const trialFailed = row.trialStatus === 2;
				const hasMoney = Number(row.paidAmount) > 0;
				uni.showModal({
					title: trialFailed ? (hasMoney ? '试课失败退费' : '标记已处理') : '退费并清零',
					content: trialFailed
						? (hasMoney
							? `「${teacher}」反馈试课失败，确定退回其已缴的 ${row.paidAmount} 元吗？退费后该投递置为"已退费"。`
							: `「${teacher}」反馈试课失败，该单无需缴费。确认后即把这条记录结算为"已处理"。`)
						: `确定向「${teacher}」退回已缴的 ${row.paidAmount} 元吗？退费后已缴金额将清零。`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminRefundPaid(this.jobId, row.teacherId, { note: trialFailed ? '试课失败退费' : '未接单退费' });
							uni.showToast({ title: '已处理', icon: 'success' });
							await this.reloadApps();
							this.loadPayList();
						} catch (e) { /* 已提示 */ }
					}
				});
			},
			onRefundPayRow(p) {
				const teacher = p.teacherName || ('老师#' + p.teacherId);
				uni.showModal({
					title: '退费并清零',
					content: `确定向「${teacher}」退回已缴的 ${p.amount} 元吗？退费后已缴金额将清零。`,
					confirmColor: '#e11d48',
					success: async (r) => {
						if (!r.confirm) return;
						try {
							await adminRefundPaid(this.jobId, p.teacherId, { note: '未接单退费' });
							uni.showToast({ title: '已退费', icon: 'success' });
							this.loadPayList();
							await this.reloadApps();
						} catch (e) { /* 已提示 */ }
					}
				});
			},

			/* 开通缴费解锁面板 */
			openGrant() {
				this.grantKeyword = '';
				this.grantOptions = [];
				this.grantTeacherId = null;
				this.grantAmount = '';
				this.grantVisible = true;
			},
			async searchTeachers() {
				const kw = this.grantKeyword.trim();
				if (!kw) { uni.showToast({ title: '请输入搜索关键词', icon: 'none' }); return; }
				this.grantSearching = true;
				try {
					const data = await adminUsers({ keyword: kw, role: 'teacher', page: 1, size: 10 });
					this.grantOptions = data.list || [];
					if (!this.grantOptions.length) uni.showToast({ title: '未搜到匹配的老师', icon: 'none' });
				} catch (e) { /* 已提示 */ } finally {
					this.grantSearching = false;
				}
			},
			async submitGrant() {
				if (!this.grantTeacherId) { uni.showToast({ title: '请先搜索并选中一位老师', icon: 'none' }); return; }
				const amount = String(this.grantAmount).trim();
				if (!AMOUNT_RE.test(amount)) { uni.showToast({ title: '金额格式不正确', icon: 'none' }); return; }
				try {
					await adminMarkPaid(this.jobId, this.grantTeacherId, { amount });
					uni.showToast({ title: `已开通，标记缴费 ${amount} 元`, icon: 'none' });
					this.grantVisible = false;
					this.loadPayList();
					await this.reloadApps();
				} catch (e) { /* 已提示 */ }
			}
		}
	};
</script>

<style scoped>
	.alert { margin: 20rpx 24rpx 0; padding: 16rpx 20rpx; border-radius: 14rpx; font-size: 24rpx; line-height: 1.5; }
	.alert-ok { background: #ecfdf5; color: #047857; }
	.alert-warn { background: #fffbeb; color: #b45309; }

	.list { padding: 20rpx 24rpx 0; }
	.appcard { margin-bottom: 20rpx; }
	.apc-title { font-weight: 600; color: #b88230; font-size: 28rpx; margin-bottom: 10rpx; }
	.apc-line { font-size: 26rpx; color: #5c4827; line-height: 1.7; }

	.app-head { display: flex; gap: 16rpx; }
	.app-photo { width: 96rpx; height: 96rpx; border-radius: 12rpx; flex-shrink: 0; background: #f2f4f8; }
	.app-photo-empty { display: flex; align-items: center; justify-content: center; font-size: 22rpx; color: #c0c4cc; }
	.app-head-info { flex: 1; min-width: 0; }
	.app-name { display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; }
	.aname { font-size: 30rpx; font-weight: 700; color: #1f2d52; }
	.app-brief { margin-top: 8rpx; font-size: 24rpx; color: #6b7280; line-height: 1.6; }

	.app-grid { margin-top: 16rpx; display: flex; flex-wrap: wrap; }
	.app-cell { width: 50%; box-sizing: border-box; padding: 8rpx 6rpx; font-size: 24rpx; color: #303133; line-height: 1.5; }
	.app-cell .k, .app-block .k { color: #909399; margin-right: 8rpx; }
	.app-block { margin-top: 12rpx; font-size: 24rpx; color: #303133; line-height: 1.7; }
	.award-thumbs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 8rpx; }
	.award-thumb { width: 140rpx; height: 140rpx; border-radius: 10rpx; background: #f2f4f8; }
	.app-msg { margin-top: 12rpx; font-size: 24rpx; color: #6b7280; background: #f5f7fa; border-radius: 10rpx; padding: 12rpx 16rpx; line-height: 1.6; }
	.app-time { margin-top: 12rpx; font-size: 22rpx; color: #c0c4cc; }
	.app-ops { margin-top: 16rpx; display: flex; align-items: center; flex-wrap: wrap; gap: 14rpx; }
	.trial-tip { margin-right: auto; font-size: 22rpx; color: #e11d48; font-weight: 600; }

	.pay-tip { font-size: 22rpx; margin: 12rpx 0; line-height: 1.5; }
	.pay-list { margin-top: 8rpx; }
	.pay-row { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f0f2f5; }
	.pay-row:last-child { border-bottom: none; }
	.pay-info { flex: 1; min-width: 0; }
	.pay-name { font-size: 26rpx; font-weight: 600; color: #303133; }
	.pay-sub { display: block; font-size: 22rpx; margin-top: 4rpx; }

	.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-end; z-index: 100; }
	.sheet { width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 32rpx 32rpx 48rpx; box-sizing: border-box; }
	.sheet-title { font-size: 32rpx; font-weight: 700; color: #1f2d52; }
	.sheet-sub { font-size: 24rpx; margin: 10rpx 0 20rpx; line-height: 1.5; }
	.search-line { display: flex; gap: 16rpx; align-items: center; }
	.sheet-input { flex: 1; height: 76rpx; background: #f2f4f8; border-radius: 12rpx; padding: 0 20rpx; font-size: 26rpx; }
	.sheet-hint { text-align: center; padding: 24rpx 0; font-size: 24rpx; }
	.teacher-list { max-height: 340rpx; margin-top: 16rpx; border: 1rpx solid #eef0f4; border-radius: 12rpx; }
	.teacher-item { display: flex; flex-direction: column; padding: 16rpx 20rpx; border-bottom: 1rpx solid #f5f6f8; }
	.teacher-item:last-child { border-bottom: none; }
	.teacher-item.active { background: #eef2ff; }
	.t-name { font-size: 26rpx; font-weight: 600; color: #303133; }
	.t-sub { font-size: 22rpx; margin-top: 4rpx; }
	.amount-line { display: flex; align-items: center; gap: 16rpx; margin-top: 20rpx; }
	.amount-line .k { font-size: 26rpx; color: #4b5563; flex-shrink: 0; }
	.sheet-ops { display: flex; gap: 20rpx; margin-top: 32rpx; }
	.sheet-ops .btn { flex: 1; text-align: center; }
</style>
