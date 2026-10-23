/* 多角色路由：登录按 role 分流到各自首页；各端首页也用它做守卫。 */

import { getUser } from './auth.js';

export const ROLE_HOME = {
	admin: '/pages/home/home',
	teacher: '/pages/teacher-home/teacher-home',
	parent: '/pages/parent-home/parent-home',
	student: '/pages/student-home/student-home'
};

/* 返回该角色的首页路径；未知角色返回空串（由调用方决定提示/拦截） */
export function homePathForRole(role) {
	return ROLE_HOME[role] || '';
}

export const ROLE_END = { teacher: '教师端', student: '学生端', parent: '家长端', admin: '管理端' };
export function roleEnd(role) { return ROLE_END[role] || ''; }

/*
 * 首页守卫：校验本地登录态与角色。
 * - 无登录态 → 回登录页，返回 false
 * - 角色与当前首页不匹配 → 跳到该角色真正的首页，返回 false
 * - 通过 → 返回 user 对象
 */
export function guardRole(expectRole) {
	const user = getUser();
	if (!user || !user.token) {
		uni.reLaunch({ url: '/pages/login/login' });
		return false;
	}
	if (user.role !== expectRole) {
		const hp = homePathForRole(user.role);
		if (hp) uni.reLaunch({ url: hp });
		else uni.reLaunch({ url: '/pages/login/login' });
		return false;
	}
	return user;
}
