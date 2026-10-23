package com.example.englishtutor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.englishtutor.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppVersionMapper extends BaseMapper<AppVersion> {

    /**
     * 幂等建表兜底（同 SysRefreshTokenMapper 约定）：项目没有 flyway，迁移脚本靠
     * 手工执行；漏跑 sql/21_create_app_version.sql 时由后端启动自建一次。
     * IF NOT EXISTS：手工跑过脚本再执行也没有副作用。
     */
    @Update("CREATE TABLE IF NOT EXISTS `app_version` ("
            + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',"
            + "`platform` VARCHAR(16) NOT NULL DEFAULT 'android' COMMENT '平台 android/ios',"
            + "`version_code` INT NOT NULL COMMENT '版本号（递增，客户端比较用）',"
            + "`version_name` VARCHAR(32) NULL COMMENT '版本名（展示用，如 1.0.1）',"
            + "`wgt_url` VARCHAR(512) NULL COMMENT 'wgt 热更新包下载地址',"
            + "`apk_url` VARCHAR(512) NULL COMMENT '整包 APK 下载地址（可选）',"
            + "`force_update` TINYINT NOT NULL DEFAULT 0 COMMENT '是否强制更新 1是 0否',"
            + "`changelog` VARCHAR(1024) NULL COMMENT '更新说明',"
            + "`create_time` DATETIME NULL COMMENT '发布时间',"
            + "PRIMARY KEY (`id`),"
            + "KEY `idx_av_platform_code` (`platform`, `version_code`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本（wgt 热更新）'")
    void createTableIfAbsent();
}
