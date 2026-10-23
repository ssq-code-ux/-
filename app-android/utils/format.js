/* 通用格式化与枚举映射：从 H5 管理端 Admin.vue 移植，保证展示口径一致 */

import { SITE_URL } from '../config.js';

/* 时间格式化：兼容毫秒时间戳、纯数字字符串、ISO/'YYYY-MM-DD HH:mm:ss' 字符串 */
export function fmtTime(v) {
	if (!v) return '';
	let d;
	if (typeof v === 'number') {
		d = new Date(v);
	} else if (/^\d+$/.test(String(v))) {
		d = new Date(Number(v));
	} else {
		d = new Date(String(v).replace(/-/g, '/'));
	}
	if (isNaN(d.getTime())) return String(v);
	const p = (n) => String(n).padStart(2, '0');
	return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/* 角色文案 / 标签色 */
export const ROLE_TEXT = { teacher: '老师', student: '学生', parent: '家长', admin: '管理员' };
export const ROLE_TAG = { teacher: 'warning', student: 'success', parent: 'primary', admin: 'danger' };
export function roleText(role) { return ROLE_TEXT[role] || role || '—'; }
export function roleTag(role) { return ROLE_TAG[role] || 'info'; }

/* 投递试课状态：简历通过后 试课中 → 订单完成 / 试课失败待退费 → 已退费 */
export const TRIAL_TEXT = { 0: '试课中', 1: '订单已完成', 2: '试课失败待退费', 3: '已退费' };
export const TRIAL_TAG = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' };

/* 家教单状态：已成交的单优先展示试课阶段，而不是笼统的"已关闭" */
export function jobStatusText(row) {
	if (!row) return '—';
	if (row.status === 1) return '开放中';
	return row.trialStatus != null ? (TRIAL_TEXT[row.trialStatus] || '已关闭') : '已关闭';
}
export function jobStatusTag(row) {
	if (!row) return 'info';
	if (row.status === 1) return 'success';
	return row.trialStatus != null ? (TRIAL_TAG[row.trialStatus] || 'info') : 'info';
}

/* 只取日期（YYYY-MM-DD），家长端需求提交时间用 */
export function fmtDate(v) {
	const s = fmtTime(v);
	return s ? s.slice(0, 10) : '';
}

/* 附件/图片 URL：旧数据存相对路径，OSS 上传后是完整 URL，统一补前缀 */
export function mediaUrl(u) {
	if (!u) return '';
	return /^https?:\/\//.test(u) ? u : SITE_URL + u;
}
export function isImageUrl(u) {
	return /\.(png|jpe?g|gif|webp|bmp)(\?.*)?$/i.test(u || '');
}
/* 逗号拼接的多附件拆成数组 */
export function splitFiles(s) {
	return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : [];
}
