-- 修复脚本：在 english_tutor_system 数据库执行

ALTER TABLE `learning_resource` ADD COLUMN `file_url` VARCHAR(500) COMMENT '文件URL';

ALTER TABLE `vocabulary`
  ADD COLUMN `extensions` VARCHAR(500) COMMENT '延伸词（逗号分隔）',
  DROP COLUMN `example`;
