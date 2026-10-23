import { SITE_URL } from '../config.js';

const USER_KEY = 'user';

export function getUser() {
	try {
		const raw = uni.getStorageSync(USER_KEY);
		return raw ? JSON.parse(raw) : null;
	} catch (e) {
		return null;
	}
}

export function setUser(user) {
	uni.setStorageSync(USER_KEY, JSON.stringify(user));
}

export function clearUser() {
	uni.removeStorageSync(USER_KEY);
}

/* 小程序没有 atob，自己实现 base64 解码（兼容 JWT 的 base64url 变体） */
function b64decode(input) {
	const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
	const str = String(input).replace(/-/g, '+').replace(/_/g, '/').replace(/=+$/, '');
	let output = '';
	for (let bc = 0, bs = 0, buffer, i = 0; (buffer = str.charAt(i++));) {
		buffer = chars.indexOf(buffer);
		if (~buffer) {
			bs = bc % 4 ? bs * 64 + buffer : buffer;
			if (bc++ % 4) {
				output += String.fromCharCode(255 & (bs >> ((-2 * bc) & 6)));
			}
		}
	}
	try {
		return decodeURIComponent(escape(output));
	} catch (e) {
		return output;
	}
}

/* 前端解码 JWT 的 exp 判断是否过期；解码失败一律视为过期 */
export function isTokenExpired(token) {
	try {
		const payload = JSON.parse(b64decode(token.split('.')[1]));
		return payload.exp * 1000 < Date.now();
	} catch (e) {
		return true;
	}
}

/* 用 refreshToken 换新 Token（单飞：并发只刷新一次） */
let refreshing = null;
export function tryRefreshToken() {
	if (refreshing) return refreshing;
	const user = getUser();
	if (!user || !user.refreshToken) return Promise.resolve(null);
	refreshing = new Promise((resolve) => {
		uni.request({
			url: SITE_URL + '/api/auth/refresh',
			method: 'POST',
			data: { refreshToken: user.refreshToken },
			header: { 'Content-Type': 'application/json' },
			success: (res) => {
				const body = res.data;
				if (body && body.code === 200 && body.data) {
					setUser(Object.assign({}, user, body.data));
					resolve(Object.assign({}, user, body.data));
				} else {
					resolve(null);
				}
			},
			fail: () => resolve(null)
		});
	}).finally(() => { refreshing = null; });
	return refreshing;
}
