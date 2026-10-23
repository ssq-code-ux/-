package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.TutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 教师端简历 + 接单须知 + 邀请码接口 —
 * 编辑个人信息（简历模板）、查看接单须知/收款码/管理端联系方式、获取自己的邀请码。
 * 角色校验在 Service 层 assertTeacher()。
 * 网关路由：/api/tutor-profile/** → user-service。
 */
@RestController
@RequestMapping("/api/tutor-profile")
@RequiredArgsConstructor
public class TutorProfileController {

    private final TutorProfileService tutorProfileService;

    /** 我的简历（一人一份） */
    @GetMapping("/me")
    public R<Map<String, Object>> myProfile() {
        return R.ok(tutorProfileService.myProfile());
    }

    /** 保存简历（upsert；字段见教师端"编辑个人信息"表单） */
    @PostMapping("/me")
    public R<Void> saveProfile(@RequestBody Map<String, Object> body) {
        tutorProfileService.saveProfile(body);
        return R.ok(null);
    }

    /** 接单须知（文字+图片）+ 管理端联系方式 + 收款码（教师缴费前看） */
    @GetMapping("/notice")
    public R<Map<String, Object>> notice() {
        return R.ok(tutorProfileService.notice());
    }

    /** 需求 5：我的邀请码 + 已邀请人数 + 邀请名单 */
    @GetMapping("/invite")
    public R<Map<String, Object>> myInviteInfo() {
        return R.ok(tutorProfileService.myInviteInfo());
    }
}
