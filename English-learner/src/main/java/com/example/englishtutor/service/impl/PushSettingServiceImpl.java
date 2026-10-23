package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.PushSettingService;
import com.example.englishtutor.util.WechatPushOperator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSettingServiceImpl implements PushSettingService {

    private final UserMapper userMapper;
    private final WechatPushOperator wechatPush;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 全部可选的订阅项（有序，前端按插入顺序渲染）；新增通知类型时往这里加一项，前后端各处自动跟上 */
    private static final Map<String, String> PREF_ITEMS = new LinkedHashMap<>(Map.of(
            "homework_assigned", "新作业布置",
            "homework_reviewed", "批改结果",
            "homework_deadline", "截止前提醒",
            "homework_overdue", "逾期未交提醒",
            "weekly_report", "每周学习周报"));

    @Override
    public Map<String, Object> myPushSetting() {
        User me = currentUser();
        String token = me.getPushToken();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serviceEnabled", wechatPush.isEnabled());   // 服务端总开关，关了前端提示"管理员已停用"
        m.put("configured", token != null && !token.isBlank());
        m.put("maskedToken", mask(token));
        m.put("channel", channelOf(token));
        // 订阅偏好：库里没存过（null）就按全订返回，存过的按存的来；未知 key 不返回
        m.put("prefs", prefsOf(me));
        return m;
    }

    @Override
    public void savePushToken(String token) {
        String value = (token == null || token.isBlank()) ? null : token.trim();
        if (value != null && value.length() > 64) {
            throw new BizException("推送令牌格式不正确，请从 PushPlus / Server酱 页面复制");
        }
        // 用 UpdateWrapper 而不是 updateById：MyBatis-Plus 默认忽略 null 字段，
        // 置空令牌（关闭推送）必须显式 set null 才能写进库
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, AuthContext.userId())
                .set(User::getPushToken, value));
    }

    @Override
    public boolean testPush() {
        User me = currentUser();
        if (me.getPushToken() == null || me.getPushToken().isBlank()) {
            throw new BizException("请先保存推送令牌");
        }
        // 把第三方的真实返回（如 PushPlus 903 令牌无效）透传给用户：
        // 笼统的「发送失败」会让人对着其实是正确的令牌瞎查半天
        String err = wechatPush.trySend(me.getPushToken(), "推送测试成功 🎉",
                "您已开启英语家教系统的微信推送，之后作业布置、批改结果、截止提醒都会推到这里。");
        if (err != null) {
            throw new BizException("发送失败：" + err);
        }
        return true;
    }

    @Override
    public void saveNotifyPrefs(Map<String, Boolean> prefs) {
        // 只认白名单里的 key，防止前端乱传把 JSON 撑爆；全订阅时存 null，跟「从没设置过」等价
        Map<String, Boolean> clean = new LinkedHashMap<>();
        PREF_ITEMS.forEach((key, label) -> {
            if (prefs != null && prefs.containsKey(key)) clean.put(key, prefs.get(key));
        });
        String json = null;
        if (clean.values().stream().anyMatch(v -> !v)) {
            try {
                json = objectMapper.writeValueAsString(clean);
            } catch (Exception e) {
                throw new BizException("偏好保存失败，请重试");
            }
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, AuthContext.userId())
                .set(User::getNotifyPrefs, json));
    }

    /** 解析用户存的偏好；null/解析失败都按全订处理，不让一条坏数据把推送全屏蔽了 */
    private Map<String, Boolean> prefsOf(User me) {
        Map<String, Boolean> saved = parsePrefs(me.getNotifyPrefs());
        Map<String, Boolean> result = new LinkedHashMap<>();
        PREF_ITEMS.forEach((key, label) -> result.put(key, saved == null || saved.get(key) == null || saved.get(key)));
        return result;
    }

    /** 解析存的偏好 JSON；空/解析失败返回 null（上层按全订处理） */
    private Map<String, Boolean> parsePrefs(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
        } catch (Exception e) {
            log.warn("[推送设置] 订阅偏好 JSON 解析失败，按全订处理：{}", json);
            return null;
        }
    }

    private User currentUser() {
        User me = userMapper.selectById(AuthContext.userId());
        if (me == null) {
            throw new BizException("用户不存在");
        }
        return me;
    }

    /** 只回首尾各 4 位，中间打码：既能让用户确认"存的是我那条"，又不泄露令牌 */
    private static String mask(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String t = token.trim();
        if (t.length() <= 8) {
            return t.charAt(0) + "******";
        }
        return t.substring(0, 4) + "****" + t.substring(t.length() - 4);
    }

    private static String channelOf(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String t = token.trim();
        return (t.startsWith("SCT") || t.startsWith("sctp")) ? "Server酱" : "PushPlus";
    }
}
