-- 学习资料按学科分类 — 迁移脚本
-- 请在 english_tutor_system 数据库中执行

-- 1. 学科表（预置默认学科"英语"，不可删除）
CREATE TABLE IF NOT EXISTS `subject` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '学科名称',
    `teacher_id` BIGINT COMMENT '创建学科老师ID（仅追溯）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学科表';

INSERT IGNORE INTO `subject` (`name`) VALUES ('英语');

-- 2. 学习资料表增加学科列
ALTER TABLE `learning_resource` ADD COLUMN `subject_id` BIGINT COMMENT '学科ID' AFTER `type`;

-- 3. 已有资料回迁到默认"英语"学科
UPDATE `learning_resource` lr JOIN `subject` s ON s.name='英语'
   SET lr.subject_id = s.id WHERE lr.subject_id IS NULL;
