-- ============================================================
-- 家长端 AI 内容缓存表（执行库：english_tutor_system）
--
-- 「学习建议」每天预生成一次、「周报总结」每周预生成一次，
-- 定时任务在家长登录之前生成好，家长打开页面直接读缓存，秒开不再等 AI。
--
-- period_key：
--   daily_advice   → 日期，如 2026-08-28
--   weekly_summary → ISO 周，如 2026-W35
-- ============================================================

CREATE TABLE IF NOT EXISTS `ai_content_cache` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id`   BIGINT       NOT NULL COMMENT '学生ID',
    `content_type` VARCHAR(20)  NOT NULL COMMENT 'daily_advice=每日学习建议 weekly_summary=周报总结',
    `period_key`   VARCHAR(10)  NOT NULL COMMENT '周期键：每日=2026-08-28；每周=2026-W35',
    `content`      TEXT         NOT NULL COMMENT 'AI 生成的内容',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_student_period` (`student_id`, `content_type`, `period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端 AI 内容缓存（定时预生成）';
