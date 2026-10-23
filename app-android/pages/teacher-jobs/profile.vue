<template>
	<view class="page">
		<view class="card">
			<view class="section">接单信息</view>
			<view class="fi">
				<text class="fl req">接单科目</text>
				<input class="input" v-model="profile.subjects" placeholder="如：初中数学、高中英语" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">可上课时间</text>
				<input class="input" v-model="profile.availableTime" placeholder="如：周一至周五晚上、周末全天" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">可长期授课</text>
				<view class="radio-group">
					<text v-for="o in longTermOpts" :key="o" class="radio" :class="{ on: profile.longTerm === o }" @click="profile.longTerm = o">{{ o }}</text>
				</view>
			</view>
		</view>

		<view class="card">
			<view class="section">个人简历</view>

			<!-- 个人照 -->
			<view class="fi">
				<text class="fl">个人照</text>
				<view class="photo-row">
					<view class="photo-box" @click="profile.photoUrl ? previewOne(mediaUrl(profile.photoUrl)) : choosePhoto()">
						<image v-if="profile.photoUrl" class="photo-img" :src="mediaUrl(profile.photoUrl)" mode="aspectFill" />
						<text v-else class="photo-empty">点击上传</text>
					</view>
					<view class="photo-ops">
						<text class="mini-btn mini-btn-primary" @click="choosePhoto">{{ profile.photoUrl ? '更换照片' : '上传个人照' }}</text>
						<text v-if="profile.photoUrl" class="mini-btn mini-btn-danger" @click="profile.photoUrl = ''">移除</text>
						<text class="photo-tip">投递时将随简历一起展示给管理员和家长</text>
					</view>
				</view>
			</view>

			<!-- 奖项证明 -->
			<view class="fi">
				<text class="fl">奖项证明</text>
				<view class="award-imgs">
					<view v-for="(img, i) in profile.awardImgs" :key="i" class="award-box">
						<image class="award-img" :src="mediaUrl(img)" mode="aspectFill" @click="previewAwards(i)" />
						<text class="award-del" @click="removeAward(i)">✕</text>
					</view>
					<view class="award-add" @click="chooseAward">＋</view>
				</view>
				<text class="award-tip">选填：获奖证书、资格证书等证明图片，投递时随简历一起展示</text>
			</view>

			<view class="fi-row">
				<view class="fi half">
					<text class="fl req">姓名</text>
					<input class="input" v-model="profile.name" placeholder="真实姓名" maxlength="30" />
				</view>
				<view class="fi half">
					<text class="fl">年龄</text>
					<input class="input" v-model="profile.age" type="number" placeholder="如：21" maxlength="3" />
				</view>
			</view>

			<view class="fi">
				<text class="fl">性别</text>
				<view class="radio-group">
					<text class="radio" :class="{ on: profile.gender === '男' }" @click="profile.gender = '男'">男</text>
					<text class="radio" :class="{ on: profile.gender === '女' }" @click="profile.gender = '女'">女</text>
				</view>
			</view>

			<view class="fi">
				<text class="fl">学校</text>
				<input class="input" v-model="profile.school" placeholder="如：郑州大学" maxlength="50" />
			</view>
			<view class="fi">
				<text class="fl">专业</text>
				<input class="input" v-model="profile.major" placeholder="如：数学与应用数学" maxlength="50" />
			</view>
			<view class="fi">
				<text class="fl">大几 / 研几</text>
				<picker mode="selector" :range="gradeYears" :value="gradeYearIdx" @change="onGradeYear">
					<view class="picker-in">{{ profile.gradeYear || '请选择' }} ▾</view>
				</picker>
			</view>

			<view class="fi-row">
				<view class="fi half">
					<text class="fl">教龄</text>
					<input class="input" v-model="profile.teachYears" placeholder="如：2年" maxlength="20" />
				</view>
				<view class="fi half">
					<text class="fl">籍贯</text>
					<input class="input" v-model="profile.hometown" placeholder="如：河南郑州" maxlength="30" />
				</view>
			</view>

			<view class="fi">
				<text class="fl">可教教材版本</text>
				<input class="input" v-model="profile.textbookVer" placeholder="如：人教版、北师大版" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">可辅导年级及科目</text>
				<input class="input" v-model="profile.teachScope" placeholder="如：初中全科、高一高二数学" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">备有辅导教材</text>
				<view class="radio-group">
					<text class="radio" :class="{ on: profile.hasMaterial === '有' }" @click="profile.hasMaterial = '有'">有</text>
					<text class="radio" :class="{ on: profile.hasMaterial === '无' }" @click="profile.hasMaterial = '无'">无</text>
				</view>
			</view>
			<view class="fi">
				<text class="fl">其他优势</text>
				<textarea class="textarea" v-model="profile.otherAdvantage" placeholder="如：有家教经验、普通话二级甲等、耐心负责" maxlength="500" />
			</view>
			<view class="fi">
				<text class="fl">辅导优势</text>
				<textarea class="textarea" v-model="profile.teachAdvantage" placeholder="如：善于引导学生思路、注重错题归纳" maxlength="500" />
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">{{ saving ? '保存中…' : '保存简历' }}</view>
		</view>
	</view>
</template>

