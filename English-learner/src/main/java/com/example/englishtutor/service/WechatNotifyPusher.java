package com.example.englishtutor.service;

import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.util.WechatPushOperator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 站内通知的微信旁路推送（user 服务内共用）。
 *
 * 与 homework 服务 NotifyService / WeeklyReportPushService 同一套行为约定：
 * 站内通知必写（调用方自己负责），这里只负责"能推就推"的微信增强——
 * 令牌为空、订阅偏好关闭、网络异常一律只记日志，绝不影响主业务流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatNotifyPusher {

    private final UserMapper userMapper;
    private final WechatPushOperator wechatPush;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 给指定用户推一条微信消息（该用户配置过 push_token 才会真的发出）。
     *
     * @param type    订阅偏好里的通知类型 key（如 tutor_apply_result），偏好明确关了就不推
     * @param title   推送标题
     * @param content 推送正文（与站内通知内容保持一致即可）
     */
    public void push(Long userId, String type, String title, String content) {
        try {
            User u = userMapper.selectById(userId);
            if (u == null || u.getPushToken() == null || u.getPushToken().isBlank()) {
                return; // 没配置推送令牌：只走站内铃铛，静默跳过
            }
            if (prefOff(u, type)) {
                return; // 用户退订了这一类：只走站内铃铛
            }
            wechatPush.send(u.getPushToken(), title, content);
        } catch (Exception e) {
            log.warn("[微信推送] 推送失败（站内通知已由调用方写入）：userId={} — {}", userId, e.getMessage());
        }
    }

    /** 偏好 JSON 里该类型是否被明确关了；没存过/解析失败都视为订阅（宁可多推不可漏推） */
    private boolean prefOff(User u, String type) {
        String json = u.getNotifyPrefs();
        if (json == null || json.isBlank()) return false;
        try {
            Map<String, Boolean> prefs = objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
            return Boolean.FALSE.equals(prefs.get(type));
        } catch (Exception e) {
            log.warn("[微信推送] 订阅偏好解析失败，按全订处理：{}", json);
            return false;
        }
    }
}
