package com.example.englishtutor.service;

import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.util.WechatPushOperator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 每周学习周报推送 — 周报预生成完成后把摘要推给家长
 * <p>
 * 与作业通知（homework 服务的 NotifyService）同一套行为约定：
 * 站内通知必写，微信推送是旁路增强；家长在「订阅偏好」退订 weekly_report 后只进铃铛不推微信。
 * <p>
 * 文案走平实口吻（小结/进步/督促），不出现「AI」字眼，
 * 读起来像系统例行汇报而不是机器生成的推销腔。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportPushService {

    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final WechatPushOperator wechatPush;
    private final ObjectMapper objectMapper;
    private final ParentService parentService;

    /** 周报生成后调用：给绑定的家长写站内通知并尝试微信推送（没绑定家长就跳过） */
    public void pushToParent(Long studentId) {
        try {
            User student = userMapper.selectById(studentId);
            if (student == null || student.getParentId() == null) return;
            Map<String, Object> r = parentService.weeklyReportData(studentId);
            String name = notBlank(student.getRealName()) ? student.getRealName() : student.getUsername();
            String content = buildContent(name, r);
            notifyUser(student.getParentId(), "weekly_report", "孩子的本周学习周报", content, studentId);
        } catch (Exception e) {
            // 推送是旁路增强，失败只记日志，不影响预生成主流程
            log.warn("[周报推送] 推送失败 studentId={}：{}", studentId, e.getMessage());
        }
    }

    /** 摘要文案：只挑家长最关心的几项，一句话读完的长度 */
    private String buildContent(String name, Map<String, Object> r) {
        StringBuilder sb = new StringBuilder(name).append("本周学习小结：");
        double avg = toDouble(r.get("avgScore"));
        double avgLast = toDouble(r.get("avgScoreLast"));
        if (avg > 0) {
            sb.append("平均分 ").append(trimZero(avg)).append(" 分");
            if (avgLast > 0) {
                double d = Math.round((avg - avgLast) * 10) / 10.0;
                sb.append(d >= 0 ? "，比上周进步 " : "，比上周低了 ").append(Math.abs(d)).append(" 分");
            }
            sb.append("；");
        }
        sb.append("提交作业 ").append(intOf(r.get("submitCount"))).append(" 次")
                .append("，学习活跃 ").append(intOf(r.get("activeDays"))).append("/7 天。");
        long overdue = longOf(r.get("overdueHomeworks"));
        if (overdue > 0) {
            sb.append("本周还有 ").append(overdue).append(" 项作业逾期未交，记得督促补上。");
        }
        sb.append("打开家长端即可查看完整周报。");
        return sb.toString();
    }

    /** 站内通知必写 + 微信推送旁路（退订 weekly_report 只挡微信不挡铃铛），与作业通知行为一致 */
    private void notifyUser(Long userId, String type, String title, String content, Long bizId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setBizId(bizId);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
        try {
            User u = userMapper.selectById(userId);
            if (u != null && !prefOff(u, type)) {
                wechatPush.send(u.getPushToken(), title, content);
            }
        } catch (Exception e) {
            log.warn("[周报推送] 微信推送失败（站内通知已写入）：userId={} — {}", userId, e.getMessage());
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
            log.warn("[周报推送] 订阅偏好解析失败，按全订处理：{}", json);
            return false;
        }
    }

    private double toDouble(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }
    private int intOf(Object o) { return o instanceof Number n ? n.intValue() : 0; }
    private long longOf(Object o) { return o instanceof Number n ? n.longValue() : 0; }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** 86.0 显示成 86、86.5 保留一位 */
    private String trimZero(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 10) / 10.0);
    }
}
