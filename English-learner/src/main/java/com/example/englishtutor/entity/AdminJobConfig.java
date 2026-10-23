package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端接单配置 — 单行表（id 恒为 1）：接单须知（文字+图片）、
 * 管理端联系电话/微信号（教师通过简历后展示给家长）、收款码图片。
 */
@Data
@TableName("admin_job_config")
public class AdminJobConfig {
    @TableId(type = IdType.INPUT)
    private Integer id;
    /** 接单须知（文字） */
    private String noticeText;
    /** 接单须知图片URL，逗号分隔（多张） */
    private String noticeImgs;
    /** 管理端联系电话 */
    private String phone;
    /** 管理端微信号 */
    private String wechat;
    /** 收款码图片URL */
    private String payQrUrl;
    private LocalDateTime updateTime;
    /** 最后修改的管理员 */
    private Long updaterId;
}
