<script>
    import { SITE_URL } from './config.js';

    export default {
        onLaunch() {
            // #ifdef APP-PLUS
            // App 冷启动检查 wgt 热更新：有新版本就静默下载并安装，用户无需重装 APK、也无需再微信发安装包
            this.checkAppUpdate();
            // #endif
        },
        onShow() {},
        onHide() {},
        methods: {
            /* 拉服务端最新版本与本机 versionCode 比对；非 App 环境（H5/小程序预览）没有 plus，自动跳过 */
            checkAppUpdate() {
                if (typeof plus === 'undefined' || !plus.runtime) return;
                try {
                    plus.runtime.getProperty(plus.runtime.appid, (info) => {
                        const cur = parseInt((info && info.versionCode) || '0', 10) || 0;
                        uni.request({
                            url: SITE_URL + '/api/app/version',
                            method: 'GET',
                            data: { platform: 'android', current: cur },
                            timeout: 10000,
                            success: (res) => {
                                const d = res.data && res.data.data;
                                if (d && d.hasUpdate && d.wgtUrl) this.promptUpdate(d);
                            },
                            fail: () => {}
                        });
                    });
                } catch (e) {}
            },

            /* 弹窗提示更新：forceUpdate 为真时不给「取消」，必须更新才能继续用 */
            promptUpdate(v) {
                const name = v.versionName || ('v' + v.versionCode);
                const log = v.changelog ? ('\n\n更新内容：\n' + v.changelog) : '';
                uni.showModal({
                    title: '发现新版本 ' + name,
                    content: '建议立即更新以获取最新功能。' + log,
                    showCancel: !v.forceUpdate,
                    confirmText: '立即更新',
                    cancelText: '下次再说',
                    success: (r) => { if (r.confirm) this.downloadWgt(v); }
                });
            },

            /* 下载 wgt 资源包并安装，装完提示重启生效（wgt 只更新前端资源，不动原生壳） */
            downloadWgt(v) {
                uni.showLoading({ title: '正在下载 0%', mask: true });
                const task = uni.downloadFile({
                    url: v.wgtUrl,
                    success: (res) => {
                        if (res.statusCode !== 200) {
                            uni.hideLoading();
                            uni.showToast({ title: '下载失败（' + res.statusCode + '）', icon: 'none' });
                            return;
                        }
                        // force:true 跳过 versionName/versionCode 校验：避免因基座与 wgt 版本名不一致而反复报“version 版本不匹配”。
                        // 前提是 wgt 只改前端资源（同一台机、同一 HBuilderX 打包）；若改了原生权限/模块/图标或换了 HBuilderX 版本，必须重打完整 APK，不能走 wgt。
                        plus.runtime.install(res.tempFilePath, { force: true }, () => {
                            uni.hideLoading();
                            uni.showModal({
                                title: '更新完成',
                                content: '新版本已安装，点击确定立即重启应用。',
                                showCancel: false,
                                confirmText: '重启',
                                success: () => plus.runtime.restart()
                            });
                        }, (err) => {
                            uni.hideLoading();
                            uni.showToast({ title: '安装失败：' + ((err && err.message) || '未知错误'), icon: 'none' });
                        });
                    },
                    fail: () => {
                        uni.hideLoading();
                        uni.showToast({ title: '下载失败，请检查网络', icon: 'none' });
                    }
                });
                if (task && task.onProgressUpdate) {
                    task.onProgressUpdate((p) => {
                        uni.showLoading({ title: '正在下载 ' + p.progress + '%', mask: true });
                    });
                }
            }
        }
    }
</script>

<style>
	page {
		background-color: #f5f7fa;
		color: #303133;
		font-size: 28rpx;
	}

	/* 通用容器与卡片 */
	.page {
		min-height: 100vh;
		box-sizing: border-box;
	}
	.card {
		background: #ffffff;
		border-radius: 20rpx;
		padding: 24rpx;
		margin: 20rpx 24rpx;
		box-shadow: 0 4rpx 20rpx rgba(31, 45, 82, 0.06);
	}
	.card-title {
		font-size: 30rpx;
		font-weight: 600;
		color: #1f2d52;
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}
	.card-sub {
		font-size: 22rpx;
		font-weight: 400;
		color: #909399;
	}

	/* 文本与布局原子类 */
	.muted { color: #909399; font-size: 24rpx; }
	.title-lg { font-size: 34rpx; font-weight: 700; color: #1f2d52; }
	.row { display: flex; align-items: center; }
	.row-between { display: flex; align-items: center; justify-content: space-between; }
	.gap8 { gap: 8rpx; }
	.gap12 { gap: 12rpx; }
	.mt8 { margin-top: 8rpx; }
	.mt16 { margin-top: 16rpx; }
	.mt24 { margin-top: 24rpx; }

	/* 金额 */
	.amount-ok { color: #16a34a; font-weight: 700; }
	.amount-danger { color: #e11d48; font-weight: 700; }

	/* 渐变头（登录页 / 首页顶部） */
	.grad-header {
		background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
		color: #ffffff;
	}

	/* 主按钮 */
	.btn {
		height: 80rpx;
		line-height: 80rpx;
		text-align: center;
		border-radius: 40rpx;
		font-size: 28rpx;
		font-weight: 600;
	}
	.btn-primary {
		background: linear-gradient(135deg, #667eea 0%, #38bdf8 100%);
		color: #ffffff;
		box-shadow: 0 6rpx 18rpx rgba(102, 126, 234, 0.32);
	}
	.btn-plain { background: #f2f4f8; color: #4b5563; }
	.btn-danger { background: #fde8ec; color: #e11d48; }
	.btn-disabled { opacity: 0.6; }

	/* 卡片内联小按钮 */
	.mini-btn {
		display: inline-block;
		padding: 8rpx 22rpx;
		border-radius: 26rpx;
		font-size: 24rpx;
		background: #f2f4f8;
		color: #4b5563;
		margin-right: 12rpx;
		margin-top: 12rpx;
	}
	.mini-btn-primary { background: rgba(102, 126, 234, 0.12); color: #667eea; }
	.mini-btn-success { background: rgba(22, 163, 74, 0.12); color: #16a34a; }
	.mini-btn-danger { background: rgba(225, 29, 72, 0.12); color: #e11d48; }

	/* 电话链接 */
	.tel-link { color: #667eea; }
</style>
