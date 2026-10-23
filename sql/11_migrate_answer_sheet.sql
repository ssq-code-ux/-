-- ============================================================
-- 答题卡作业 + AI 自动批改 迁移脚本（执行库：english_tutor_system）
-- 1. homework.type 复用为作业形态标识：normal（传统作业）/ answer_sheet（答题卡）
-- 2. 新表 homework_question：答题卡的题目与标准答案（AI 出题 + 教师核验）
-- 3. 新表 homework_answer：学生逐题作答与判分结果（行级存储，便于知识点统计/错题库联动）
-- ⚠️ homework.type 本来就存在（旧数据为空），这里只补注释，不新增列
-- ============================================================

-- ---------- 1. homework 表：说明 type 的新含义 ----------
ALTER TABLE homework
    MODIFY COLUMN type VARCHAR(32) NULL COMMENT '作业形态：normal=传统作业（默认，NULL 也按 normal 处理）；answer_sheet=答题卡';

-- ---------- 2. 答题卡题目表 ----------
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

-- ---------- 3. 学生逐题作答表 ----------
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
