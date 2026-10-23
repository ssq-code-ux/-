package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.AuthService;
import com.example.englishtutor.service.PushSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 当前登录用户的个人设置（三端通用）
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final PushSettingService pushSettingService;
    private final AuthService authService;

    /** 我的微信推送设置（令牌打码返回） */
    @GetMapping("/push-token")
    public R<Map<String, Object>> pushSetting() {
        return R.ok(pushSettingService.myPushSetting());
    }

    /** 保存推送令牌；token 传空表示关闭推送 */
    @PutMapping("/push-token")
    public R<Void> savePushToken(@RequestParam(required = false) String token) {
        pushSettingService.savePushToken(token);
        return R.ok(null);
    }

    /** 发一条测试推送，验证令牌是否可用 */
    @PostMapping("/push-test")
    public R<Boolean> testPush() {
        return R.ok(pushSettingService.testPush());
    }
    
    /** 保存微信推送订阅偏好（勾选/取消勾选即时生效，不经过“保存”按钮） */
    @PutMapping("/notify-prefs")
    public R<Void> saveNotifyPrefs(@RequestBody Map<String, Boolean> prefs) {
        pushSettingService.saveNotifyPrefs(prefs);
        return R.ok(null);
    }

    /** 本人修改密码（教师/学生/家长通用，需校验原密码；body: {oldPassword, newPassword}） */
    @PostMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body == null ? null : body.get("oldPassword"),
                body == null ? null : body.get("newPassword"));
        return R.ok(null);
    }
}
