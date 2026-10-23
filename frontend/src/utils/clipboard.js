/**
 * 复制到剪贴板 — 兼容非 HTTPS 环境
 *
 * navigator.clipboard 只在安全上下文（HTTPS / localhost）下存在。用 http://IP 访问时
 * （App 外壳、纯 IP 部署都属于这种）它是 undefined，直接调用会抛 TypeError。
 * 所以优先走标准 API，不可用时退到 textarea + execCommand 这条老路。
 *
 * @param {string} text 要复制的文本
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyToClipboard(text) {
  if (!text) return false;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (e) { /* 继续走兼容分支 */ }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (e) {
    return false;
  }
}
