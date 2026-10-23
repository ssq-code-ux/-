package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端接口 — 单体版直接挂在主应用里。
 * 角色校验在 Service 层逐个 assertAdmin()（与 StudentService/ParentService 同一套约定）。
 * 路径统一在 /api/admin/** 下，由 AuthInterceptor 做登录校验。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ==================== 用户管理 ====================

    /** 用户分页列表（keyword 搜账号/姓名/手机号；可按角色、状态筛；附邀请码与已邀请人数） */
    @GetMapping("/users")
    public R<Map<String, Object>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.users(keyword, role, status, page, size));
    }

    /** 需求 5：该用户（老师）邀请来的账号名单，在老师详细信息里展示 */
    @GetMapping("/users/{id}/invites")
    public R<List<Map<String, Object>>> invitedUsers(@PathVariable Long id) {
        return R.ok(adminService.invitedUsers(id));
    }

    /** 重置密码（统一重置为 123456，明文返回给管理员线下告知用户） */
    @PostMapping("/users/{id}/reset-password")
    public R<String> resetPassword(@PathVariable Long id) {
        return R.ok(adminService.resetPassword(id));
    }

    /** 管理员把某用户密码改成自定义新密码（body: {newPassword}） */
    @PostMapping("/users/{id}/password")
    public R<Void> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.updatePassword(id, body == null ? null : body.get("newPassword"));
        return R.ok(null);
    }

    /** 删除用户（连带清理绑定关系与登录态；admin 与自己不可删） */
    @DeleteMapping("/users/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return R.ok(null);
    }

    /** 封禁(0) / 解封(1) */
    @PostMapping("/users/{id}/status")
    public R<Void> setUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.setUserStatus(id, status);
        return R.ok(null);
    }

    /** 调整角色（teacher/student/parent 之间） */
    @PostMapping("/users/{id}/role")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam String role) {
        adminService.updateRole(id, role);
        return R.ok(null);
    }

    /** 解除绑定（type=parent 解除家长绑定 / type=teacher 解除老师绑定） */
    @PostMapping("/users/{id}/unbind")
    public R<Void> unbind(@PathVariable Long id, @RequestParam String type) {
        adminService.unbind(id, type);
        return R.ok(null);
    }

    // ==================== 数据看板 ====================

    /** 全平台统计：用户总量/角色分布/封禁数/站内通知总量 + 最近 7 天注册趋势 */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(adminService.dashboard());
    }

    // ==================== 审计日志 ====================

    /** 审计日志分页（可按 action 筛选：RESET_PASSWORD / SET_STATUS / UPDATE_ROLE / UNBIND_PARENT / UNBIND_TEACHER） */
    @GetMapping("/audit-logs")
    public R<Map<String, Object>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.auditLogs(action, page, size));
    }
}
