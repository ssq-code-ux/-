package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师缴费记录 — 按家教单维度：管理端标记已缴费后该教师解锁该单的投递权；
 * 未接到单时管理端退费并把金额清零。
 */
@Data
@TableName("tutor_job_payment")
public class TutorJobPayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private Long teacherId;
    /** 已缴金额（元）；0 表示未缴费/已退清 */
    private BigDecimal amount;
    /** 最近一次标记缴费时间 */
    private LocalDateTime paidTime;
    /** 退费时间（退费后金额清零） */
    private LocalDateTime refundTime;
    /** 备注（缴费/退费说明） */
    private String note;
    /** 操作管理员ID */
    private Long operatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
