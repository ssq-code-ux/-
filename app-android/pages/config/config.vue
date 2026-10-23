<template>
	<view class="page">
		<!-- 接单须知 -->
		<view class="card">
			<view class="card-title">接单须知 <text class="card-sub">展示给教师端</text></view>
			<textarea class="textarea lg" v-model="cfg.noticeText" placeholder="如：投递前请先确认可上课时间、地点；试课成功后请及时反馈…" maxlength="2000" />
			<view class="count">{{ (cfg.noticeText || '').length }}/2000</view>
		</view>

		<!-- 须知配图 -->
		<view class="card">
			<view class="card-title">须知配图 <text class="card-sub">最多 9 张</text></view>
			<view class="img-grid">
				<view v-for="(img, i) in cfg.noticeImgs" :key="i" class="img-box">
					<image class="thumb" :src="img" mode="aspectFill" @click="preview(cfg.noticeImgs, i)" />
					<text class="img-del" @click="removeNotice(i)">✕</text>
				</view>
				<view v-if="cfg.noticeImgs.length < 9" class="img-add" @click="addNotice">
					<text class="plus">＋</text>
					<text class="add-txt">添加</text>
				</view>
			</view>
		</view>

		<!-- 联系方式 -->
		<view class="card">
			<view class="card-title">缴费沟通方式 <text class="card-sub">教师缴费 / 家长通过简历后展示</text></view>
			<view class="fi">
				<text class="fl">电话</text>
				<input class="input" v-model="cfg.phone" placeholder="联系号码" maxlength="20" />
			</view>
			<view class="fi">
				<text class="fl">微信号</text>
				<input class="input" v-model="cfg.wechat" placeholder="联系微信" maxlength="50" />
			</view>
		</view>

		<!-- 收款码 -->
		<view class="card">
			<view class="card-title">收款码 <text class="card-sub">教师缴费时展示</text></view>
			<view class="img-grid" v-if="cfg.payQrUrl">
				<view class="img-box big">
					<image class="thumb" :src="cfg.payQrUrl" mode="aspectFit" @click="preview([cfg.payQrUrl], 0)" />
					<text class="img-del" @click="cfg.payQrUrl = ''">✕</text>
				</view>
			</view>
			<view class="img-grid" v-else>
				<view class="img-add big" @click="addPayQr">
					<text class="plus">＋</text>
					<text class="add-txt">上传收款码</text>
				</view>
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-primary" @click="save">{{ saving ? '保存中…' : '保存配置' }}</view>
		</view>
	</view>
</template>

<script>
	import { adminGetTutorConfig, adminSaveTutorConfig } from '../../api/index.js';
	import { upload } from '../../utils/request.js';

	export default {
		data() {
			return {
				cfg: { noticeText: '', noticeImgs: [], phone: '', wechat: '', payQrUrl: '' },
				saving: false
			};
		},
		onLoad() { this.load(); },
		onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
		methods: {
			async load() {
				try {
					const data = await adminGetTutorConfig();
					this.cfg = {
						noticeText: data.noticeText || '',
						noticeImgs: data.noticeImgs || [],
						phone: data.phone || '',
						wechat: data.wechat || '',
						payQrUrl: data.payQrUrl || ''
					};
				} catch (e) { /* 已提示 */ }
			},
			preview(urls, i) { uni.previewImage({ urls, current: urls[i] }); },
			removeNotice(i) { this.cfg.noticeImgs.splice(i, 1); },
			async uploadOne(path) {
				uni.showLoading({ title: '上传中…', mask: true });
				try {
					const r = await upload(path);
					return r && r.fileUrl ? r.fileUrl : '';
				} catch (e) { return ''; } finally {
					uni.hideLoading();
				}
			},
			addNotice() {
				const left = 9 - this.cfg.noticeImgs.length;
				if (left <= 0) { uni.showToast({ title: '最多 9 张', icon: 'none' }); return; }
				uni.chooseImage({
					count: left,
					sizeType: ['compressed'],
					success: async (res) => {
						for (const path of res.tempFilePaths) {
							const url = await this.uploadOne(path);
							if (url) this.cfg.noticeImgs.push(url);
						}
					}
				});
			},
			addPayQr() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					success: async (res) => {
						const url = await this.uploadOne(res.tempFilePaths[0]);
						if (url) this.cfg.payQrUrl = url;
					}
				});
			},
			async save() {
				if (this.saving) return;
				this.saving = true;
				try {
					await adminSaveTutorConfig({
						noticeText: this.cfg.noticeText,
						noticeImgs: this.cfg.noticeImgs,
						phone: this.cfg.phone,
						wechat: this.cfg.wechat,
						payQrUrl: this.cfg.payQrUrl
					});
					uni.showToast({ title: '已保存', icon: 'success' });
				} catch (e) { /* 已提示 */ } finally {
					this.saving = false;
				}
			}
		}
	};
</script>

<style scoped>
	.textarea { width: 100%; box-sizing: border-box; height: 140rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.textarea.lg { height: 260rpx; }
	.count { text-align: right; font-size: 22rpx; color: #a0a4ab; margin-top: 8rpx; }

	.img-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
	.img-box, .img-add { position: relative; width: 200rpx; height: 200rpx; border-radius: 14rpx; overflow: hidden; }
	.img-box.big, .img-add.big { width: 320rpx; height: 320rpx; }
	.thumb { width: 100%; height: 100%; }
	.img-del { position: absolute; top: 0; right: 0; width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; background: rgba(0,0,0,0.5); color: #fff; font-size: 26rpx; border-bottom-left-radius: 12rpx; }
	.img-add { background: #f5f7fa; border: 1rpx dashed #c9ccd4; display: flex; flex-direction: column; align-items: center; justify-content: center; }
	.plus { font-size: 56rpx; color: #b0b4bd; line-height: 1; }
	.add-txt { font-size: 22rpx; color: #909399; margin-top: 8rpx; }

	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }

	.bottom-bar { padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { text-align: center; }
</style>
