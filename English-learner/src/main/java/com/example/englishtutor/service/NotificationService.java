package com.example.englishtutor.service;

import java.util.List;
import java.util.Map;

/**
 * 站内通知（通用）— 任何登录用户都能查看发给自己的通知
 *
 * 原先通知接口挂在家长端（/api/parent/notifications）且强校验 parent 角色，
 * 导致"作业已批改""新作业布置"这类给学生的通知没有出口；改为按 userId 通用后
 * 三端共用同一套铃铛。
 */
public interface NotificationService {

    /** 我的通知列表（最新在前，最多 50 条） */
    List<Map<String, Object>> myNotifications();

    /** 我的未读通知数（铃铛角标） */
    Long unreadCount();

    /** 标记单条已读（只能标记发给自己的） */
    void markRead(Long notificationId);

    /** 全部标记已读 */
    void markAllRead();
}
