-- ============================================================
-- 微信推送「订阅偏好」迁移脚本（执行库：english_tutor_system）
--
-- 用户可以自己勾选订哪些通知，不想要的不推微信（站内铃铛不受影响）。
-- 存 JSON 是为了以后加新通知类型不用改表结构。
-- NULL = 全部订阅（对存量用户零影响，老行为不变）
-- ============================================================

ALTER TABLE `sys_user`
    ADD COLUMN `notify_prefs` VARCHAR(200) NULL COMMENT '微信推送订阅偏好 JSON，如 {"homework_overdue":false}；NULL=全部订阅' AFTER `push_token`;

-- ⚠️ MySQL 的 ALTER TABLE 不支持 IF NOT EXISTS，重复执行会报
--    Duplicate column name 'notify_prefs'，报这个错说明已加过，直接跳过即可。
