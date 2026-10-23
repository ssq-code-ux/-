-- ============================================
-- 20_migrate_student_profile_subject.sql
-- 学生档案新增「学科」字段
-- 背景：教师端学生管理支持编辑学生基本信息（年级 / 学科 / 掌握程度），
--       原 student_profile 表只有 grade(年级)、level(掌握程度)，缺 subject(学科)。
-- 执行方式：在 english_tutor_system 库执行一次即可。
-- 幂等：用 information_schema 判断列是否已存在，重复执行不会报错。
-- ============================================

SET @dbname = DATABASE();
SET @tablename = 'student_profile';
SET @columnname = 'subject';

SET @preparedStatement = (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) = 0,
    'ALTER TABLE `student_profile` ADD COLUMN `subject` VARCHAR(64) NULL COMMENT ''学科'' AFTER `grade`',
    'SELECT ''column subject already exists, skip'' AS msg'
  )
);

PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
