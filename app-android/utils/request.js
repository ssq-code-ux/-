import { SITE_URL, REQUEST_TIMEOUT } from '../config.js';
import { getUser, clearUser, tryRefreshToken } from './auth.js';

function buildUrl(url) {
	return /^https?:\/\//.test(url) ? url : SITE_URL + url;
}

/* 把对象拼成 query string；空值（undefined/null/''）自动跳过 */
export function buildQuery(params) {
	if (!params) return '';
	const parts = [];
	Object.keys(params).forEach((k) => {
		const v = params[k];
		if (v === undefined || v === null || v === '') return;
		parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
	});
	return parts.length ? '?' + parts.join('&') : '';
}

function httpMsg(status) {
	const map = {
		413: '文件过大，超过服务器限制',
		500: '服务器内部错误',
		502: '网关错误，请稍后重试',
		503: '服务暂时不可用',
		504: '服务器处理超时'
	};
	return map[status] || ('网络异常 [HTTP ' + status + ']');
}

function toast(title) {
	uni.showToast({ title: String(title || '请求失败'), icon: 'none' });
}

function forceLogout() {
	clearUser();
	const pages = getCurrentPages();
	const cur = pages.length ? pages[pages.length - 1].route : '';
	if (cur === 'pages/login/login') return;
	toast('登录已过期，请重新登录');
	setTimeout(() => uni.reLaunch({ url: '/pages/login/login' }), 800);
}

function rawRequest(options) {
	return new Promise((resolve, reject) => {
		uni.request({
			url: buildUrl(options.url),
			method: options.method || 'GET',
			data: options.data,
			timeout: options.timeout || REQUEST_TIMEOUT,
			header: options.header || {},
			dataType: 'json',
			success: resolve,
			fail: reject
		});
	});
}

async function doRequest(options, isRetry) {
	const user = getUser();
	const header = Object.assign({ 'Content-Type': 'application/json' }, options.header || {});
	if (user && user.token) header.Authorization = 'Bearer ' + user.token;

	let res;
	try {
		res = await rawRequest(Object.assign({}, options, { header }));
	} catch (e) {
		toast('网络连接失败，请检查网络');
		return Promise.reject(e);
	}

	const status = res.statusCode;
	const body = res.data;

	// HTTP 401：尝试静默续期后重放一次
	if (status === 401) {
		if (!isRetry) {
			const nu = await tryRefreshToken();
			if (nu) return doRequest(options, true);
		}
		forceLogout();
		return Promise.reject(body || { message: '登录已过期' });
	}

	if (status < 200 || status >= 400) {
		const msg = (body && body.message) || httpMsg(status);
		toast(msg);
		return Promise.reject(body || { message: msg });
	}

	// 网关鉴权失败会返回 HTTP 200 + code=1002，必须在这里拦截
	if (body && body.code === 1002) {
		if (!isRetry) {
			const nu = await tryRefreshToken();
			if (nu) return doRequest(options, true);
		}
		// 本地根本没有登录态（如登录页预取）：静默失败，不弹"过期"也不跳转
		if (!getUser()) return Promise.reject(body);
		forceLogout();
		return Promise.reject(body);
	}

	if (body && body.code !== undefined && body.code !== 200) {
		toast(body.message || '服务器异常');
		return Promise.reject(body);
	}

	return body && body.data !== undefined ? body.data : body;
}

export default function request(options) {
	return doRequest(options, false);
}

export function get(url, params, opt) {
	return request(Object.assign({ url, method: 'GET', data: params || {} }, opt || {}));
}
export function post(url, data, opt) {
	return request(Object.assign({ url, method: 'POST', data }, opt || {}));
}
export function put(url, data, opt) {
	return request(Object.assign({ url, method: 'PUT', data }, opt || {}));
}
export function del(url, params, opt) {
	return request(Object.assign({ url, method: 'DELETE', data: params || {} }, opt || {}));
}
/* 后端用 @RequestParam 接收的 POST：参数要放在 URL query 上而不是 body */
export function postQuery(url, params, opt) {
	return request(Object.assign({ url: url + buildQuery(params), method: 'POST' }, opt || {}));
}
export function putQuery(url, params, opt) {
	return request(Object.assign({ url: url + buildQuery(params), method: 'PUT' }, opt || {}));
}

/* 后端用 @RequestParam 接收、且字段可能较长（如资料正文）：走 x-www-form-urlencoded 表单体，避免 URL 过长 */
export function postForm(url, data, opt) {
	return request(Object.assign({ url, method: 'POST', data, header: { 'Content-Type': 'application/x-www-form-urlencoded' } }, opt || {}));
}
export function putForm(url, data, opt) {
	return request(Object.assign({ url, method: 'PUT', data, header: { 'Content-Type': 'application/x-www-form-urlencoded' } }, opt || {}));
}

/* 通用 multipart 上传：uni.uploadFile 到任意接口，可附带额外表单字段。
   复用与 doRequest 一致的鉴权、401/1002 静默续期重放、错误提示逻辑。 */
export function uploadTo(url, filePath, formData, name = 'file', opt = {}) {
	const options = Object.assign({ timeout: 120000 }, opt || {});
	return new Promise((resolve, reject) => {
		const user = getUser();
		const header = Object.assign({}, options.header || {});
		if (user && user.token) header.Authorization = 'Bearer ' + user.token;
		uni.uploadFile({
			url: buildUrl(url),
			filePath,
			name,
			formData: formData || {},
			header,
			timeout: options.timeout,
			success: async (res) => {
				let body = res.data;
				try { body = JSON.parse(res.data); } catch (e) { /* 保持原样 */ }
				const status = res.statusCode;
				// 鉴权失败：尝试静默续期后重放一次
				if (status === 401 || (body && body.code === 1002)) {
					const nu = await tryRefreshToken();
					if (nu) { uploadTo(url, filePath, formData, name, options).then(resolve, reject); return; }
					if (!getUser()) { reject(body || { message: '登录已过期' }); return; }
					forceLogout();
					reject(body || { message: '登录已过期' });
					return;
				}
				if (status < 200 || status >= 400) {
					const msg = (body && body.message) || httpMsg(status);
					toast(msg);
					reject(body || { message: msg });
					return;
				}
				if (body && body.code !== undefined && body.code !== 200) {
					toast(body.message || '服务器异常');
					reject(body);
					return;
				}
				resolve(body && body.data !== undefined ? body.data : body);
			},
			fail: (err) => {
				toast((options.failMsg) || '上传失败，请重试');
				reject(err);
			}
		});
	});
}

/* 文件上传：先压缩再走 uni.uploadFile，带 Authorization 头 */
export function upload(filePath, name = 'file') {
	return new Promise((resolve, reject) => {
		const user = getUser();
		const header = {};
		if (user && user.token) header.Authorization = 'Bearer ' + user.token;
		uni.uploadFile({
			url: buildUrl('/api/files/upload'),
			filePath,
			name,
			header,
			timeout: 120000,
			success: (res) => {
				let body = res.data;
				try { body = JSON.parse(res.data); } catch (e) { /* 保持原样 */ }
				if (body && body.code === 200) {
					resolve(body.data);
				} else {
					toast((body && body.message) || '上传失败');
					reject(body);
				}
			},
			fail: (err) => {
				toast('上传失败，请重试');
				reject(err);
			}
		});
	});
}
