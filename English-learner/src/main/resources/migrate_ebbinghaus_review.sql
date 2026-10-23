-- ============================================================
-- 艾宾浩斯记忆曲线复习调度 — word_error_record 表升级
-- 兼容 MySQL 5.7+ / 8.0，使用存储过程安全判断列/索引是否存在（可重复执行）
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_ebbinghaus_review_columns$$

CREATE PROCEDURE add_ebbinghaus_review_columns()
BEGIN
    -- 添加 review_stage 列（艾宾浩斯复习阶段）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND COLUMN_NAME = 'review_stage'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD COLUMN `review_stage` INT NOT NULL DEFAULT 0 COMMENT '艾宾浩斯复习阶段（0-6，走完 6 阶段后掌握）' AFTER `error_count`;
    END IF;

    -- 添加 next_review_time 列（下次计划复习时间）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND COLUMN_NAME = 'next_review_time'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD COLUMN `next_review_time` DATETIME NULL COMMENT '下次计划复习时间（null=已掌握/无计划）' AFTER `review_stage`;
    END IF;

    -- 添加 last_review_time 列（最近一次复习时间）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND COLUMN_NAME = 'last_review_time'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD COLUMN `last_review_time` DATETIME NULL COMMENT '最近一次复习时间' AFTER `next_review_time`;
    END IF;

    -- 「今日复习」查询索引（按学生 + 掌握状态 + 到期时间）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND INDEX_NAME = 'idx_next_review'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD INDEX `idx_next_review` (`student_id`, `reviewed`, `next_review_time`);
    END IF;
END$$

DELIMITER ;

CALL add_ebbinghaus_review_columns();

DROP PROCEDURE IF EXISTS add_ebbinghaus_review_columns;

-- 存量未掌握错题回填：安排首次复习（错误时间 + 1 天；已到期的立即出现在今日复习）
UPDATE word_error_record
SET next_review_time = DATE_ADD(COALESCE(create_time, NOW()), INTERVAL 1 DAY)
WHERE reviewed = 0 AND next_review_time IS NULL;
