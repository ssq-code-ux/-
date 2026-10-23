<template>
	<view class="page">
		<view class="card">
			<view class="hw-head">
				<text class="hw-title">{{ hw.title || '作业' }}</text>
				<text class="hw-deadline" v-if="hw.deadline">📅 截止 {{ fmtTime(hw.deadline) }}</text>
			</view>
			<view v-if="hw.content" class="hw-content">{{ hw.content }}</view>
		</view>

		<view class="card">
			<view class="fi">
				<text class="fl">文字作答</text>
				<textarea class="textarea" v-model="textAnswer" placeholder="输入你的答案（选填，也可只上传图片/文件）" maxlength="-1" />
			</view>

			<view class="fi">
				<text class="fl">图片 / 附件</text>
				<view class="up-btns">
					<view class="up-btn" @click="chooseImage">📷 拍照/相册</view>
					<view class="up-btn" @click="chooseFile">📎 选择文件</view>
				</view>
				<!-- #ifdef H5 -->
				<view class="doc-picker-host" :prop="docSignal" :change:prop="docPicker.onSignal"></view>
				<!-- #endif -->
				<text class="muted up-tip">可上传作业照片或文档，支持多次添加</text>

				<view v-if="imageFiles.length" class="thumbs">
					<view v-for="(u, i) in imageFiles" :key="u" class="thumb-box">
						<image class="thumb" :src="mediaUrl(u)" mode="aspectFill" @click="previewImg(u)" />
						<text class="thumb-x" @click="removeFile(u)">✕</text>
					</view>
				</view>
				<view v-for="u in docFiles" :key="u" class="doc-row">
					<text class="doc-name">📄 {{ fileName(u) }}</text>
					<text class="doc-x" @click="removeFile(u)">✕</text>
				</view>
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" :class="{ 'btn-disabled': submitting }" @click="submit">{{ submitting ? '提交中…' : '提交作业' }}</view>
		</view>
	</view>
</template>

