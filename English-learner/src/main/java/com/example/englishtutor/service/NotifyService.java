package com.example.englishtutor.service;

import com.example.englishtutor.entity.Homework;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.HomeworkMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.util.WechatPushOperator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 作业通知统一出口 — 站内通知（notification 表）+ 微信推送（用户自己的推送令牌）
 *
 * 两条调用路径都走这里，保证行为一致：
 *   1. MQ 路径：HomeworkNotifyConsumer 消费 homework.notify 消息
 *   2. 降级路径：RabbitMQ 未启用时（producer 为 null）业务代码直接调用
 *
 * 微信推送是旁路增强：失败只记日志，站内通知已经落库，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final HomeworkMapper homeworkMapper;
    private final WechatPushOperator wechatPush;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    /**
     * 处理一条作业通知
     *
     * @param action "assigned"（新作业布置）| "reviewed"（批改完成）
     */
    public void onHomeworkNotify(Long studentId, Long homeworkId, String title, String action) {
        if (studentId == null) return;
        String hwTitle = (title != null && !title.isBlank()) ? title : "未命名作业";

        if ("assigned".equals(action)) {
            notifyUser(studentId, "homework_assigned", "老师布置了新作业",
                    String.format("《%s》已布置，请在截止时间前完成并提交。", hwTitle), homeworkId);
            // 家长同步知道有新作业，才能盯着孩子按时完成；未绑定家长自动跳过
            Long parentId = parentIdOf(studentId);
            if (parentId != null) {
                String deadline = deadlineOf(homeworkId);
                notifyUser(parentId, "homework_assigned", "孩子有新作业",
                        deadline != null
                                ? String.format("《%s》已布置，请在 %s 前完成。", hwTitle, deadline)
                                : String.format("《%s》已布置，请督促孩子按时完成。", hwTitle),
                        homeworkId);
            }
        } else if ("reviewed".equals(action)) {
            notifyUser(studentId, "homework_reviewed", "作业已批改",
                    String.format("《%s》老师已批改完成，快去看看得分和评语吧。", hwTitle), homeworkId);
            // 家长同样关心批改结果，绑定了家长就一并通知（未绑定自动跳过）
            Long parentId = parentIdOf(studentId);
            if (parentId != null) {
                notifyUser(parentId, "homework_reviewed", "孩子的作业已批改",
                        String.format("《%s》老师已批改完成，可在家长端查看得分与评语。", hwTitle), homeworkId);
            }
        } else {
            log.warn("[作业通知] 未知动作 {}，已忽略（作业 {}）", action, homeworkId);
        }
    }

    /**
     * 通用通知：写站内通知 + 尝试微信推送
     *
     * @return 微信推送是否成功（站内通知不受影响，调用方可忽略此返回值）
     */
    public boolean notifyUser(Long userId, String type, String title, String content, Long bizId) {
        if (userId == null) return false;

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
            if (u != null) {
                // 用户明确退订了这类消息就只发站内不推微信（铃铛里还是能看到）
                if (prefOff(u, type)) {
                    log.info("[作业通知] 用户已退订 {}，仅写站内通知：userId={}", type, userId);
                    return false;
                }
                return wechatPush.send(u.getPushToken(), title, content);
            }
        } catch (Exception e) {
            log.warn("[作业通知] 微信推送失败（站内通知已写入）：userId={} — {}", userId, e.getMessage());
        }
        return false;
    }

    /** 偏好 JSON 里该类型是否被显式关了；没存过/解析失败都视为订阅（宁可多推不可漏推） */
    private boolean prefOff(User u, String type) {
        String json = u.getNotifyPrefs();
        if (json == null || json.isBlank()) return false;
        try {
            Map<String, Boolean> prefs = objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
            return Boolean.FALSE.equals(prefs.get(type));
        } catch (Exception e) {
            log.warn("[作业通知] 订阅偏好解析失败，按全订处理：{}", json);
            return false;
        }
    }

    private Long parentIdOf(Long studentId) {
        User student = userMapper.selectById(studentId);
        return student != null ? student.getParentId() : null;
    }

    /** 截止时间给人看的写法；作业查不到或没设截止时间返回 null，家长端换个话术 */
    private String deadlineOf(Long homeworkId) {
        try {
            Homework h = homeworkId == null ? null : homeworkMapper.selectById(homeworkId);
            if (h != null && h.getDeadline() != null) {
                return h.getDeadline().format(DEADLINE_FMT);
            }
        } catch (Exception e) {
            log.warn("[作业通知] 查询截止时间失败（通知照发）：{}", e.getMessage());
        }
        return null;
    }
}
