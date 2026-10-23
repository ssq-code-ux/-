package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;
    /** 账号状态：1正常 0封禁（封禁后禁止登录，管理端专用） */
    private Integer status;
    private Long teacherId;
    /** 学生的家长用户 ID（家长端绑定用，教师/家长账号为 null） */
    private Long parentId;
    private String phone;
    /**
     * 微信推送令牌（PushPlus token / Server酱 SendKey），为空表示未开启推送。
     * @JsonIgnore：令牌等同于“往该用户微信发消息的权限”，绝不能随
     * /api/students、/api/parent/children 等返回 User 的接口泄露给其他人，
     * 本人查看走 /api/me/push-token（只返回打码后的片段）。
     */
    @JsonIgnore
    private String pushToken;
    /**
     * 微信推送订阅偏好，JSON 如 {"homework_overdue":false}；
     * null = 全部订阅（存量用户行为不变）。只控制微信推不推，站内铃铛不受影响。
     */
    @JsonIgnore
    private String notifyPrefs;
    /**
     * 本人邀请码（全表唯一，uk_invite_code）。
     * 教师把它发给同行，对方注册时填写即可建立邀请关系；
     * 它不是登录凭据，泄露也不会导致越权，因此不加 @JsonIgnore（教师端与管理端都需要读）。
     */
    private String inviteCode;
    /** 邀请人用户ID（通过别人的邀请码注册时写入，用于统计“该老师邀请了几位老师”） */
    private Long inviterId;
    private LocalDateTime createTime;
}
