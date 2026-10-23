package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师接单简历 — 教师端"编辑个人信息"维护，投递时自动附给管理端和家长端。
 * 主键就是 teacher_id（一人一份）。
 */
@Data
@TableName("teacher_profile")
public class TeacherProfile {
    @TableId(type = IdType.INPUT)
    private Long teacherId;
    /** 接单科目 */
    private String subjects;
    /** 可上课时间 */
    private String availableTime;
    /** 是否可长期授课：可/不可/面议 */
    private String longTerm;
    /** 姓名 */
    private String name;
    private String gender;
    private Integer age;
    /** 学校 */
    private String school;
    /** 专业 */
    private String major;
    /** 大几（如：大三） */
    private String gradeYear;
    /** 教龄（如：2年） */
    private String teachYears;
    /** 籍贯 */
    private String hometown;
    /** 可教教材版本 */
    private String textbookVer;
    /** 可辅导年级及科目 */
    private String teachScope;
    /** 是否备有辅导教材：有/无 */
    private String hasMaterial;
    /** 其他优势 */
    private String otherAdvantage;
    /** 辅导优势 */
    private String teachAdvantage;
    /** 个人照URL（走现有 /api/files/upload） */
    private String photoUrl;
    /** 奖项证明图片URL（逗号分隔，非必填） */
    private String awardImgs;
    private LocalDateTime updateTime;
}
