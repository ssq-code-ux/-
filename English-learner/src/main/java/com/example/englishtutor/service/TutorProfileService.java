package com.example.englishtutor.service;

import java.util.Map;

/**
 * 教师简历 + 接单配置 —
 * 教师端：维护简历、查看接单须知（须知属于公开信息，登录即可看）；
 * 管理端：维护接单配置（须知文字/图片、联系电话、微信号、收款码）。
 */
public interface TutorProfileService {

    /** 教师端：查看我的简历 */
    Map<String, Object> myProfile();

    /** 教师端：保存简历（一人一份，upsert） */
    void saveProfile(Map<String, Object> body);

    /** 教师端：接单须知（文字+图片）+ 收款码（教师缴费前看）+ 管理端联系方式（沟通用） */
    Map<String, Object> notice();

    /**
     * 教师端：我的邀请信息（需求 5）。
     * 返回 { inviteCode, invitedCount, invitedUsers } —— 邀请码没有就当场补发，
     * 保证存量账号（迁移没跑到的）也能拿到自己的码。
     */
    Map<String, Object> myInviteInfo();

    /** 管理端：读取接单配置 */
    Map<String, Object> adminGetConfig();

    /** 管理端：保存接单配置（须知文字/图片URL数组、电话、微信、收款码URL） */
    void adminSaveConfig(Map<String, Object> body);
}
