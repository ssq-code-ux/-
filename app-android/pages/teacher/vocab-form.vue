<template>
	<view class="page">
		<view class="card">
			<view class="fi">
				<text class="fl req">单词</text>
				<input class="input" v-model="form.word" placeholder="如：happy" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl">读音/音标</text>
				<input class="input" v-model="form.phonetic" placeholder="如 /ˈhæpi/" maxlength="100" />
			</view>
			<view class="fi">
				<text class="fl req">意思</text>
				<input class="input" v-model="form.translation" placeholder="如：开心的，快乐的" maxlength="200" />
			</view>
			<view class="fi">
				<text class="fl">词性</text>
				<input class="input" v-model="form.partOfSpeech" placeholder="如：adj. / noun / verb" maxlength="50" />
			</view>
			<view class="fi">
				<text class="fl">所属词库</text>
				<picker mode="selector" :range="banks" :value="bankIndex" @change="onBankChange">
					<view class="input picker-input">{{ form.bank }} ▾</view>
				</picker>
			</view>
			<view class="fi">
				<view class="fl-row">
					<text class="fl fl-inline">延伸词</text>
					<text class="ai-btn" :class="{ 'ai-disabled': aiLoading }" @click="aiExpand">✨ {{ aiLoading ? 'AI 生成中…' : 'AI 一键生成延伸词' }}</text>
				</view>
				<view class="tag-input-row">
					<input class="input tag-input" v-model="tagInput" placeholder="输入后点添加" maxlength="100" @confirm="addTag" />
					<view class="tag-add-btn" @click="addTag">添加</view>
				</view>
				<view v-if="tags.length" class="tags">
					<text v-for="(t, i) in tags" :key="i" class="tag">{{ t }}<text class="tag-x" @click="tags.splice(i, 1)"> ✕</text></text>
				</view>
				<text class="muted tag-tip">延伸词会以逗号拼接存入词库，学生端可用于联想记忆</text>
			</view>
		</view>

		<view class="bottom-bar">
			<view class="btn btn-plain" @click="cancel">取消</view>
			<view class="btn btn-primary" @click="submit">{{ submitting ? '提交中…' : (form.id ? '保存' : '添加') }}</view>
		</view>
	</view>
</template>

