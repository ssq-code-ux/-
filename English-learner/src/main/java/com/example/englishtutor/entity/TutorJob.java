package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家教信息单 — 管理端发布，教师端浏览并投递简历。
 * 状态机：status=1 开放（可投递）→ 某份投递审核通过 → status=0 关闭（不再展示/投递）。
 * jobType 把「家教单」与「同行合作单」分开：两者共用同一套发布/投递/审核流程，
 * 只是管理端分两个入口维护、教师端可分别筛选。
 */
@Data
@TableName("tutor_job")
public class TutorJob {
    /** 状态常量：开放中 */
    public static final int STATUS_OPEN = 1;
    /** 状态常量：已关闭（有人通过或管理员手动下架） */
    public static final int STATUS_CLOSED = 0;

    /** 单子类型：家教单 */
    public static final int TYPE_TUTOR = 1;
    /** 单子类型：同行合作单 */
    public static final int TYPE_PARTNER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 单号（教师端展示的编号，如 647734） */
    private String jobNo;
    /** 单子类型：1家教单 2同行合作单 */
    private Integer jobType;
    /** 科目（如：数学） */
    private String subject;
    private String province;
    private String city;
    /** 详细地址 */
    private String address;
    /** 学生年级（如：初三） */
    private String grade;
    /** 学生性别 */
    private String studentGender;
    /** 老师性别要求（空=不限） */
    private String expectGender;
    /** 上课频次（如：每周1次，每次2小时） */
    private String frequency;
    /** 时薪（元/小时） */
    private Integer salary;
    /** 学生情况（如：补基础，提优） */
    private String situation;
    /** 要求（如：教学经验丰富，善于引导） */
    private String requirement;
    /** 状态：1开放 0关闭 */
    private Integer status;
    /** 发布管理员ID */
    private Long publisherId;
    /** 发布管理员姓名（冗余） */
    private String publisherName;
    /** 关联的家长用户ID（家长端提交需求生成；教师端不返回） */
    private Long parentId;
    /** 来源的家长需求单ID */
    private Long parentDemandId;
    /** 是否需缴费后才能投递：0否 1是（管理端标记教师已缴费后解锁） */
    private Integer payToApply;
    /** 审核通过的老师ID（该单关闭的原因） */
    private Long approvedTeacherId;
    /** 审核通过的投递记录ID */
    private Long approvedApplicationId;
    private LocalDateTime createTime;
    private LocalDateTime closeTime;
}
