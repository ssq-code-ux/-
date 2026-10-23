package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public List<Map<String, Object>> myNotifications() {
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .orderByDesc(Notification::getCreateTime)
                .last("LIMIT 50"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("bizId", n.getBizId());
            m.put("isRead", n.getIsRead());
            m.put("createTime", n.getCreateTime());
            result.add(m);
        }
        return result;
    }

    @Override
    public Long unreadCount() {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !Objects.equals(n.getUserId(), AuthContext.userId())) {
            throw new BizException("通知不存在");
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    @Override
    public void markAllRead() {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