<script>
	import { addVocab, updateVocab, aiExpandVocabulary, vocabBanks } from '../../api/index.js';
	import { guardRole } from '../../utils/role.js';

	export default {
		data() {
			return {
				form: { id: null, word: '', phonetic: '', translation: '', partOfSpeech: '', bank: '学生词库' },
				banks: ['学生词库', '四级词库', '六级词库'], bankIndex: 0,
				tags: [], tagInput: '', submitting: false, aiLoading: false
			};
		},
		onLoad(opt) {
			if (!guardRole('teacher')) return;
			if (opt && opt.data) {
				uni.setNavigationBarTitle({ title: '编辑词汇' });
				try {
					const v = JSON.parse(decodeURIComponent(opt.data));
					this.form.id = v.id;
					this.form.word = v.word || '';
					this.form.phonetic = v.phonetic || '';
					this.form.translation = v.translation || '';
					this.form.partOfSpeech = v.partOfSpeech || '';
					this.form.bank = v.bank || '学生词库';
					this.tags = v.extensions ? String(v.extensions).split(',').map((x) => x.trim()).filter(Boolean) : [];
				} catch (e) { /* 忽略解析失败 */ }
			} else {
				uni.setNavigationBarTitle({ title: '添加词汇' });
			}
			this.loadBanks();
		},
		methods: {
			async loadBanks() {
				try {
					const list = await vocabBanks();
					if (Array.isArray(list) && list.length) this.banks = list;
				} catch (e) { /* 默认三个词库 */ }
				const i = this.banks.indexOf(this.form.bank);
				this.bankIndex = i >= 0 ? i : 0;
				if (i < 0 && this.banks.length) this.form.bank = this.banks[0];
			},
			onBankChange(e) {
				this.bankIndex = Number(e.detail.value) || 0;
				this.form.bank = this.banks[this.bankIndex] || '学生词库';
			},
			addTag() {
				const v = String(this.tagInput || '').trim();
				if (!v) return;
				if (this.tags.indexOf(v) < 0) this.tags.push(v);
				this.tagInput = '';
			},
			/* AI 扩词：生成延伸词并入标签（单词:释义 格式，与 H5 存库一致），空的意思/词性顺带回填 */
			async aiExpand() {
				const word = String(this.form.word || '').trim();
				if (!word) { uni.showToast({ title: '请先填写单词', icon: 'none' }); return; }
				if (this.aiLoading) return;
				this.aiLoading = true;
				try {
					const r = await aiExpandVocabulary(word);
					if (!r) return;
					if (!String(this.form.translation || '').trim() && r.translation) this.form.translation = r.translation;
					if (!String(this.form.partOfSpeech || '').trim() && r.partOfSpeech) this.form.partOfSpeech = r.partOfSpeech;
					const exts = (r.extensions || []).filter((e) => e && e.word && e.selected !== false);
					let added = 0;
					exts.forEach((e) => {
						const tag = e.translation ? String(e.word).trim() + ':' + String(e.translation).trim() : String(e.word).trim();
						if (this.tags.indexOf(tag) < 0) { this.tags.push(tag); added++; }
					});
					uni.showToast({ title: added ? 'AI 已生成 ' + added + ' 个延伸词' : '延伸词已存在，无需新增', icon: 'none' });
				} catch (e) { /* 已提示 */ } finally { this.aiLoading = false; }
			},
			cancel() { uni.navigateBack(); },
			async submit() {
				if (this.submitting) return;
				const word = String(this.form.word || '').trim();
				const translation = String(this.form.translation || '').trim();
				if (!word) { uni.showToast({ title: '请填写单词', icon: 'none' }); return; }
				if (!translation) { uni.showToast({ title: '请填写意思', icon: 'none' }); return; }
				this.addTag(); // 把输入框里没回车的内容也一并保存
				this.submitting = true;
				const payload = { word, phonetic: this.form.phonetic, translation, partOfSpeech: this.form.partOfSpeech, extensions: this.tags.join(','), bank: this.form.bank || '学生词库' };
				try {
					if (this.form.id) await updateVocab(this.form.id, payload);
					else await addVocab(payload);
					// 标记词汇列表需要刷新：返回列表页时才会重新拉取，平时直接复用缓存
					try { uni.setStorageSync('teacher-vocab-dirty', '1'); } catch (e) { /* 忽略 */ }
					uni.showToast({ title: this.form.id ? '已保存' : '添加成功', icon: 'success' });
					setTimeout(() => uni.navigateBack(), 700);
				} catch (e) { /* 已提示 */ } finally { this.submitting = false; }
			}
		}
	};
</script>

<style scoped>
	.fi { margin-bottom: 24rpx; }
	.fl { display: block; font-size: 26rpx; color: #4b5563; margin-bottom: 12rpx; }
	.fl-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
	.fl-inline { margin-bottom: 0; }
	.ai-btn { font-size: 24rpx; color: #ffffff; background: linear-gradient(135deg, #6366f1, #8b5cf6); border-radius: 999rpx; padding: 8rpx 22rpx; font-weight: 600; }
	.ai-disabled { opacity: 0.6; }
	.fl.req::after { content: ' *'; color: #e11d48; }
	.input { height: 80rpx; line-height: 80rpx; background: #f5f7fa; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #303133; }
	.picker-input { display: flex; align-items: center; }
	.tag-input-row { display: flex; gap: 16rpx; align-items: center; }
	.tag-input { flex: 1; }
	.tag-add-btn { flex-shrink: 0; height: 80rpx; line-height: 80rpx; padding: 0 30rpx; border-radius: 12rpx; background: #e6fffa; color: #0d9488; font-size: 26rpx; font-weight: 600; }
	.tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
	.tag { font-size: 24rpx; color: #0d9488; background: #e6fffa; border-radius: 999rpx; padding: 8rpx 20rpx; }
	.tag-x { color: #e11d48; }
	.tag-tip { display: block; margin-top: 14rpx; font-size: 21rpx; line-height: 1.5; }
	.bottom-bar { display: flex; gap: 20rpx; padding: 10rpx 24rpx 40rpx; }
	.bottom-bar .btn { flex: 1; text-align: center; }
</style>
