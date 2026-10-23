-- ============================================================
-- 25_migrate_error_phonetic.sql
-- 错词记录补充读音字段（错题本/复习本/已掌握本的单词详情展示用）
--
-- 变更：word_error_record 增加 phonetic（音标），并从 vocabulary 回填存量数据。
-- 幂等：已存在列会自动跳过。
-- 执行库：english_tutor_system
-- 用法：mysql -u root -p --default-character-set=utf8mb4 english_tutor_system < sql/25_migrate_error_phonetic.sql
-- ============================================================

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND COLUMN_NAME = 'phonetic') = 0,
    'ALTER TABLE word_error_record ADD COLUMN phonetic VARCHAR(100) NULL COMMENT ''读音（音标）'' AFTER word',
    'SELECT ''skip: word_error_record.phonetic 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量回填：按对应词汇的词库读音
UPDATE word_error_record e
JOIN vocabulary v ON e.word_id = v.id
SET e.phonetic = v.phonetic
WHERE (e.phonetic IS NULL OR e.phonetic = '')
  AND v.phonetic IS NOT NULL AND v.phonetic <> '';

SELECT '错词读音迁移完成' AS done;