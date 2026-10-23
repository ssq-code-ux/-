-- ============================================================
-- 24_migrate_word_books.sql
-- 背单词改版：音标字段 + 错题三本状态机（错题本 / 复习本 / 已掌握本）
--
-- 变更：
--   1. vocabulary.phonetic        单词读音（音标），卡片翻转后展示
--   2. word_error_record.book     所属本子：error=错题本 / review=复习本 / mastered=已掌握本
--   3. 错词去重：一个用户一个单词只保留一条记录
--      （原唯一索引是 学生+单词+错误类型，会让同一单词在本子里出现多条）
--   4. 唯一索引切换为 (student_id, word_id)，并加 (student_id, book) 查询索引
--
-- 幂等性：已存在的列 / 索引自动跳过，可重复执行。
-- 执行库：english_tutor_system
-- 用法：mysql -u root -p --default-character-set=utf8mb4 english_tutor_system < sql/24_migrate_word_books.sql
-- ============================================================

-- ---------- 1. vocabulary.phonetic（读音/音标） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'vocabulary' AND COLUMN_NAME = 'phonetic') = 0,
    'ALTER TABLE vocabulary ADD COLUMN phonetic VARCHAR(100) NULL COMMENT ''读音（音标，如 /ˈæpl/）'' AFTER word',
    'SELECT ''skip: vocabulary.phonetic 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. word_error_record.book ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND COLUMN_NAME = 'book') = 0,
    'ALTER TABLE word_error_record ADD COLUMN book VARCHAR(20) NOT NULL DEFAULT ''error'' COMMENT ''本子：error=错题本 review=复习本 mastered=已掌握本'' AFTER reviewed',
    'SELECT ''skip: word_error_record.book 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量回填：原来 reviewed=1 视为已掌握，其余进错题本
UPDATE word_error_record SET book = IF(reviewed = 1, 'mastered', 'error')
WHERE book IS NULL OR book = '';

-- ---------- 3. 错词去重：同一 (student_id, word_id) 只保留一条 ----------
-- 3.1 把重复行的错误次数累加到保留行（保留 id 最大的一条）
UPDATE word_error_record keep
JOIN (
    SELECT student_id, word_id, MAX(id) AS keep_id, SUM(error_count) AS total_count
    FROM word_error_record
    GROUP BY student_id, word_id
    HAVING COUNT(*) > 1
) d ON keep.id = d.keep_id
SET keep.error_count = d.total_count;

-- 3.2 删除重复行（可重复执行，第二次已无重复）
DELETE e1 FROM word_error_record e1
INNER JOIN word_error_record e2
  ON e1.student_id = e2.student_id
 AND e1.word_id = e2.word_id
 AND e1.id < e2.id;

-- ---------- 4. 唯一索引切换：(student_id, word_id, error_type) → (student_id, word_id) ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND INDEX_NAME = 'uk_student_word_type') > 0,
    'ALTER TABLE word_error_record DROP INDEX uk_student_word_type',
    'SELECT ''skip: uk_student_word_type 不存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND INDEX_NAME = 'uk_student_word') = 0,
    'ALTER TABLE word_error_record ADD UNIQUE INDEX uk_student_word (student_id, word_id)',
    'SELECT ''skip: uk_student_word 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 5. 按本子查询索引 ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND INDEX_NAME = 'idx_student_book') = 0,
    'ALTER TABLE word_error_record ADD INDEX idx_student_book (student_id, book)',
    'SELECT ''skip: idx_student_book 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SELECT '单词三本迁移完成：phonetic + book 就位，错词已去重' AS done;