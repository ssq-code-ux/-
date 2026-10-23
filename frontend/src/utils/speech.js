/**
 * 单词一键发音 — 基于浏览器 speechSynthesis（Web Speech API）
 *
 * 零成本、无需第三方服务；主流浏览器（Chrome/Edge/Safari）均内置英文语音包。
 * 不支持的环境（极少数）静默降级并提示一次。
 */

let unsupportedWarned = false;

/**
 * 朗读一段英文文本（会打断上一次朗读）
 * @param {string} text 要朗读的文本（单词/短语）
 * @returns {boolean} 是否成功发起朗读
 */
export function speakWord(text) {
  if (!text || typeof window === 'undefined' || !('speechSynthesis' in window)) {
    if (!unsupportedWarned) {
      unsupportedWarned = true;
      console.warn('[speak] 当前浏览器不支持语音合成');
    }
    return false;
  }
  try {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 0.85; // 稍慢，适合学生跟读
    // 优先挑选系统里的英文女声（找不到则用默认语音）
    const voices = window.speechSynthesis.getVoices();
    const enVoice = voices.find(v => v.lang.startsWith('en') && /female|zira|samantha/i.test(v.name))
      || voices.find(v => v.lang.startsWith('en'));
    if (enVoice) utterance.voice = enVoice;
    window.speechSynthesis.speak(utterance);
    return true;
  } catch (e) {
    console.warn('[speak] 朗读失败', e);
    return false;
  }
}

// 部分浏览器 getVoices() 首次返回空，需要触发加载
if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
  window.speechSynthesis.getVoices();
}
