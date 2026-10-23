-- ============================================
-- 英语家教系统 — 迁移脚本 15
-- 错题幂等唯一索引 + 词汇搜索索引（对齐微服务版 sql/05）
-- 注意：如索引已存在，ALTER 语句会报 Duplicate key name，可跳过
-- ============================================

-- 1. 错题幂等：先去重存量数据（同一学生+同一单词+同一错误类型只保留最新一条），
--    再加联合唯一索引，防止并发提交/前端重试导致重复记录
--    （代码层已配合捕获 DuplicateKeyException 自动降级为 UPDATE）
DELETE e1 FROM word_error_record e1
INNER JOIN word_error_record e2
  ON e1.student_id = e2.student_id
 AND e1.word_id = e2.word_id
 AND e1.error_type = e2.error_type
 AND e1.id < e2.id;

ALTER TABLE word_error_record
    ADD UNIQUE INDEX uk_student_word_type (student_id, word_id, error_type);

-- 2. 词汇搜索加速：/api/vocabularies/search 走 MySQL LIKE 模糊搜索，加索引提速
ALTER TABLE vocabulary
    ADD INDEX idx_word (word),
    ADD INDEX idx_translation (translation);
