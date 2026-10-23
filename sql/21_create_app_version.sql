-- =====================================================================
-- 21_create_app_version.sql
-- App 版本表（uni-app 客户端 wgt 资源热更新）
--
-- 背景：app-android 是 uni-app 云打包的 APK，前端页面资源被打进包里，
--       不发新包用户就一直是旧版。本表存每个平台的最新版本信息，
--       客户端启动调 GET /api/app/version 比对 versionCode，发现更高就
--       下载 wgt_url 静默安装并重启，无需重装 APK、无需再微信发安装包。
--
-- 幂等性：CREATE TABLE IF NOT EXISTS，重复执行不会报错。
-- 兜底：  后端启动会执行同样的建表语句（AppVersionServiceImpl.init），
--         这一步漏跑也不会导致检查更新接口报错，但仍建议与其它迁移一起执行。
-- 执行库：english_tutor_system
-- =====================================================================

CREATE TABLE IF NOT EXISTS `app_version` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `platform`     VARCHAR(16)   NOT NULL DEFAULT 'android' COMMENT '平台 android/ios',
  `version_code` INT           NOT NULL COMMENT '版本号（递增，客户端比较用）',
  `version_name` VARCHAR(32)   NULL COMMENT '版本名（展示用，如 1.0.1）',
  `wgt_url`      VARCHAR(512)  NULL COMMENT 'wgt 热更新包下载地址（阿里云 OSS）',
  `apk_url`      VARCHAR(512)  NULL COMMENT '整包 APK 下载地址（可选，原生壳变动时用）',
  `force_update` TINYINT       NOT NULL DEFAULT 0 COMMENT '是否强制更新 1是 0否',
  `changelog`    VARCHAR(1024) NULL COMMENT '更新说明',
  `create_time`  DATETIME      NULL COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_av_platform_code` (`platform`, `version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本（wgt 热更新）';
