package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知 — 家长端接收作业逾期等提醒
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 接收人用户 ID（家长） */
    private Long userId;
    /** 类型：homework_overdue */
    private String type;
    private String title;
    private String content;
    /** 关联业务 ID（作业 ID） */
    private Long bizId;
    /** 0 未读 1 已读 */
    private Integer isRead;
    private LocalDateTime createTime;
}