<script>
	import { getHomework, submitHomework, upload } from '../../api/index.js';
	import { fmtTime, mediaUrl, isImageUrl } from '../../utils/format.js';
	import { guardRole } from '../../utils/role.js';
	import { getUser } from '../../utils/auth.js';

	export default {
		data() {
			return { id: null, hw: {}, textAnswer: '', fileUrls: [], submitting: false, docSignal: null };
		},
		computed: {
			imageFiles() { return this.fileUrls.filter(isImageUrl); },
			docFiles() { return this.fileUrls.filter((u) => !isImageUrl(u)); }
		},
		onLoad(opt) {
			if (!guardRole('student')) return;
			this.id = opt && opt.id;
			this.load();
		},
		methods: {
			fmtTime, mediaUrl,
			async load() { try { this.hw = (await getHomework(this.id)) || {}; } catch (e) { /* 已提示 */ } },
			chooseImage() {
				uni.chooseImage({ count: 9, sizeType: ['compressed'], success: (res) => this.uploadFiles(res.tempFilePaths || []) });
			},
			chooseFile() {
				// #ifdef MP-WEIXIN
				uni.chooseMessageFile({
					count: 9, type: 'file',
					success: (res) => this.uploadFiles((res.tempFiles || []).map((f) => f.path)),
					fail: (err) => { if (String(err.errMsg || '').indexOf('cancel') === -1) uni.showToast({ title: '选择文档失败', icon: 'none' }); }
				});
				// #endif
				// #ifdef APP-PLUS
				// App 端 WebView 的 <input type=file> 会被系统降级成“拍照/相册”，根本选不到文档；
				// 必须用 Native.js 调 Android 系统文件选择器（ACTION_GET_CONTENT）
				this.pickDocNative();
				// #endif
				// #ifdef H5
				const u = getUser();
				this.docSignal = { trigger: Date.now(), token: (u && u.token) || '' };
				// #endif
			},
			// #ifdef APP-PLUS
			pickDocNative() {
				if (typeof plus === 'undefined' || !plus.android) {
					uni.showToast({ title: '当前环境不支持选择文档', icon: 'none' });
					return;
				}
				try {
					const Intent = plus.android.importClass('android.content.Intent');
					const Activity = plus.android.importClass('android.app.Activity');
					const main = plus.android.runtimeMainActivity();
					const intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType('*/*');
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					const REQ = 90211;
					const self = this;
					main.onActivityResult = function (requestCode, resultCode, data) {
						if (requestCode !== REQ) return;
						main.onActivityResult = null; // 用完即卸，避免影响其它原生回调
						if (resultCode !== Activity.RESULT_OK || !data) return;
						try {
							const uri = data.getData();
							if (uri) self.handleDocUri(uri, main);
						} catch (e) {
							uni.showToast({ title: '读取文档失败', icon: 'none' });
						}
					};
					main.startActivityForResult(intent, REQ);
				} catch (e) {
					uni.showToast({ title: '打开文件选择器失败', icon: 'none' });
				}
			},
			// content:// URI 不能直接喂给 uni.uploadFile，必须先落到本地真实文件。
			// 优先用 resolveNativeUri（HBuilderX 3.9.12+ 能直接解析出真实路径）；解析不到
			// （老基座 / SAF 云端文档）就用 ContentResolver 把文件流复制到 App 私有缓存目录再上传。
			handleDocUri(uri, main) {
				let filePath = '';
				try {
					if (typeof plus.android.resolveNativeUri === 'function') filePath = plus.android.resolveNativeUri(uri);
				} catch (e) { filePath = ''; }
				// resolveNativeUri 可能返回 file:// 前缀，统一成绝对路径再判断
				if (filePath && filePath.indexOf('file://') === 0) filePath = filePath.substring(7);
				const usable = filePath && filePath.charAt(0) === '/'
					&& filePath.indexOf('content://') !== 0 && filePath.indexOf('/document/') === -1;
				if (usable) { this.uploadFiles([filePath]); return; }
				const res = this.copyUriToCache(uri, main);
				if (res && res.path) { this.uploadFiles([res.path]); return; }
				// 复制失败：把真实原因弹出来便于定位（临时诊断，定位后可改回普通提示）
				uni.showModal({
					title: '读取文档失败',
					content: 'uri=' + uri + '\nresolved=' + (filePath || '空') + '\nerr=' + ((res && res.err) || '未知'),
					showCancel: false
				});
			},
			// 把 content:// 流复制到 App 私有缓存目录，返回 { path, err }。
			// 走 ParcelFileDescriptor→FileInputStream.getChannel→FileChannel.transferFrom：
			// 避开 Channels.newChannel 在 Native.js 里的重载歧义，也不需分配 byte[]。
			copyUriToCache(uri, main) {
				uni.showLoading({ title: '读取中', mask: true });
				let pfd = null, fis = null, fos = null, srcCh = null, outCh = null;
				try {
					// 先导入要 new 的类；系统返回的对象（resolver/cacheDir/pfd/channel/cursor）一律用 plus.android.invoke 调方法，
					// 否则会报 "xxx is not a function"（Native.js 未导入其类时不暴露实例方法）。
					const File = plus.android.importClass('java.io.File');
					const FileInputStream = plus.android.importClass('java.io.FileInputStream');
					const FileOutputStream = plus.android.importClass('java.io.FileOutputStream');
					const resolver = main.getContentResolver();
					// 文件名必须 ASCII：SAF 原始名常含中文/空格，会损坏 uni.uploadFile 组的 multipart 头，
					// 后端便收不到 file 分片（报 Required part 'file' is not present）。仅保留纯 ASCII 扩展名。
					const name = this.safeDocName(this.queryDisplayName(uri, resolver));
					// 落到 _doc/（uni 原生可读可写区，与图片临时文件同域），再用相对路径 _doc/name 交给 uni.uploadFile，
					// 和“能正常上传的图片”走完全相同的路径形态；_doc 转换失败则兜底用缓存目录绝对路径。
					let out = null, relPath = '';
					try {
						let docDir = plus.io.convertLocalFileSystemURL('_doc/');
						if (docDir.charAt(docDir.length - 1) !== '/') docDir += '/';
						out = new File(docDir, name);
						relPath = '_doc/' + name;
					} catch (e) {
						out = new File(plus.android.invoke(main.getCacheDir(), 'getAbsolutePath'), name);
					}
					pfd = plus.android.invoke(resolver, 'openFileDescriptor', uri, 'r');
					if (!pfd) return { path: '', err: 'openFileDescriptor 返回空' };
					fis = new FileInputStream(plus.android.invoke(pfd, 'getFileDescriptor'));
					fos = new FileOutputStream(out);
					srcCh = fis.getChannel();
					outCh = fos.getChannel();
					const size = plus.android.invoke(srcCh, 'size');
					let pos = 0;
					for (;;) {
						const n = plus.android.invoke(outCh, 'transferFrom', srcCh, pos, 1 << 20);
						if (!(n > 0)) break;
						pos += n;
						if (size > 0 && pos >= size) break;
					}
					plus.android.invoke(fos, 'flush');
					return { path: relPath || plus.android.invoke(out, 'getAbsolutePath'), err: '' };
				} catch (e) {
					return { path: '', err: (e && (e.message || e.toString())) || String(e) };
				} finally {
					try { if (outCh) plus.android.invoke(outCh, 'close'); } catch (e) { /* ignore */ }
					try { if (srcCh) plus.android.invoke(srcCh, 'close'); } catch (e) { /* ignore */ }
					try { if (fos) fos.close(); } catch (e) { /* ignore */ }
					try { if (fis) fis.close(); } catch (e) { /* ignore */ }
					try { if (pfd) plus.android.invoke(pfd, 'close'); } catch (e) { /* ignore */ }
					uni.hideLoading();
				}
			},
			// 查 SAF 文档的显示名（含扩展名，后端据此判类型）；查不到返回空串。
			queryDisplayName(uri, resolver) {
				let cursor = null;
				try {
					cursor = plus.android.invoke(resolver, 'query', uri, null, null, null, null);
					if (!cursor) return '';
					plus.android.invoke(cursor, 'moveToFirst');
					const idx = plus.android.invoke(cursor, 'getColumnIndex', '_display_name'); // OpenableColumns.DISPLAY_NAME
					return idx >= 0 ? (plus.android.invoke(cursor, 'getString', idx) || '') : '';
				} catch (e) {
					return '';
				} finally {
					try { if (cursor) plus.android.invoke(cursor, 'close'); } catch (e) { /* ignore */ }
				}
			},
			// 生成 ASCII 安全文件名：仅保留纯 ASCII 扩展名（后端据此判类型），基名用时间戳，
			// 避免中文/空格文件名损坏 uni.uploadFile 的 multipart 头导致后端收不到 file 分片。
			safeDocName(displayName) {
				const n = String(displayName || '');
				const dot = n.lastIndexOf('.');
				let ext = '';
				if (dot >= 0 && dot < n.length - 1) {
					const e = n.substring(dot + 1).toLowerCase();
					if (/^[a-z0-9]{1,5}$/.test(e)) ext = '.' + e;
				}
				return 'doc_' + Date.now() + ext;
			},
			// #endif
			onDocUploadStart() { uni.showLoading({ title: '上传中', mask: true }); },
			onDocUploaded(url) { if (url) this.fileUrls.push(url); },
			onDocUploadError(e) { uni.showToast({ title: ((e && e.name) || '文档') + ' 上传失败', icon: 'none' }); },
			onDocUploadDone() { uni.hideLoading(); },
			async uploadFiles(paths) {
				if (!paths || !paths.length) return;
				uni.showLoading({ title: '上传中' });
				try {
					for (const p of paths) {
						const r = await upload(p);
						if (r && r.fileUrl) this.fileUrls.push(r.fileUrl);
					}
				} catch (e) { /* 已提示 */ } finally { uni.hideLoading(); }
			},
			removeFile(u) { const i = this.fileUrls.indexOf(u); if (i >= 0) this.fileUrls.splice(i, 1); },
			previewImg(u) { uni.previewImage({ urls: this.imageFiles.map(mediaUrl), current: mediaUrl(u) }); },
			fileName(u) { const s = String(u || ''); return decodeURIComponent(s.split('/').pop() || '文档'); },
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				const text = String(this.textAnswer || '').trim();
				if (!text && !this.fileUrls.length) { uni.showToast({ title: '请填写答案或上传附件', icon: 'none' }); return; }
				this.submitting = true;
				try {
					await submitHomework({ homeworkId: this.id, textAnswer: text, fileUrl: this.fileUrls.join(',') });
					uni.showToast({ title: '提交成功', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 800);
				} catch (e) { /* 已提示 */ } finally { this.submitting = false; }
			}
		}
	};
