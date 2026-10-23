package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内通知接口（三端通用）— 按当前登录用户过滤，不限角色
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /** 我的通知列表（最新在前，最多 50 条） */
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(service.myNotifications());
    }

    /** 未读通知数（铃铛角标） */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(service.unreadCount());
    }

    /** 标记单条已读 */
    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return R.ok(null);
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        service.markAllRead();
        return R.ok(null);
    }
}
