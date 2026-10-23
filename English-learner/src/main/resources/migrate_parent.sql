-- ============================================================
-- 家长端功能迁移脚本（执行库：english_tutor_system）
-- 1. sys_user 增加 parent_id：学生 → 家长 的绑定关系
-- 2. 新建 sms_notify_record：短信/提醒发送记录（逾期提醒按天去重）
-- ============================================================

ALTER TABLE sys_user
    ADD COLUMN parent_id BIGINT NULL COMMENT '学生的家长用户ID（家长端绑定）' AFTER teacher_id;

ALTER TABLE sys_user
    ADD INDEX idx_user_parent_id (parent_id);

CREATE TABLE IF NOT EXISTS sms_notify_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type    VARCHAR(32)  NOT NULL COMMENT '业务类型：homework_overdue',
    biz_id      BIGINT       NOT NULL COMMENT '业务ID：作业ID',
    phone       VARCHAR(20)  NOT NULL COMMENT '接收手机号',
    status      VARCHAR(10)  NOT NULL COMMENT 'SENT / FAILED',
    message     VARCHAR(255) NULL,
    create_time DATETIME     NOT NULL,
    INDEX idx_sms_biz (biz_type, biz_id)
) COMMENT '提醒发送记录（逾期提醒按天去重）';
