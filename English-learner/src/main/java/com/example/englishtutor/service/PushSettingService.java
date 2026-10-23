package com.example.englishtutor.service;

import java.util.Map;

/**
 * 微信推送设置 — 每个用户自己去 PushPlus / Server酱 微信扫码获取令牌后粘贴进来
 *
 * 令牌保存在 sys_user.push_token，服务端不需要任何密钥配置。
 */
public interface PushSettingService {

    /** 我的推送设置（令牌只回打码片段，不返回原文） */
    Map<String, Object> myPushSetting();

    /** 保存/清空我的推送令牌（传空表示关闭推送） */
    void savePushToken(String token);

    /** 用当前令牌发一条测试推送，返回是否成功 */
    boolean testPush();

    /** 保存微信推送订阅偏好（key=通知类型，value=是否订阅；只控制微信推送，站内铃铛不受影响） */
    void saveNotifyPrefs(Map<String, Boolean> prefs);
}
