/**
 * 单词一键发音 —— 小程序无原生 TTS，改用在线词典发音音频流播放（有道优先、百度回退）。
 *
 * 真机注意：音频域名要在微信公众平台「开发管理 → 开发设置 → 服务器域名」
 * 把 dict.youdao.com 与 fanyi.baidu.com 加入 downloadFile 业务域名白名单，
 * 否则真机不发声（开发者工具可勾「不校验合法域名」临时绕过）。
 */

/* 发音源：0=有道（美音） 1=百度翻译 TTS，第一个失败自动切第二个 */
const SOURCES = [
	(w) => 'https://dict.youdao.com/dictvoice?type=2&audio=' + encodeURIComponent(w),
	(w) => 'https://fanyi.baidu.com/gettts?lan=en&text=' + encodeURIComponent(w) + '&spd=3&source=web'
];

let audio = null;
let curText = '';
let curSource = 0;
let warned = false;

function ensureAudio() {
	if (audio) return audio;
	audio = uni.createInnerAudioContext();
	audio.onError(() => {
		// 有道失败 → 自动换百度源重试一次；两源都失败只提示一次，避免刷屏
		if (curSource === 0 && curText) {
			curSource = 1;
			audio.src = SOURCES[1](curText);
			audio.play();
		} else if (!warned) {
			warned = true;
			uni.showToast({ title: '发音播放失败：请检查网络，或确认词典域名已加白名单', icon: 'none' });
		}
	});
	return audio;
}

/**
 * 朗读一个英文单词/短语（会打断上一次播放）
 * @param {string} text 要发音的文本
 * @returns {boolean} 是否成功发起播放
 */
export function speakWord(text) {
	const w = String(text || '').trim();
	if (!w) return false;
	curText = w;
	curSource = 0;
	const a = ensureAudio();
	a.stop();
	a.src = SOURCES[0](w);
	a.play();
	return true;
}

/** 停止当前发音（页面卸载时调用） */
export function stopSpeak() {
	curText = '';
	if (audio) audio.stop();
}
