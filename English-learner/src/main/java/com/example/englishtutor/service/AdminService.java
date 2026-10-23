package com.example.englishtutor.service;

import java.util.List;
import java.util.Map;

/**
 * 管理端服务 — 全部接口都要求 role=admin（实现里逐个 assertAdmin）
 * 单体版直接挂在主应用里，与用户/作业/词汇等模块共用一个库
 */
public interface AdminService {

    /** 用户分页列表（可按关键字/角色/状态筛选；附邀请码与已邀请人数） */
    Map<String, Object> users(String keyword, String role, Integer status, long page, long size);

    /** 需求 5：某位用户（老师）邀请来的账号名单，管理端在老师详情里看 */
    List<Map<String, Object>> invitedUsers(Long id);

    /** 重置密码为 123456，返回明文新密码（由管理员线下告知用户） */
    String resetPassword(Long id);

    /** 管理员把某用户密码改成自定义新密码 */
    void updatePassword(Long id, String newPassword);

    /** 删除用户（连带清理绑定关系与登录态；admin 与自己不可删） */
    void deleteUser(Long id);

    /** 封禁(0)/解封(1) */
    void setUserStatus(Long id, Integer status);

    /** 调整角色（teacher/student/parent 之间，admin 角色不可动） */
    void updateRole(Long id, String role);

    /** 解除绑定（type=parent 清 parentId / type=teacher 清 teacherId） */
    void unbind(Long id, String type);

    /** 数据看板：用户总量/角色分布/封禁数/站内通知总量 + 最近 7 天注册趋势 */
    Map<String, Object> dashboard();

    /** 审计日志分页（可按动作筛选） */
    Map<String, Object> auditLogs(String action, long page, long size);
}
