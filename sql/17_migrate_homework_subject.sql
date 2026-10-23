-- 作业（含 AI 答题卡）多学科化 — 迁移脚本
-- 请在 english_tutor_system 数据库中执行

-- 1. 作业表增加学科列（存学科名称，与 subject 表的 name 对应；历史作业统一回填为英语）
ALTER TABLE `homework` ADD COLUMN `subject` VARCHAR(50) DEFAULT NULL COMMENT '学科名称' AFTER `type`;

-- 2. 历史作业回填：系统此前只服务英语学科，存量作业全部归为英语
UPDATE `homework` SET `subject` = '英语' WHERE `subject` IS NULL;
