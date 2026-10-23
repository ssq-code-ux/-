-- ============================================================
-- 站内通知表（家长端接收作业逾期等提醒）
-- 执行库：english_tutor_system
-- ============================================================
CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '接收人用户ID（家长）',
    type        VARCHAR(32)  NOT NULL COMMENT '类型：homework_overdue',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(255) NULL,
    biz_id      BIGINT       NULL COMMENT '关联业务ID（作业ID）',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME     NOT NULL,
    INDEX idx_notify_user (user_id, is_read),
    INDEX idx_notify_biz (type, biz_id)
) COMMENT '站内通知（家长端提醒）';
