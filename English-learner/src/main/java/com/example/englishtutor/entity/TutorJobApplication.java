package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家教投递记录（"投简历"）— 一个单子可多人投递，
 * uk_job_teacher 唯一键在数据库层兜底防止同一老师对同一单重复投递。
 */
@Data
@TableName("tutor_job_application")
public class TutorJobApplication {
    /** 状态常量：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态常量：通过（通过的瞬间所属家教单自动关闭） */
    public static final int STATUS_APPROVED = 1;
    /** 状态常量：未通过 */
    public static final int STATUS_REJECTED = 2;

    /** 试课状态：试课中（简历通过的瞬间进入） */
    public static final int TRIAL_IN_PROGRESS = 0;
    /** 试课状态：试课成功（订单完成） */
    public static final int TRIAL_SUCCESS = 1;
    /** 试课状态：试课失败待退费（等管理端处理） */
    public static final int TRIAL_FAILED = 2;
    /** 试课状态：已退费 */
    public static final int TRIAL_REFUNDED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 家教单ID */
    private Long jobId;
    /** 投递老师ID */
    private Long teacherId;
    /** 老师姓名（冗余，列表直接可看） */
    private String teacherName;
    /** 老师手机号（冗余，审核通过后管理员线下联系用） */
    private String teacherPhone;
    /** 投递附言（自我介绍 / 教学经验） */
    private String message;
    /** 状态：0待处理 1通过 2未通过 */
    private Integer status;
    /** 试课状态：0试课中 1试课成功（订单完成） 2试课失败待退费 3已退费；null=未进入试课阶段 */
    private Integer trialStatus;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
}
