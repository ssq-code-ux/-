package com.example.englishtutor.dto;

import lombok.Data;

/**
 * 发布 App 版本入参（管理员后台提交）。
 */
@Data
public class AppVersionDTO {
    /** 平台，默认 android */
    private String platform;
    /** 版本号，必填且递增 */
    private Integer versionCode;
    /** 版本名，如 1.0.1 */
    private String versionName;
    /** wgt 热更新包地址（先调 /api/files/upload 上传拿到 OSS URL） */
    private String wgtUrl;
    /** 整包 APK 地址（可选） */
    private String apkUrl;
    /** 是否强制更新 1/0 */
    private Integer forceUpdate;
    /** 更新说明 */
    private String changelog;
}
