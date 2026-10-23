<template>
	<view class="page">
		<view class="card">
			<view class="stu-line" v-if="studentName">布置给：<text class="stu-name">{{ studentName }}</text></view>

			<view class="fi">
				<text class="fl req">作业标题</text>
				<input class="input" v-model="form.title" placeholder="如：Unit 3 单词抄写" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">作业分类（学科）</text>
				<picker mode="selector" :range="subjectNames" :value="subjectIdx" @change="onSubject">
					<view class="picker-in">{{ form.subject || '英语' }} ▾</view>
				</picker>
			</view>
			<view class="fi">
				<text class="fl">作业内容</text>
				<textarea class="textarea" v-model="form.content" placeholder="作业要求、题目内容等" maxlength="2000" />
			</view>
			<view class="fi">
				<text class="fl">截止时间</text>
				<view class="dt-row">
					<picker mode="date" :value="datePart" @change="onDate">
						<view class="picker-in dt">{{ datePart || '选择日期' }} 📅</view>
					</picker>
					<picker mode="time" :value="timePart" @change="onTime">
						<view class="picker-in dt">{{ timePart }} ⏰</view>
					</picker>
					<text v-if="datePart" class="clear-dt" @click="clearDeadline">清除</text>
				</view>
			</view>

			<view class="fi">
				<text class="fl">附件图片（可选，可多张）</text>
				<view class="imgs">
					<view v-for="(url, i) in attachUrls" :key="i" class="img-box">
						<image class="img" :src="mediaUrl(url)" mode="aspectFill" @click="preview(i)" />
						<text class="img-del" @click="attachUrls.splice(i, 1)">✕</text>
					</view>
					<view class="img-add" @click="chooseImg">＋</view>
				</view>
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">{{ submitting ? '提交中…' : (form.id ? '保存修改' : '布置作业') }}</view>
		</view>
	</view>
</template>

<script>
	import { addHomework, updateHomework, getHomework, upload, subjectList } from '../../api/index.js';
	import { mediaUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				form: { id: null, studentId: null, title: '', content: '', subject: '英语', deadline: '' },
				studentName: '',
				subjects: [],
				datePart: '', timePart: '23:59',
				attachUrls: [],
				submitting: false
			};
		},
		computed: {
			subjectNames() { return this.subjects.map((s) => s.name); },
			subjectIdx() { const i = this.subjectNames.indexOf(this.form.subject); return i < 0 ? 0 : i; }
		},
		onLoad(opt) {
			if (!guardRole('teacher')) return;
			this.studentName = opt && opt.studentName ? decodeURIComponent(opt.studentName) : '';
			this.loadSubjects();
			if (opt && opt.id) {
				uni.setNavigationBarTitle({ title: '编辑作业' });
				this.form.id = opt.id;
				this.prefill(opt.id);
			} else {
				uni.setNavigationBarTitle({ title: '布置作业' });
				this.form.studentId = opt && opt.studentId ? opt.studentId : null;
			}
		},
		methods: {
			mediaUrl,
			async loadSubjects() {
				try { this.subjects = (await subjectList()) || []; } catch (e) { this.subjects = []; }
			},
			onSubject(e) { const i = Number(e.detail.value); this.form.subject = this.subjectNames[i] || '英语'; },
			async prefill(id) {
				try {
					const d = await getHomework(id);
					if (!d) return;
					this.form.studentId = d.studentId;
					this.form.title = d.title || '';
					this.form.content = d.content || '';
					this.form.subject = d.subject || '英语';
					this.form.deadline = d.deadline || '';
					this.attachUrls = d.attachmentUrl ? String(d.attachmentUrl).split(',').filter(Boolean) : [];
					this.parseDeadline(d.deadline);
				} catch (e) { /* 已提示 */ }
			},
			parseDeadline(dl) {
				if (!dl) { this.datePart = ''; this.timePart = '23:59'; return; }
				const parts = String(dl).split(/[T ]/);
				this.datePart = parts[0] || '';
				this.timePart = (parts[1] || '23:59').slice(0, 5);
			},
			onDate(e) { this.datePart = e.detail.value; },
			onTime(e) { this.timePart = e.detail.value; },
			clearDeadline() { this.datePart = ''; this.timePart = '23:59'; },
			buildDeadline() { return this.datePart ? `${this.datePart}T${this.timePart || '23:59'}:00` : ''; },
			preview(i) { uni.previewImage({ urls: this.attachUrls.map(mediaUrl), current: i }); },
			chooseImg() {
				uni.chooseImage({
					count: 9, sizeType: ['compressed'],
					success: async (res) => {
						uni.showLoading({ title: '上传中' });
						try {
							for (const p of res.tempFilePaths) {
								const r = await upload(p);
								if (r && r.fileUrl) this.attachUrls.push(r.fileUrl);
							}
							uni.hideLoading();
						} catch (e) { uni.hideLoading(); }
					}
				});
			},
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				if (!String(this.form.title || '').trim()) { uni.showToast({ title: '请填写作业标题', icon: 'none' }); return; }
				if (!this.form.id && !this.form.studentId) { uni.showToast({ title: '缺少学生信息', icon: 'none' }); return; }
				this.submitting = true;
				const payload = {
					studentId: this.form.studentId,
					title: this.form.title.trim(),
					content: this.form.content,
					subject: this.form.subject || '英语',
					deadline: this.buildDeadline(),
					attachmentUrl: this.attachUrls.join(',')
				};
				try {
					if (this.form.id) await updateHomework(this.form.id, payload);
					else await addHomework(payload);
					uni.showToast({ title: this.form.id ? '已保存' : '布置成功', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally { this.submitting = false; }
			}
		}
	};
</script>

<style scoped>
	.stu-line { font-size: 26rpx; color: #4b5563; margin-bottom: 20rpx; }
	.stu-name { color: #0d9488; font-weight: 700; }
	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 220rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.dt-row { display: flex; gap: 16rpx; align-items: center; }
	.picker-in { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.picker-in.dt { flex: 1; text-align: center; }
	.clear-dt { font-size: 24rpx; color: #e11d48; flex-shrink: 0; }

	.imgs { display: flex; flex-wrap: wrap; gap: 16rpx; }
	.img-box { position: relative; width: 150rpx; height: 150rpx; }
	.img { width: 150rpx; height: 150rpx; border-radius: 12rpx; background: #f2f4f8; }
	.img-del { position: absolute; top: -12rpx; right: -12rpx; width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #e11d48; color: #ffffff; font-size: 24rpx; }
	.img-add { width: 150rpx; height: 150rpx; border-radius: 12rpx; border: 2rpx dashed #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 56rpx; color: #94a3b8; background: #fafbfc; }

	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