</script>

<script module="docPicker" lang="renderjs">
	import { SITE_URL } from '../../config.js';

	export default {
		data() { return { input: null, token: '' }; },
		methods: {
			onSignal(val) {
				if (!val || !val.trigger) return;
				this.token = val.token || '';
				this.openPicker();
			},
			openPicker() {
				if (!this.input) {
					const input = document.createElement('input');
					input.type = 'file';
					input.multiple = true;
					input.accept = '.doc,.docx,.pdf,.txt,.xls,.xlsx,.ppt,.pptx';
					input.style.cssText = 'position:fixed;left:-9999px;top:0;width:1px;height:1px;opacity:0;';
					document.body.appendChild(input);
					const self = this;
					input.addEventListener('change', function (e) { self.handleFiles(e.target.files); });
					this.input = input;
				}
				this.input.value = '';
				this.input.click();
			},
			handleFiles(fileList) {
				if (!fileList || !fileList.length) return;
				const owner = this.$ownerInstance;
				if (!owner) return;
				owner.callMethod('onDocUploadStart');
				const arr = Array.prototype.slice.call(fileList);
				const self = this;
				const next = (i) => {
					if (i >= arr.length) { owner.callMethod('onDocUploadDone'); return; }
					self.uploadFile(arr[i]).then((url) => {
						if (url) owner.callMethod('onDocUploaded', url);
						next(i + 1);
					}).catch((e) => {
						owner.callMethod('onDocUploadError', { name: arr[i].name, msg: String((e && e.message) || e) });
						next(i + 1);
					});
				};
				next(0);
			},
			uploadFile(file) {
				const token = this.token;
				return new Promise((resolve, reject) => {
					const formData = new FormData();
					formData.append('file', file);
					const xhr = new XMLHttpRequest();
					xhr.open('POST', SITE_URL + '/api/files/upload');
					if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);
					xhr.timeout = 120000;
					xhr.onload = () => {
						try {
							const body = JSON.parse(xhr.responseText);
							if (body && body.code === 200) resolve(body.data && body.data.fileUrl);
							else reject(new Error((body && body.message) || '上传失败'));
						} catch (e) { reject(new Error('解析返回失败')); }
					};
					xhr.ontimeout = () => reject(new Error('上传超时'));
					xhr.onerror = () => reject(new Error('网络错误'));
					xhr.send(formData);
				});
			}
		}
	};
