-- ============================================================
-- 微信推送 + 语音批改迁移脚本（执行库：english_tutor_system）
-- 1. sys_user 增加 push_token：用户自己的微信推送令牌（PushPlus / Server酱）
-- 2. homework_review 增加 voice_url：教师语音评语的文件地址
-- ⚠️ 已执行过会报 Duplicate column，忽略即可
-- ============================================================

ALTER TABLE sys_user
    ADD COLUMN push_token VARCHAR(64) NULL COMMENT '微信推送令牌（PushPlus token / Server酱 SendKey），为空表示未开启' AFTER phone;

ALTER TABLE homework_review
    ADD COLUMN voice_url VARCHAR(500) NULL COMMENT '语音评语文件地址（OSS URL）' AFTER comment;
