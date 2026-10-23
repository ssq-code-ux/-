package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目订单用例模板 — 每个科目一份，管理端发布家教单时可选用模板再改。
 * 字段与 {@link TutorJob} 一一对应，套用模板即把这些值预填到单子上；
 * uk_tpl_subject_type 保证同一科目同一单类型只有一份模板。
 */
@Data
@TableName("tutor_job_template")
public class TutorJobTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 科目 */
    private String subject;
    /** 模板名称 */
    private String name;
    /** 1家教单 2同行合作单 */
    private Integer jobType;
    private String grade;
    private String province;
    private String city;
    private String address;
    /** 学生性别 */
    private String studentGender;
    /** 老师性别要求（空/不限） */
    private String expectGender;
    /** 上课频次 */
    private String frequency;
    /** 时薪（元/小时） */
    private Integer salary;
    /** 学生情况 */
    private String situation;
    /** 对老师的要求 */
    private String requirement;
    /** 0免费投递 1需缴费解锁 */
    private Integer payToApply;
    /** 1启用 0停用 */
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
