-- ============================================================
-- 线上库迁移补齐脚本（执行库：english_tutor_system）
--
-- 背景：线上环境部署了新代码（答题卡 / 微信推送 / 家长端 AI / 双 Token 鉴权），
--       但 sql/10 ~ sql/15 的迁移没有在库上执行过，导致登录等接口报
--       Unknown column 'push_token' in 'field list'。
--
-- 本脚本把 sql/10 ~ sql/15 的全部变更合并为幂等版本：
--       已存在的列 / 表 / 索引会自动跳过，可放心重复执行。
--
-- 用法（服务器宿主机上，进入项目根目录后执行）：
--   mysql -u root -p english_tutor_system < sql/16_backfill_missed_migrations.sql
-- ============================================================

-- ---------- 1. sys_user.push_token（原 sql/10：微信推送令牌） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'push_token') = 0,
    'ALTER TABLE sys_user ADD COLUMN push_token VARCHAR(64) NULL COMMENT ''微信推送令牌（PushPlus token / Server酱 SendKey），为空表示未开启'' AFTER phone',
    'SELECT ''skip: sys_user.push_token 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. homework_review.voice_url（原 sql/10：语音评语地址） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'homework_review' AND COLUMN_NAME = 'voice_url') = 0,
    'ALTER TABLE homework_review ADD COLUMN voice_url VARCHAR(500) NULL COMMENT ''语音评语文件地址（OSS URL）'' AFTER comment',
    'SELECT ''skip: homework_review.voice_url 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3. homework.type 含义更新为作业形态（原 sql/11） ----------
ALTER TABLE homework
    MODIFY COLUMN type VARCHAR(32) NULL COMMENT '作业形态：normal=传统作业（默认，NULL 也按 normal 处理）；answer_sheet=答题卡';

-- ---------- 4. 答题卡题目表（原 sql/11） ----------
CREATE TABLE IF NOT EXISTS `homework_question` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `homework_id`     BIGINT       NOT NULL COMMENT '所属作业ID',
    `seq`             INT          NOT NULL COMMENT '题号，从 1 开始，决定答题卡展示顺序',
    `question_type`   VARCHAR(20)  NOT NULL COMMENT '题型：single=单选 multi=多选 judge=判断 blank=填空 short=简答/写作',
    `content`         TEXT         NOT NULL COMMENT '题干',
    `options`         TEXT         NULL COMMENT '选项，JSON 数组如 [{"key":"A","text":"..."}]，非选择题为 NULL',
    `standard_answer` VARCHAR(1000) NULL COMMENT '标准答案。选择题存选项key（多选如 "AC"）；填空多解用竖线分隔 "answer1|answer2"',
    `analysis`        TEXT         NULL COMMENT '解析，学生提交后才可见',
    `score`           INT          NOT NULL DEFAULT 5 COMMENT '该题分值',
    `knowledge_point` VARCHAR(100) NULL COMMENT '知识点标签，用于错因统计',
    `ai_generated`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否 AI 解析生成：1=AI 出题 0=教师手工录入',
    `confirmed`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '教师是否已核验：0=待核验（学生看不到）1=已确认',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_hq_homework` (`homework_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题卡题目表';

-- ---------- 5. 答题卡学生作答表（原 sql/11） ----------
CREATE TABLE IF NOT EXISTS `homework_answer` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `submit_id`      BIGINT       NOT NULL COMMENT '所属提交记录ID（homework_submit.id）',
    `question_id`    BIGINT       NOT NULL COMMENT '题目ID（homework_question.id）',
    `student_answer` TEXT         NULL COMMENT '学生答案：选择题为选项key，填空/简答为文本',
    `correct`        TINYINT(1)   NULL COMMENT '判定结果：1=对 0=错 NULL=待判（主观题 AI 未返回时）',
    `got_score`      INT          NOT NULL DEFAULT 0 COMMENT '该题实际得分',
    `ai_comment`     VARCHAR(500) NULL COMMENT 'AI 点评/扣分理由，客观题为空',
    `teacher_fixed`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '教师是否手工改过判定：1=已复核调整',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_submit_question` (`submit_id`, `question_id`),
    KEY `idx_ha_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题卡学生作答与判分表';

