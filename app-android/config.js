/**
 * 站点地址配置 —— 换服务器 / 换域名只改这一个文件，然后在 HBuilderX 重新运行到微信小程序。
 *
 * 微信小程序硬性要求：
 *   1. 所有请求必须 HTTPS（http 一律被拦截）。
 *   2. 域名需 ICP 备案，并在微信公众平台「开发管理 → 开发设置 → 服务器域名」
 *      把 https://ssq.ink 同时加入 request 合法域名 与 uploadFile 合法域名。
 *   3. 开发期可在微信开发者工具「详情 → 本地设置 → 不校验合法域名」临时绕过；
 *      真机预览 / 正式发布前必须配好，否则请求全部失败。
 */

/** 后端站点基址（不带末尾斜杠），API 走 `${SITE_URL}/api/...` */
export const SITE_URL = 'https://ssq.ink';

/** 普通请求超时（毫秒）。批量 AI 解析等慢接口会单独覆盖 */
export const REQUEST_TIMEOUT = 30000;
