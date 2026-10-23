/**
 * 家长端周报分享卡片 —— 纯前端 canvas 绘制
 * 图片在家长自己的设备上生成（谁看图谁出算力），服务器零负担、零存储成本。
 * 画布 750 宽（手机屏约 375pt 的 2 倍），输出 PNG dataURL。
 */

/** 逐字测量换行（中文没有空格，不能按单词折行） */
function wrapText(ctx, text, maxWidth) {
  const lines = [];
  let line = '';
  for (const ch of String(text)) {
    if (ch === '\n') { lines.push(line); line = ''; continue; }
    if (line && ctx.measureText(line + ch).width > maxWidth) {
      lines.push(line);
      line = ch;
    } else {
      line += ch;
    }
  }
  if (line) lines.push(line);
  return lines;
}

/** 去掉轻量 Markdown 标记（**加粗** / `代码`），canvas 只画纯文本 */
function plainText(md) {
  return String(md || '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/`(.*?)`/g, '$1')
    .trim();
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

/**
 * 画一张周报分享卡片
 * @param {Object} opts
 * @param {string} opts.childName   孩子名
 * @param {string} opts.rangeText   日期区间文案，如 "2026-08-22 ~ 2026-08-28"
 * @param {Object} opts.report      周报聚合数据（parentChildWeeklyReport 返回值）
 * @param {string} [opts.summary]   AI 周报点评（可空，空时画默认鼓励语）
 * @returns {string} PNG dataURL
 */
export function drawReportCard({ childName, rangeText, report, summary }) {
  const W = 750;
  const H = 1334;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  const r = report || {};

  // ---- 白底 ----
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, W, H);

  // ---- 头部渐变 + 半透明装饰圆（呼应家长端 AI 卡片的紫蓝主题）----
  const head = ctx.createLinearGradient(0, 0, W, 330);
  head.addColorStop(0, '#667eea');
  head.addColorStop(1, '#764ba2');
  ctx.fillStyle = head;
  ctx.fillRect(0, 0, W, 330);
  ctx.globalAlpha = 0.13;
  ctx.fillStyle = '#ffffff';
  [[665, 55, 95], [745, 185, 55], [70, 305, 70]].forEach(([cx, cy, cr]) => {
    ctx.beginPath();
    ctx.arc(cx, cy, cr, 0, Math.PI * 2);
    ctx.fill();
  });
  ctx.globalAlpha = 1;

  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(255,255,255,.78)';
  ctx.font = '24px sans-serif';
  ctx.fillText(rangeText, W / 2, 86);
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 52px sans-serif';
  ctx.fillText('本周学习周报', W / 2, 158);
  ctx.font = '28px sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,.92)';
  ctx.fillText(`${childName} · 学习数据一览`, W / 2, 216);

  // ---- 指标网格（2 列 × 4 行）----
  const delta = Math.round(((r.avgScore || 0) - (r.avgScoreLast || 0)) * 10) / 10;
  const stats = [
    { icon: '', label: '新收到作业', value: r.newHomeworks ?? 0 },
    { icon: '', label: '提交次数', value: r.submitCount ?? 0 },
    { icon: '', label: '已获批改', value: r.reviewedCount ?? 0 },
    { icon: '', label: '本周平均分', value: r.avgScore ?? 0, delta },
    { icon: '', label: '学习活跃天数', value: `${r.activeDays ?? 0}/7` },
    { icon: '', label: '测验新增错词', value: r.newWordErrors ?? 0 },
    { icon: '', label: '掌握错词', value: r.masteredWords ?? 0 },
    { icon: '', label: '逾期未提交', value: r.overdueHomeworks ?? 0, warn: true },
  ];
  const gx = 40, gy = 372, gw = 320, gh = 130, gap = 30;
  stats.forEach((s, i) => {
    const x = gx + (i % 2) * (gw + gap);
    const y = gy + Math.floor(i / 2) * (gh + gap);
    ctx.fillStyle = '#f5f7fa';
    roundRect(ctx, x, y, gw, gh, 16);
    ctx.fill();
    ctx.textAlign = 'left';
    ctx.font = '34px sans-serif';
    ctx.fillText(s.icon, x + 24, y + 60);
    ctx.font = 'bold 42px sans-serif';
    ctx.fillStyle = s.warn && Number(s.value) > 0 ? '#f56c6c' : '#303133';
    ctx.fillText(String(s.value), x + 84, y + 64);
    if (s.delta) {
      // 涨绿跌红（教育场景：进步=好=绿），紧跟数值右侧
      const vw = ctx.measureText(String(s.value)).width;
      ctx.font = '22px sans-serif';
      ctx.fillStyle = s.delta > 0 ? '#67c23a' : '#f56c6c';
      ctx.fillText(`${s.delta > 0 ? '↑' : '↓'}${Math.abs(s.delta)} vs 上周`, x + 84 + vw + 14, y + 62);
    }
    ctx.font = '24px sans-serif';
    ctx.fillStyle = '#909399';
    ctx.fillText(s.label, x + 24, y + 106);
  });

  // ---- AI 点评（最多 4 行，超出截断加省略号）----
  const ay = 1036;
  ctx.textAlign = 'left';
  ctx.font = 'bold 30px sans-serif';
  ctx.fillStyle = '#303133';
  ctx.fillText('本周点评', 40, ay);

  const txt = plainText(summary) || '本周学习数据已出炉，坚持就会有进步，下周继续加油！';
  ctx.font = '26px sans-serif';
  ctx.fillStyle = '#606266';
  let lines = wrapText(ctx, txt, 670);
  if (lines.length > 4) {
    lines = lines.slice(0, 4);
    let last = lines[3];
    while (last && ctx.measureText(last + '…').width > 670) last = last.slice(0, -1);
    lines[3] = (last || '') + '…';
  }
  lines.forEach((ln, i) => ctx.fillText(ln, 40, ay + 52 + i * 42));

  // ---- 底部品牌位 ----
  ctx.textAlign = 'center';
  ctx.font = '22px sans-serif';
  ctx.fillStyle = '#c0c4cc';
  ctx.fillText('— 智乐学 · 家长端 —', W / 2, H - 58);

  return canvas.toDataURL('image/png');
}