-- ---------- 6. 答题卡大题（题组）表（原 sql/12） ----------
CREATE TABLE IF NOT EXISTS `homework_section` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `homework_id`    BIGINT       NOT NULL COMMENT '所属作业ID',
    `seq`            INT          NOT NULL COMMENT '大题序号，从 1 开始',
    `section_type`   VARCHAR(20)  NOT NULL COMMENT '大题类型：cloze=完形填空 reading=阅读理解 word_bank=选词填空 blank_fill=盲填/首字母填空 dialogue=补全对话 writing=书面表达 standalone=单句题（无材料）',
    `title`          VARCHAR(200) NULL COMMENT '大题标题，如「二、完形填空」',
    `instruction`    VARCHAR(500) NULL COMMENT '答题要求，如「从每题所给的四个选项中选出最佳选项」',
    `passage`        TEXT         NULL COMMENT '材料原文：完形/阅读的文章、对话原文、写作要求；单句题为 NULL',
    `word_bank`      TEXT         NULL COMMENT '选词填空的备选词库，JSON 数组如 ["make","happy"]',
    `shared_options` TEXT         NULL COMMENT '整个大题共用的选项（补全对话 7选5 的选项框），JSON 数组如 [{"key":"A","text":"..."}]',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_hs_homework` (`homework_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题卡大题（题组）表';

-- ---------- 7. homework_question.section_id（原 sql/12：小题挂到大题下） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'homework_question' AND COLUMN_NAME = 'section_id') = 0,
    'ALTER TABLE homework_question ADD COLUMN section_id BIGINT NULL COMMENT ''所属大题ID（homework_section.id）；NULL=单句题，无共享材料'' AFTER homework_id',
    'SELECT ''skip: homework_question.section_id 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'homework_question' AND INDEX_NAME = 'idx_hq_section') = 0,
    'ALTER TABLE homework_question ADD KEY idx_hq_section (section_id, seq)',
    'SELECT ''skip: idx_hq_section 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 8. sys_user.notify_prefs（原 sql/13：推送订阅偏好） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_prefs') = 0,
    'ALTER TABLE sys_user ADD COLUMN notify_prefs VARCHAR(200) NULL COMMENT ''微信推送订阅偏好 JSON；NULL=全部订阅'' AFTER push_token',
    'SELECT ''skip: sys_user.notify_prefs 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 9. 家长端 AI 内容缓存表（原 sql/14） ----------
CREATE TABLE IF NOT EXISTS `ai_content_cache` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id`   BIGINT       NOT NULL COMMENT '学生ID',
    `content_type` VARCHAR(20)  NOT NULL COMMENT 'daily_advice=每日学习建议 weekly_summary=周报总结',
    `period_key`   VARCHAR(10)  NOT NULL COMMENT '周期键：每日=2026-08-28；每周=2026-W35',
    `content`      TEXT         NOT NULL COMMENT 'AI 生成的内容',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_student_period` (`student_id`, `content_type`, `period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端 AI 内容缓存（定时预生成）';

-- ---------- 10. 错题幂等：存量去重 + 唯一索引（原 sql/15） ----------
-- 去重可重复执行：第二次跑时已无重复行，DELETE 0 行
DELETE e1 FROM word_error_record e1
INNER JOIN word_error_record e2
  ON e1.student_id = e2.student_id
 AND e1.word_id = e2.word_id
 AND e1.error_type = e2.error_type
 AND e1.id < e2.id;

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'word_error_record' AND INDEX_NAME = 'uk_student_word_type') = 0,
    'ALTER TABLE word_error_record ADD UNIQUE INDEX uk_student_word_type (student_id, word_id, error_type)',
    'SELECT ''skip: uk_student_word_type 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 11. 词汇搜索加速索引（原 sql/15） ----------
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'vocabulary' AND INDEX_NAME = 'idx_word') = 0,
    'ALTER TABLE vocabulary ADD INDEX idx_word (word)',
    'SELECT ''skip: idx_word 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'vocabulary' AND INDEX_NAME = 'idx_translation') = 0,
    'ALTER TABLE vocabulary ADD INDEX idx_translation (translation)',
    'SELECT ''skip: idx_translation 已存在'' AS info');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 完成 ----------
SELECT '迁移补齐完成：sql/10~15 全部就位' AS done;