<script>
	import { myTutorProfile, saveTutorProfile, upload } from '../../api/index.js';
	import { mediaUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';

	const EMPTY = {
		subjects: '', availableTime: '', longTerm: '',
		name: '', gender: '', age: '', school: '', major: '', gradeYear: '',
		teachYears: '', hometown: '', textbookVer: '', teachScope: '', hasMaterial: '',
		otherAdvantage: '', teachAdvantage: '', photoUrl: '', awardImgs: []
	};

	export default {
		data() {
			return {
				profile: { ...EMPTY, awardImgs: [] },
				longTermOpts: ['可', '不可', '面议'],
				gradeYears: ['', '大一', '大二', '大三', '大四', '研一', '研二', '研三'],
				loading: false,
				saving: false
			};
		},
		computed: {
			gradeYearIdx() {
				const i = this.gradeYears.indexOf(this.profile.gradeYear);
				return i < 0 ? 0 : i;
			}
		},
		onLoad() {
			if (!guardRole('teacher')) return;
			this.load();
		},
		methods: {
			mediaUrl,
			async load() {
				this.loading = true;
				try {
					const data = await myTutorProfile();
					this.profile = Object.assign({}, EMPTY, data || {});
					if (!Array.isArray(this.profile.awardImgs)) {
						this.profile.awardImgs = this.profile.awardImgs ? String(this.profile.awardImgs).split(',').filter(Boolean) : [];
					}
				} catch (e) {
					this.profile = { ...EMPTY, awardImgs: [] };
				} finally {
					this.loading = false;
				}
			},
			onGradeYear(e) { this.profile.gradeYear = this.gradeYears[Number(e.detail.value)]; },
			previewOne(url) { uni.previewImage({ urls: [url] }); },
			previewAwards(idx) { uni.previewImage({ urls: this.profile.awardImgs.map(mediaUrl), current: idx }); },
			removeAward(i) { this.profile.awardImgs.splice(i, 1); },
			choosePhoto() {
				uni.chooseImage({
					count: 1, sizeType: ['compressed'],
					success: async (res) => {
						uni.showLoading({ title: '上传中' });
						try {
							const r = await upload(res.tempFilePaths[0]);
							this.profile.photoUrl = (r && r.fileUrl) || '';
							uni.hideLoading();
							uni.showToast({ title: '个人照已上传', icon: 'success' });
						} catch (e) { uni.hideLoading(); }
					}
				});
			},
			chooseAward() {
				uni.chooseImage({
					count: 9, sizeType: ['compressed'],
					success: async (res) => {
						uni.showLoading({ title: '上传中' });
						try {
							for (const p of res.tempFilePaths) {
								const r = await upload(p);
								if (r && r.fileUrl) this.profile.awardImgs.push(r.fileUrl);
							}
							uni.hideLoading();
							uni.showToast({ title: '奖项证明已上传', icon: 'success' });
						} catch (e) { uni.hideLoading(); }
					}
				});
			},
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.saving) return;
				const name = String(this.profile.name || '').trim();
				const subjects = String(this.profile.subjects || '').trim();
				if (!name || !subjects) { uni.showToast({ title: '姓名和接单科目必填', icon: 'none' }); return; }
				this.saving = true;
				try {
					await saveTutorProfile({
						...this.profile,
						name, subjects,
						age: this.profile.age === '' || this.profile.age == null ? null : Number(this.profile.age)
					});
					uni.showToast({ title: '简历已保存', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally {
					this.saving = false;
				}
			}
		}
	};
</script>

<style scoped>
	.section { font-size: 27rpx; font-weight: 700; color: #0d9488; margin: 4rpx 0 22rpx; padding-left: 16rpx; border-left: 6rpx solid #14b8a6; }
	.fi { margin-bottom: 24rpx; }
	.fi-row { display: flex; gap: 20rpx; }
	.fi.half { flex: 1; min-width: 0; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.picker-in { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.radio-group { display: flex; gap: 16rpx; }
	.radio { flex: 1; text-align: center; height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; font-size: 26rpx; color: #6b7280; border: 2rpx solid transparent; }
	.radio.on { background: #e6fffa; color: #0d9488; border-color: #14b8a6; font-weight: 600; }

	.photo-row { display: flex; gap: 24rpx; align-items: flex-start; }
	.photo-box { width: 180rpx; height: 180rpx; border-radius: 12rpx; background: #f2f4f8; overflow: hidden; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
	.photo-img { width: 180rpx; height: 180rpx; }
	.photo-empty { font-size: 24rpx; color: #a0a4ab; }
	.photo-ops { display: flex; flex-direction: column; gap: 14rpx; align-items: flex-start; }
	.photo-tip { font-size: 22rpx; color: #a0a4ab; line-height: 1.5; }

	.award-imgs { display: flex; flex-wrap: wrap; gap: 16rpx; }
	.award-box { position: relative; width: 150rpx; height: 150rpx; }
	.award-img { width: 150rpx; height: 150rpx; border-radius: 12rpx; background: #f2f4f8; }
	.award-del { position: absolute; top: -12rpx; right: -12rpx; width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #e11d48; color: #ffffff; font-size: 24rpx; }
	.award-add { width: 150rpx; height: 150rpx; border-radius: 12rpx; border: 2rpx dashed #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 56rpx; color: #94a3b8; background: #fafbfc; }
	.award-tip { display: block; margin-top: 14rpx; font-size: 22rpx; color: #a0a4ab; line-height: 1.5; }

	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