</script>

<style scoped>
	.hw-head { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; }
	.hw-title { font-size: 32rpx; font-weight: 700; color: #1f2d52; flex: 1; min-width: 0; }
	.hw-deadline { flex-shrink: 0; font-size: 22rpx; color: #909399; }
	.hw-content { margin-top: 12rpx; font-size: 26rpx; color: #606266; line-height: 1.7; }

	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.textarea { width: 100%; box-sizing: border-box; height: 260rpx; background: #f5f7fa; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; color: #303133; }
	.up-btns { display: flex; gap: 16rpx; }
	.up-btn { flex: 1; text-align: center; height: 76rpx; line-height: 76rpx; border-radius: 12rpx; background: #eef2ff; color: #6366f1; font-size: 26rpx; font-weight: 600; }
	.up-tip { display: block; margin-top: 12rpx; font-size: 21rpx; }
	.thumbs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
	.thumb-box { position: relative; width: 160rpx; height: 160rpx; }
	.thumb { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #f2f4f8; }
	.thumb-x { position: absolute; top: -10rpx; right: -10rpx; width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #e11d48; color: #ffffff; font-size: 24rpx; }
	.doc-row { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; padding: 14rpx 18rpx; background: #f8fafc; border-radius: 12rpx; }
	.doc-name { flex: 1; min-width: 0; font-size: 24rpx; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.doc-x { flex-shrink: 0; margin-left: 16rpx; color: #e11d48; font-size: 26rpx; }

	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
	.doc-picker-host { height: 0; overflow: hidden; }
</style>
