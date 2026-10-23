/**
 * Word 文档工具 — 类型判断 / 文件名解析 / 下载 / 在线预览
 *
 * 在线预览依赖 docx-preview 库（仅支持 .docx 新格式）；
 * .doc 旧格式浏览器无法渲染，只提供下载。
 */
import { renderAsync } from 'docx-preview';

/** 是否为图片（用于区分"缩略图"与"文件条"展示） */
export const isImageUrl = (url) => /\.(png|jpe?g|gif|bmp|webp|svg|ico|heic)(\?.*)?$/i.test(url || '');

/** 是否为文档文件（Word/PDF/TXT/PPT/Excel/CSV 等，均以"文件条"展示，不支持内联缩略图） */
export const isDocFile = (url) => /\.(docx?|pdf|txt|rtf|pptx?|xlsx?|csv|md)(\?.*)?$/i.test(url || '');

/** 是否为 Word 文档（.doc / .docx，忽略 URL 上的查询参数） */
export const isWordDoc = (url) => /\.(docx?)(\?.*)?$/i.test(url || '');

/** 是否为可在线预览的 .docx 新格式 */
export const isDocx = (url) => /\.docx(\?.*)?$/i.test(url || '');

/** 从 URL 提取文件名（OSS 对象名为 UUID+扩展名，优先取不含 UUID 的原始名场景不存在，直接取末段解码） */
export function fileNameFromUrl(url) {
  try {
    const path = url.split('?')[0];
    const seg = path.substring(path.lastIndexOf('/') + 1);
    return decodeURIComponent(seg) || '文件';
  } catch {
    return '文件';
  }
}

/**
 * 下载文件：优先 fetch → Blob 触发浏览器下载（可自定义文件名）；
 * 跨域被拒时降级为新标签页直接打开（OSS 已设置 docx MIME，浏览器会直接下载）
 */
export async function downloadFile(url, name) {
  const filename = name || fileNameFromUrl(url);
  try {
    const resp = await fetch(url);
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    const blob = await resp.blob();
    const objectUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(objectUrl);
  } catch {
    // CORS 未配置或网络问题 → 直接打开原始链接
    window.open(url, '_blank');
  }
}

/**
 * 在线预览 .docx：将文件流用 docx-preview 渲染到指定容器。
 * 优先走后端代理接口拿 Blob（同源无跨域问题），避免直连 OSS 触发 CORS 拦截。
 */
export async function renderDocxBuffer(buffer, container) {
  await renderAsync(buffer, container, null, {
    ignoreWidth: false,
    ignoreHeight: true,
    renderHeaders: false,
    renderFooters: false,
  });
}

/** 通过 URL 拉取并预览（仅在无后端代理可用时作为备选） */
export async function previewDocx(url, container) {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error('HTTP ' + resp.status);
  const buffer = await resp.arrayBuffer();
  await renderDocxBuffer(buffer, container);
}
