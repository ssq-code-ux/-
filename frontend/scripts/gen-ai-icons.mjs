// 生成 PWA 应用图标：改用 AI 助手同款 logo（src/assets/ai-logo.png）
// 纯 Node 实现（无第三方依赖）：PNG 解码 → 双线性缩放 → 重新编码
// 输出：icon-192.png / icon-512.png / icon-maskable-512.png（maskable 版加渐变底+留安全区）
import { inflateSync, deflateSync } from 'node:zlib';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'src', 'assets', 'ai-logo.png');
const OUT = join(ROOT, 'public', 'icons');
mkdirSync(OUT, { recursive: true });

// ---------- PNG 解码（支持 8bit RGBA，filter 0-4） ----------
function decodePNG(path) {
  const buf = readFileSync(path);
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('不是 PNG 文件');
  let w = 0, h = 0;
  const idat = [];
  let off = 8;
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      w = data.readUInt32BE(0); h = data.readUInt32BE(4);
      if (data[8] !== 8 || data[9] !== 6) throw new Error('仅支持 8bit RGBA PNG');
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') break;
    off += 12 + len;
  }
  const raw = inflateSync(Buffer.concat(idat));
  const stride = w * 4;
  const rgba = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const dst = rgba.subarray(y * stride, (y + 1) * stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= 4 ? dst[x - 4] : 0;                       // left
      const b = y > 0 ? rgba[(y - 1) * stride + x] : 0;         // up
      const c = (y > 0 && x >= 4) ? rgba[(y - 1) * stride + x - 4] : 0; // up-left
      let v = line[x];
      switch (filter) {
        case 1: v += a; break;
        case 2: v += b; break;
        case 3: v += (a + b) >> 1; break;
        case 4: {
          const p = a + b - c;
          const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
          break;
        }
      }
      dst[x] = v & 0xff;
    }
  }
  return { w, h, rgba };
}

// ---------- 双线性缩放 ----------
function resize(src, sw, sh, size) {
  const out = Buffer.alloc(size * size * 4);
  const xr = sw / size, yr = sh / size;
  for (let y = 0; y < size; y++) {
    const sy = Math.min(sh - 1.001, (y + 0.5) * yr - 0.5);
    const y0 = Math.max(0, Math.floor(sy)), y1 = Math.min(sh - 1, y0 + 1);
    const fy = sy - y0;
    for (let x = 0; x < size; x++) {
      const sx = Math.min(sw - 1.001, (x + 0.5) * xr - 0.5);
      const x0 = Math.max(0, Math.floor(sx)), x1 = Math.min(sw - 1, x0 + 1);
      const fx = sx - x0;
      for (let ch = 0; ch < 4; ch++) {
        const i00 = (y0 * sw + x0) * 4 + ch, i10 = (y0 * sw + x1) * 4 + ch;
        const i01 = (y1 * sw + x0) * 4 + ch, i11 = (y1 * sw + x1) * 4 + ch;
        const top = src[i00] + (src[i10] - src[i00]) * fx;
        const bot = src[i01] + (src[i11] - src[i01]) * fx;
        out[(y * size + x) * 4 + ch] = Math.round(top + (bot - top) * fy);
      }
    }
  }
  return out;
}

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(b) {
  let c = 0xffffffff;
  for (let i = 0; i < b.length; i++) c = CRC_TABLE[(c ^ b[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePNG(size, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8bit, RGBA
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0; // filter: none
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- maskable：渐变底 + logo 居中缩到安全区 ----------
function makeMaskable(logo, size) {
  const canvas = Buffer.alloc(size * size * 4);
  // 背景：与 AI 助手一致的蓝紫渐变（#667eea → #764ba2）
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const t = (x + y) / (2 * size);
      const o = (y * size + x) * 4;
      canvas[o] = Math.round(102 + (118 - 102) * t);
      canvas[o + 1] = Math.round(126 + (75 - 126) * t);
      canvas[o + 2] = Math.round(234 + (162 - 234) * t);
      canvas[o + 3] = 255;
    }
  }
  // logo 缩到 62%（圆形裁剪安全区内），alpha 合成到中心
  const inner = Math.round(size * 0.62);
  const small = resize(logo.rgba, logo.w, logo.h, inner);
  const off = Math.floor((size - inner) / 2);
  for (let y = 0; y < inner; y++) {
    for (let x = 0; x < inner; x++) {
      const si = (y * inner + x) * 4;
      const sa = small[si + 3] / 255;
      if (sa === 0) continue;
      const di = ((y + off) * size + (x + off)) * 4;
      for (let ch = 0; ch < 3; ch++) {
        canvas[di + ch] = Math.round(small[si + ch] * sa + canvas[di + ch] * (1 - sa));
      }
      canvas[di + 3] = Math.min(255, Math.round(canvas[di + 3] + sa * (255 - canvas[di + 3])));
    }
  }
  return canvas;
}

// ---------- 执行 ----------
const logo = decodePNG(SRC);
console.log(`源图 ai-logo.png: ${logo.w}x${logo.h}`);

const direct192 = resize(logo.rgba, logo.w, logo.h, 192);
const direct512 = resize(logo.rgba, logo.w, logo.h, 512);
const maskable512 = makeMaskable(logo, 512);

writeFileSync(join(OUT, 'icon-ai-192.png'), encodePNG(192, direct192));
writeFileSync(join(OUT, 'icon-ai-512.png'), encodePNG(512, direct512));
writeFileSync(join(OUT, 'icon-ai-maskable-512.png'), encodePNG(512, maskable512));
console.log('已生成: icon-ai-192.png / icon-ai-512.png / icon-ai-maskable-512.png（AI 助手同款 logo）');
