package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试课失败退费工单。
 * <p>
 * 为什么单独建表：试课失败后 {@code tutor_job_application} 会被整单清空（业务要求“清空历史投递历史”），
 * 退费状态如果继续挂在投递记录的 trial_status 上就会一起消失，管理端的“试课待退费”永远统计不到。
 * 这张表是投递清空之后退费状态的唯一载体；同时冗余了教师姓名与电话——投递记录没了，
 * 管理员只能靠这里联系到该退费的老师。
 */
@Data
@TableName("tutor_refund_task")
public class TutorRefundTask {

    /** 待退费（试课失败刚登记） */
    public static final int STATUS_PENDING = 0;
    /** 已退费 / 已处理（免费单也走这个终态，否则待退费计数清不掉） */
    public static final int STATUS_DONE = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    /** 单号（冗余） */
    private String jobNo;
    private Long teacherId;
    /** 教师姓名（冗余） */
    private String teacherName;
    /** 教师电话（冗余，投递记录已删除，联系全靠它） */
    private String teacherPhone;
    /** 应退金额；0 表示该单无需缴费，只需把工单标记为已处理 */
    private BigDecimal amount;
    /** 退费原因 */
    private String reason;
    /** 0待退费 1已退费/已处理 */
    private Integer status;
    /** 试课失败登记时间 */
    private LocalDateTime createTime;
    /** 实际办结时间 */
    private LocalDateTime refundTime;
    /** 办理人（管理员） */
    private Long operatorId;
    /** 办理备注 */
    private String note;
}
