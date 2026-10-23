package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App 版本 — 支撑 uni-app 客户端的 wgt 资源热更新。
 * <p>
 * 每次发版由管理员在后台新增一行（versionCode 递增）；客户端启动时调
 * GET /api/app/version 拉取本平台 versionCode 最大的一行比对，发现更高就
 * 下载 wgtUrl 静默安装并重启，无需重装 APK，也不用再微信发安装包。
 */
@Data
@TableName("app_version")
public class AppVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 平台：android / ios（当前只发 android） */
    private String platform;
    /** 版本号（整数递增，客户端比较用；对应 manifest.json 的 versionCode） */
    private Integer versionCode;
    /** 版本名（展示用，如 1.0.1；对应 manifest.json 的 versionName） */
    private String versionName;
    /** wgt 资源热更新包下载地址（阿里云 OSS） */
    private String wgtUrl;
    /** 整包 APK 下载地址（可选；原生壳权限/模块变动、wgt 覆盖不了时用） */
    private String apkUrl;
    /** 是否强制更新：1 强制（不可跳过）0 可选 */
    private Integer forceUpdate;
    /** 更新说明 */
    private String changelog;
    private LocalDateTime createTime;
}
