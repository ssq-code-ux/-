-- ============================================================
-- 26_seed_subjects.sql
-- 作业/资料「学科分类」：预置 9 个标准学科，存量作业归入「英语」
--
-- 变更：
--   1. 确保 subject 表存在（与 migrate_subject.sql 一致）
--   2. 预置学科：语文/数学/英语/政治/历史/地理/物理/化学/生物
--   3. 确保 homework.subject 列存在，并把存量作业归为「英语」
-- 幂等：可重复执行。执行库：english_tutor_system
-- ============================================================

CREATE TABLE IF NOT EXISTS `subject` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '学科名称',
    `teacher_id` BIGINT COMMENT '创建学科老师ID（仅追溯）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学科表';

INSERT IGNORE INTO `subject` (`name`) VALUES
('语文'),('数学'),('英语'),('政治'),('历史'),('地理'),('物理'),('化学'),('生物');

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'homework' AND COLUMN_NAME = 'subject') = 0,
    'ALTER TABLE homework ADD COLUMN subject VARCHAR(50) DEFAULT NULL COMMENT ''学科名称'' AFTER type',
    'SELECT ''skip: homework.subject 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE homework SET subject = '英语' WHERE subject IS NULL OR subject = '';

SELECT '学科预置完成（9 个标准学科），存量作业已归入英语' AS done;