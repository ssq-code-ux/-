/**
 * 图片压缩工具 — 在客户端使用 Canvas API 压缩图片后再上传
 *
 * 为什么需要压缩：
 *   1. 手机拍照普遍 4000×3000 像素、5-15MB，直接上传很慢
 *   2. 老师查看原图时也要从 OSS 下载同样的大文件
 *   3. 作业批改场景不需要原图分辨率，1920px 足够看清文字
 *   4. 压缩到 ~500KB 以内，上传和查看都快 10 倍以上
 */

// 最大尺寸限制（像素）：长边不超过此值
const MAX_DIMENSION = 1920;
// JPEG 压缩质量：0-1，0.8 在清晰度和体积间平衡较好
const JPEG_QUALITY = 0.8;
// 如果文件已经小于此值（字节），跳过压缩，避免对小图二次压缩反而变大
const SKIP_COMPRESS_SIZE = 500 * 1024; // 500KB

/**
 * 压缩图片文件
 * @param {File} file - 原始图片文件
 * @returns {Promise<File>} 压缩后的图片文件（如果需要压缩），或原始文件（如果已经很小）
 */
export async function compressImage(file) {
  // 非图片文件或已很小的文件直接返回
  if (!file.type.startsWith('image/')) return file;
  if (file.size <= SKIP_COMPRESS_SIZE) {
    console.log(`图片已小于 500KB (${(file.size / 1024).toFixed(0)}KB)，跳过压缩`);
    return file;
  }

  const originalSizeKB = (file.size / 1024).toFixed(0);

  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);

    img.onload = () => {
      URL.revokeObjectURL(url); // 释放内存

      const { width, height } = calcSize(img.width, img.height);
      // 如果原图已经很小，无需压缩
      if (width >= img.width && height >= img.height) {
        console.log(`原图尺寸 ${img.width}×${img.height} 已小于限制，仅做格式优化`);
      }

      // 离屏 Canvas 渲染 + 导出
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      // 平滑缩放（抗锯齿）
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, width, height);

      // 使用 JPEG 格式导出（对照片压缩效果最好），PNG 截图如包含文字也转 JPEG
      canvas.toBlob(
        (blob) => {
          if (!blob) {
            console.warn('Canvas toBlob 失败，使用原始文件');
            resolve(file);
            return;
          }

          const compressedFile = new File([blob],
            // 改扩展名为 .jpg
            file.name.replace(/\.[^.]+$/, '') + '.jpg',
            { type: 'image/jpeg' }
          );

          const newSizeKB = (compressedFile.size / 1024).toFixed(0);
          const ratio = ((1 - compressedFile.size / file.size) * 100).toFixed(0);
          console.log(
            `图片压缩完成: ${originalSizeKB}KB → ${newSizeKB}KB (减小${ratio}%), ` +
            `${img.width}×${img.height} → ${width}×${height}`
          );

          // 如果压缩后反而更大（极小概率），使用原图
          if (compressedFile.size >= file.size) {
            console.log('压缩后体积未减小，使用原始文件');
            resolve(file);
          } else {
            resolve(compressedFile);
          }
        },
        'image/jpeg',
        JPEG_QUALITY
      );
    };

    img.onerror = () => {
      URL.revokeObjectURL(url);
      console.warn('图片加载失败，使用原始文件');
      resolve(file); // 降级：使用原始文件
    };

    img.src = url;
  });
}

/**
 * 根据最大尺寸限制计算缩放后的宽高
 */
function calcSize(w, h) {
  if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) {
    return { width: w, height: h };
  }
  if (w >= h) {
    return { width: MAX_DIMENSION, height: Math.round(h * MAX_DIMENSION / w) };
  } else {
    return { height: MAX_DIMENSION, width: Math.round(w * MAX_DIMENSION / h) };
  }
}
