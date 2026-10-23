package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信发送记录 — 用于作业逾期提醒去重（同一作业每天最多提醒一次）
 */
@Data
@TableName("sms_notify_record")
public class SmsNotifyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务类型：homework_overdue */
    private String bizType;
    /** 业务 ID（作业 ID） */
    private Long bizId;
    private String phone;
    /** SENT / FAILED */
    private String status;
    private String message;
    private LocalDateTime createTime;
}
