-- 为 word_error_record 表添加词性和延伸词字段
-- 使错题库能够展示完整的单词信息（如同词汇表一样）
-- 兼容 MySQL 5.7+ / 8.0，使用存储过程安全判断列是否存在

DELIMITER $$

DROP PROCEDURE IF EXISTS add_error_record_columns$$

CREATE PROCEDURE add_error_record_columns()
BEGIN
    -- 添加 part_of_speech 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND COLUMN_NAME = 'part_of_speech'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD COLUMN `part_of_speech` VARCHAR(50) COMMENT '词性（冗余存储）' AFTER `correct_answer`;
    END IF;

    -- 添加 extensions 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'english_tutor_system'
          AND TABLE_NAME = 'word_error_record'
          AND COLUMN_NAME = 'extensions'
    ) THEN
        ALTER TABLE `word_error_record`
            ADD COLUMN `extensions` VARCHAR(500) COMMENT '延伸词（冗余存储）' AFTER `part_of_speech`;
    END IF;
END$$

DELIMITER ;

CALL add_error_record_columns();

DROP PROCEDURE IF EXISTS add_error_record_columns;
