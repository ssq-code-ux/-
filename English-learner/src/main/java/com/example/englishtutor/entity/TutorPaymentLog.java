package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缴费/退费流水（只增不改）。
 * <p>
 * {@code tutor_job_payment} 存的是“当前状态”：退费后金额被清零、paid_time/refund_time 被覆盖，
 * 想知道“这笔钱什么时候收的、什么时候退的、经手人是谁”就无从查起。
 * 每发生一次缴费或退费就写一条流水，时间永久留痕。
 */
@Data
@TableName("tutor_payment_log")
public class TutorPaymentLog {

    /** 缴费 */
    public static final int TYPE_PAY = 1;
    /** 退费 */
    public static final int TYPE_REFUND = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    /** 单号（冗余） */
    private String jobNo;
    private Long teacherId;
    /** 教师姓名（冗余） */
    private String teacherName;
    /** 1缴费 2退费 */
    private Integer type;
    /** 本次发生金额 */
    private BigDecimal amount;
    private String note;
    private Long operatorId;
    private String operatorName;
    /** 发生时间 */
    private LocalDateTime createTime;
}
