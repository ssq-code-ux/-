-- 单词测验错题库
-- 在 english_tutor_system 数据库中执行

CREATE TABLE IF NOT EXISTS `word_error_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `word_id` BIGINT NOT NULL COMMENT '词汇ID',
    `word` VARCHAR(100) NOT NULL COMMENT '单词（冗余）',
    `translation` VARCHAR(255) NOT NULL COMMENT '释义（冗余）',
    `error_type` VARCHAR(20) NOT NULL COMMENT '错误类型: meaning(选错意思) / spelling(拼写错误)',
    `wrong_answer` VARCHAR(255) COMMENT '学生提交的错误答案',
    `correct_answer` VARCHAR(255) NOT NULL COMMENT '正确答案',
    `part_of_speech` VARCHAR(50) COMMENT '词性（冗余存储，方便错题库展示完整单词信息）',
    `extensions` VARCHAR(500) COMMENT '延伸词（冗余存储）',
    `reviewed` TINYINT DEFAULT 0 COMMENT '是否已掌握: 0-未掌握 1-已掌握',
    `error_count` INT DEFAULT 1 COMMENT '累计错误次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近错误时间',
    INDEX `idx_student` (`student_id`),
    INDEX `idx_word` (`word_id`),
    INDEX `idx_student_reviewed` (`student_id`, `reviewed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词错误记录表（词汇测验专用错题库）';
