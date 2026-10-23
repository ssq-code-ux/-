// 生成 PWA 应用图标 PNG（192/512/maskable-512）
// 纯 Node 实现（无第三方依赖）：像素级绘制 + 3x 超采样抗锯齿 + zlib PNG 编码
// 图案：蓝紫渐变圆角背景 + 打开的书 + AI 四角星（与 icon.svg 一致）
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons');
mkdirSync(OUT, { recursive: true });

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
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
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

// ---------- 几何 ----------
function inPoly(px, py, pts) {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > py) !== (yj > py) && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}
function distToSeg(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1, dy = y2 - y1;
  const l2 = dx * dx + dy * dy;
  let t = l2 ? ((px - x1) * dx + (py - y1) * dy) / l2 : 0;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
}
function inRoundRect(x, y, s, r) {
  if (x < 0 || y < 0 || x >= s || y >= s) return false;
  const dx = Math.max(r - x, x - (s - r), 0);
  const dy = Math.max(r - y, y - (s - r), 0);
  return dx * dx + dy * dy <= r * r;
}

// ---------- 绘制 ----------
const SS = 3; // 超采样倍数
function render(size, maskable) {
  const scale = maskable ? 0.72 : 1.0;
  const P = (nx, ny) => [(nx - 0.5) * scale * size + size / 2, (ny - 0.5) * scale * size + size / 2];

  const leftPage = [[0.18, 0.351], [0.461, 0.417], [0.461, 0.715], [0.18, 0.648]].map(([x, y]) => P(x, y));
  const rightPage = [[0.82, 0.351], [0.539, 0.417], [0.539, 0.715], [0.82, 0.648]].map(([x, y]) => P(x, y));
  const lines = [];
  for (const y of [0.485, 0.555, 0.625]) {
    lines.push({ a: P(0.235, y), b: P(0.415, y + 0.043), color: [79, 140, 255] });
    lines.push({ a: P(0.765, y), b: P(0.585, y + 0.043), color: [123, 92, 255] });
  }
  const cx = 0.5, cy = 0.225, r1 = 0.085, r2 = 0.028;
  const starPts = [
    [cx, cy - r1], [cx + r2, cy - r2], [cx + r1, cy], [cx + r2, cy + r2],
    [cx, cy + r1], [cx - r2, cy + r2], [cx - r1, cy], [cx - r2, cy - r2],
  ].map(([x, y]) => P(x, y));

  const corner = size * 0.225;
  const lineW = Math.max(2, size * 0.024 * scale) / 2;
  const buf = Buffer.alloc(size * size * 4);

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let cr = 0, cg = 0, cb = 0, alpha = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = x + (sx + 0.5) / SS, py = y + (sy + 0.5) / SS;
          const inBg = maskable || inRoundRect(px, py, size, corner);
          if (!inBg) continue;
          // 背景渐变
          const t = (px + py) / (2 * size);
          let rr = 79 + (123 - 79) * t, gg = 140 + (92 - 140) * t, bb = 255;
          // 书页
          if (inPoly(px, py, leftPage)) { rr = 255; gg = 255; bb = 255; }
          else if (inPoly(px, py, rightPage)) { rr = 235; gg = 242; bb = 255; }
          // 文字线
          for (const L of lines) {
            if (distToSeg(px, py, L.a[0], L.a[1], L.b[0], L.b[1]) <= lineW) {
              [rr, gg, bb] = L.color;
            }
          }
          // AI 星
          if (inPoly(px, py, starPts)) { rr = 255; gg = 214; bb = 102; }
          cr += rr; cg += gg; cb += bb; alpha += 1;
        }
      }
      const n = SS * SS, o = (y * size + x) * 4;
      if (alpha > 0) {
        buf[o] = cr / alpha; buf[o + 1] = cg / alpha; buf[o + 2] = cb / alpha;
        buf[o + 3] = Math.round((255 * alpha) / n);
      }
    }
  }
  return buf;
}

const tasks = [
  ['icon-192.png', 192, false],
  ['icon-512.png', 512, false],
  ['icon-maskable-512.png', 512, true],
];
for (const [name, size, maskable] of tasks) {
  writeFileSync(join(OUT, name), encodePNG(size, render(size, maskable)));
  console.log('generated:', join(OUT, name));
}
