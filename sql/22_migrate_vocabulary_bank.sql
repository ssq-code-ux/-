-- ============================================================
-- 22_migrate_vocabulary_bank.sql
-- 词库（bank）迁移：为 vocabulary 表新增 bank 字段
--
-- 背景：教师端「背单词」需要按词库背诵，同时新增四级/六级词库。
--       存量单词（数据库原本的数据）统一归入「学生词库」，
--       四六级数据由 sql/23_seed_cet_vocabulary.sql 单独导入。
--
-- 规则：
--   学生/家长端只可见「学生词库」，其行为与迁移前完全一致；
--   教师端可选择任一已存在的词库进行背诵，也可在新增/编辑单词时指定词库。
--
-- 幂等性：已存在的列 / 索引会自动跳过，可放心重复执行。
-- 执行库：english_tutor_system
-- 用法：mysql -u root -p english_tutor_system < sql/22_migrate_vocabulary_bank.sql
-- ============================================================

-- ---------- 1. vocabulary.bank ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'vocabulary' AND COLUMN_NAME = 'bank') = 0,
    'ALTER TABLE vocabulary ADD COLUMN bank VARCHAR(50) NOT NULL DEFAULT ''学生词库'' COMMENT ''所属词库：学生词库/四级词库/六级词库'' AFTER extensions',
    'SELECT ''skip: vocabulary.bank 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. 存量数据兜底：NULL 或空串归入学生词库 ----------
UPDATE vocabulary SET bank = '学生词库' WHERE bank IS NULL OR bank = '';

-- ---------- 3. bank 索引（按词库过滤加速） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'vocabulary' AND INDEX_NAME = 'idx_bank') = 0,
    'ALTER TABLE vocabulary ADD INDEX idx_bank (bank)',
    'SELECT ''skip: idx_bank 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 4. 错题库记录所属词库（教师错题本按词库复习用） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND COLUMN_NAME = 'bank') = 0,
    'ALTER TABLE word_error_record ADD COLUMN bank VARCHAR(50) NULL COMMENT ''所属词库：学生词库/四级词库/六级词库'' AFTER translation',
    'SELECT ''skip: word_error_record.bank 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量错题兜底：按对应词汇的词库回填
UPDATE word_error_record e
JOIN vocabulary v ON e.word_id = v.id
SET e.bank = v.bank
WHERE e.bank IS NULL OR e.bank = '';

SELECT '词库迁移完成：vocabulary.bank 就位，存量数据已归入「学生词库」' AS done;