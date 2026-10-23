<template>
	<view class="page">
		<view class="card">
			<view class="card-title">✏️ 编辑学生基本信息<text class="card-sub">{{ studentName }}</text></view>
			<view class="tip">填写或修改该学生的年级、学科与掌握程度，保存后立即生效。</view>

			<view class="field">
				<text class="field-label">年级</text>
				<input class="input" type="text" v-model="form.grade" placeholder="如：初二" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">学科</text>
				<input class="input" type="text" v-model="form.subject" placeholder="如：英语" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">掌握程度</text>
				<picker mode="selector" :range="levels" :value="levelIdx < 0 ? 0 : levelIdx" @change="onLevelPick">
					<view class="input picker-text" :class="{ ph: levelIdx < 0 }">{{ levelIdx >= 0 ? levels[levelIdx] : '请选择掌握程度' }}</view>
				</picker>
			</view>
			<view class="field">
				<text class="field-label">教材</text>
				<input class="input" type="text" v-model="form.textbook" placeholder="教材（选填）" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">学习目标</text>
				<input class="input" type="text" v-model="form.goals" placeholder="学习目标（选填）" placeholder-class="ph" />
			</view>
			<view class="field">
				<text class="field-label">薄弱点</text>
				<textarea class="textarea" v-model="form.weakPoints" placeholder="薄弱点（选填）" placeholder-class="ph" />
			</view>

			<view class="btn btn-primary mt24" :class="{ 'btn-disabled': saving }" hover-class="btn-hover" @click="onSave">
				{{ saving ? '保存中…' : '保存' }}
			</view>
		</view>
	</view>
</template>

<script>
	import { studentProfile, saveProfile } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	const LEVELS = ['初级', '中级', '高级'];

	export default {
		data() {
			return {
				studentId: null,
				studentName: '',
				levels: LEVELS,
				levelIdx: -1,
				form: { grade: '', subject: '', textbook: '', level: '', goals: '', weakPoints: '' },
				saving: false
			};
		},
		onLoad(q) {
			if (!guardRole('teacher')) return;
			this.studentId = q && q.studentId ? Number(q.studentId) : null;
			this.studentName = q && q.studentName ? decodeURIComponent(q.studentName) : '';
			this.loadProfile();
		},
		methods: {
			async loadProfile() {
				if (!this.studentId) return;
				try {
					const p = await studentProfile(this.studentId);
					if (p) {
						this.form.grade = p.grade || '';
						this.form.subject = p.subject || '';
						this.form.textbook = p.textbook || '';
						this.form.level = p.level || '';
						this.form.goals = p.goals || '';
						this.form.weakPoints = p.weakPoints || '';
						this.levelIdx = LEVELS.indexOf(this.form.level);
					}
				} catch (e) { /* 还没有档案，留空即可，保存时后端自动新建 */ }
			},
			onLevelPick(e) {
				this.levelIdx = Number(e.detail.value);
				this.form.level = LEVELS[this.levelIdx];
			},
			async onSave() {
				if (this.saving) return;
				if (!this.studentId) { uni.showToast({ title: '缺少学生ID', icon: 'none' }); return; }
				this.saving = true;
				try {
					await saveProfile({ studentId: this.studentId, grade: this.form.grade, subject: this.form.subject, textbook: this.form.textbook, level: this.form.level, goals: this.form.goals, weakPoints: this.form.weakPoints });
					uni.showToast({ title: '保存成功', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 600);
				} catch (e) { /* 已提示 */ } finally { this.saving = false; }
			}
		}
	};
</script>

<style scoped>
	.tip { font-size: 22rpx; color: #909399; line-height: 1.5; margin-bottom: 20rpx; }
	.field { margin-bottom: 20rpx; }
	.field-label { display: block; font-size: 24rpx; color: #4b5563; margin-bottom: 10rpx; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 27rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; color: #303133; }
	.picker-text { display: block; }
	.ph { color: #b6bcc7; }
	.btn-hover { opacity: 0.85; }
</style>
