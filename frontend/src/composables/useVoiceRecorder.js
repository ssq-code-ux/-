import { onBeforeUnmount, ref } from 'vue';

/**
 * 录音 composable — 教师语音批改用
 *
 * 说明：
 * - 依赖 MediaRecorder + getUserMedia，只在 HTTPS 或 localhost 下可用（浏览器安全限制）
 * - 编码格式按浏览器能力挑选：Chrome/Edge/Android 走 webm/opus，iOS Safari 走 mp4
 * - 到达 maxSeconds 自动停止，避免误操作录出几十兆的文件
 *
 * @param {{ maxSeconds?: number }} options
 */
export function useVoiceRecorder({ maxSeconds = 120 } = {}) {
  const supported = typeof navigator !== 'undefined'
    && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
    && typeof MediaRecorder !== 'undefined';

  const recording = ref(false);
  const seconds = ref(0);
  const blob = ref(null);
  const previewUrl = ref('');   // 本地试听地址（objectURL，用完要释放）
  const error = ref('');

  let recorder = null;
  let stream = null;
  let chunks = [];
  let timer = null;

  /** 挑一个当前浏览器支持的音频格式，都不支持就交给浏览器默认 */
  function pickMimeType() {
    const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
    return candidates.find(t => MediaRecorder.isTypeSupported(t)) || '';
  }

  function clearTimer() {
    if (timer) { clearInterval(timer); timer = null; }
  }

  function releaseStream() {
    if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null; }
  }

  function revokePreview() {
    if (previewUrl.value) { URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; }
  }

  async function start() {
    if (!supported) { error.value = '当前浏览器不支持录音'; return false; }
    if (recording.value) return true;
    error.value = '';
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (e) {
      // 用户拒绝授权 / 非 HTTPS 环境 / 没有麦克风，都会走到这里
      error.value = e && e.name === 'NotAllowedError'
        ? '麦克风权限被拒绝，请在浏览器地址栏允许后重试'
        : '无法开启麦克风（请确认使用 HTTPS 访问且设备有麦克风）';
      return false;
    }
    revokePreview();
    blob.value = null;
    chunks = [];
    seconds.value = 0;

    const mimeType = pickMimeType();
    recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
    recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
    recorder.onstop = () => {
      blob.value = new Blob(chunks, { type: recorder.mimeType || mimeType || 'audio/webm' });
      previewUrl.value = URL.createObjectURL(blob.value);
      releaseStream();
    };
    recorder.start();
    recording.value = true;
    timer = setInterval(() => {
      seconds.value += 1;
      if (seconds.value >= maxSeconds) stop();
    }, 1000);
    return true;
  }

  function stop() {
    clearTimer();
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    recording.value = false;
  }

  /** 丢弃已录制的内容（重录 / 关闭弹窗时调用） */
  function reset() {
    stop();
    revokePreview();
    blob.value = null;
    chunks = [];
    seconds.value = 0;
    error.value = '';
  }

  /** 秒数格式化成 0:08 */
  function formatDuration(s) {
    const v = Number(s) || 0;
    return `${Math.floor(v / 60)}:${String(v % 60).padStart(2, '0')}`;
  }

  onBeforeUnmount(() => {
    clearTimer();
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    releaseStream();
    revokePreview();
  });

  return { supported, recording, seconds, blob, previewUrl, error, start, stop, reset, formatDuration };
}
