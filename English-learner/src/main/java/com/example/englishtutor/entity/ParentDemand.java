package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长找家教需求单 — 家长端填写表单提交，管理端审核后生成关联家教单。
 * status：0 待发布（管理端还没处理）→ 1 已发布（已生成家教单，教师端可见）。
 */
@Data
@TableName("parent_demand")
public class ParentDemand {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PUBLISHED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 需求单编号（XQ+6位数字，全表唯一；对外展示用，避免暴露用户ID） */
    private String demandNo;
    private Long parentId;
    /** 称呼：先生/女士 */
    private String contactName;
    /** 联系电话 */
    private String phone;
    /** 微信号（选填） */
    private String wechat;
    /** 学生程度（如：基础较弱，需补基础） */
    private String studentLevel;
    /** 年级（如：初二） */
    private String grade;
    /** 学生性别：男生/女生 */
    private String studentGender;
    /** 想找什么学科的老师（如：数学） */
    private String subject;
    /** 老师性别要求（空=不限） */
    private String expectGender;
    /** 对老师的其他要求 */
    private String otherRequire;
    /** 能提供的时薪（元/小时） */
    private Integer salary;
    /** 状态：0待发布 1已发布 */
    private Integer status;
    /** 家长是否申请删除：0否 1已申请（等管理端确认） */
    private Integer deleteRequested;
    /** 关联生成的家教单ID */
    private Long jobId;
    private LocalDateTime createTime;
    private LocalDateTime publishTime;
}
