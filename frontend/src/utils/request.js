import axios from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
});

request.interceptors.request.use((config) => {
  const user = JSON.parse(localStorage.getItem('user') || 'null');
  if (user?.token) {
    config.headers.Authorization = 'Bearer ' + user.token;
  }
  return config;
});

// 用 refreshToken 换新 Token（单飞：并发 401/1002 只刷新一次）
// 导出给路由守卫复用：refresh 每次都会轮换 token，守卫和请求拦截器各刷一次的话，
// 后到的那次拿的是已被吊销的旧 refresh，必然失败并把用户踢回登录页。
let refreshing = null;
export function tryRefreshToken() {
  if (!refreshing) {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (!user?.refreshToken) return Promise.resolve(null);
    refreshing = axios
      .post((import.meta.env.VITE_API_BASE_URL || '') + '/api/auth/refresh', {
        refreshToken: user.refreshToken
      })
      .then(res => {
        if (res.data?.code === 200 && res.data.data) {
          const newUser = { ...user, ...res.data.data };
          localStorage.setItem('user', JSON.stringify(newUser));
          return newUser;
        }
        return null;
      })
      .catch(() => null)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

function forceLogout() {
  localStorage.removeItem('user');
  // 已经在登录页：只清理本地状态，不提示不跳转，避免无限刷新死循环
  if (window.location.pathname === '/login') return;
  ElMessage.error('登录已过期，请重新登录');
  setTimeout(() => { window.location.href = '/login'; }, 1000);
}

// 尝试刷新并重放原请求；返回 null 表示刷新失败
async function refreshAndRetry(config) {
  if (!config || config._retried) return null;
  const newUser = await tryRefreshToken();
  if (!newUser) return null;
  config._retried = true;
  config.headers.Authorization = 'Bearer ' + newUser.token;
  return request(config);
}

request.interceptors.response.use(
  async (response) => {
    // 二进制响应（文件预览/下载）不做 code 解包，直接返回原始响应
    if (response.config.responseType === 'blob') return response;
    // 网关鉴权失败返回 HTTP 200 + code=1002，必须在这里拦截
    if (response.data?.code === 1002) {
      // 本地完全没有登录态（如登录页预取接口）：静默失败，不弹"登录过期"也不跳转
      const user = JSON.parse(localStorage.getItem('user') || 'null');
      if (!user?.token) return Promise.reject(response.data);
      const retry = await refreshAndRetry(response.config);
      if (retry) return retry;
      forceLogout();
      return Promise.reject(response.data);
    }
    if (response.data.code !== 200) {
      ElMessage.error(response.data.message || '服务器异常');
      return Promise.reject(response.data);
    }
    return response.data.data;
  },
  async (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        const retry = await refreshAndRetry(error.config);
        if (retry) return retry;
        forceLogout();
        return Promise.reject(error);
      }
      // 显示具体错误信息：优先用服务端返回的 message，其次显示 HTTP 状态码
      const msg = error.response.data?.message;
      if (msg) {
        ElMessage.error(msg);
      } else {
        // 明确 HTTP 状态码便于诊断
        const statusMap = {
          413: '文件过大，超过服务器限制',
          500: '服务器内部错误，请查看日志',
          502: '网关错误，请稍后重试',
          503: '服务暂时不可用',
          504: '服务器处理超时，文件过大或网络不稳定'
        };
        ElMessage.error(statusMap[status] || ('网络异常 [HTTP ' + status + ']'));
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('上传超时，文件过大或网络速度较慢，请压缩后重试');
    } else {
      ElMessage.error('网络连接失败，请检查后端服务是否启动');
    }
    return Promise.reject(error);
  }
);

export default request;
