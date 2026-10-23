package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端审计日志 — 管理员的每个敏感操作（重置密码/封禁/改角色/解绑）
 * 都必须留痕：谁（operator）、何时（createTime）、动了谁（targetUserId）、
 * 做了什么（action + detail）、从哪来的（ip）。
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {
    /** 动作常量：重置密码 */
    public static final String ACTION_RESET_PASSWORD = "RESET_PASSWORD";
    /** 动作常量：管理员自定义修改用户密码 */
    public static final String ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";
    /** 动作常量：删除用户 */
    public static final String ACTION_DELETE_USER = "DELETE_USER";
    /** 动作常量：封禁/解封 */
    public static final String ACTION_SET_STATUS = "SET_STATUS";
    /** 动作常量：调整角色 */
    public static final String ACTION_UPDATE_ROLE = "UPDATE_ROLE";
    /** 动作常量：解除家长绑定 */
    public static final String ACTION_UNBIND_PARENT = "UNBIND_PARENT";
    /** 动作常量：解除老师绑定 */
    public static final String ACTION_UNBIND_TEACHER = "UNBIND_TEACHER";
    /** 动作常量：发布家教单 */
    public static final String ACTION_PUBLISH_JOB = "PUBLISH_JOB";
    /** 动作常量：编辑家教单 */
    public static final String ACTION_UPDATE_JOB = "UPDATE_JOB";
    /** 动作常量：下架家教单 */
    public static final String ACTION_CLOSE_JOB = "CLOSE_JOB";
    /** 动作常量：重新上架家教单 */
    public static final String ACTION_REOPEN_JOB = "REOPEN_JOB";
    /** 动作常量：审核投递（通过/未通过） */
    public static final String ACTION_REVIEW_APPLICATION = "REVIEW_APPLICATION";
    /** 动作常量：发布 App 新版本（wgt 热更新） */
    public static final String ACTION_PUBLISH_APP_VERSION = "PUBLISH_APP_VERSION";
    /** 动作常量：删除 App 版本记录（wgt/apk） */
    public static final String ACTION_DELETE_APP_VERSION = "DELETE_APP_VERSION";

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作人（管理员）ID */
    private Long operatorId;
    /** 操作人用户名 */
    private String operatorName;
    /** 动作，见上方常量 */
    private String action;
    /** 目标用户ID */
    private Long targetUserId;
    /** 操作详情（如重置了谁的密码、封禁原因） */
    private String detail;
    /** 来源IP */
    private String ip;
    private LocalDateTime createTime;
}
