-- ============================================================
-- 答题卡「大题（Section）」分层 迁移脚本（执行库：english_tutor_system）
--
-- 为什么要加这一层：英语卷子里完形填空、阅读理解、选词填空、补全对话都是
-- 「多个小题共享一段材料」，原来的扁平结构只能把它们拆成一堆独立单选题，
-- 材料原文直接丢失，学生看不到上下文根本没法作答。
--
-- 分层后：Homework → Section（大题：题型分类+材料）→ Question（小题：作答+判分）
-- section_type 决定「怎么展示」，question_type 仍决定「怎么判分」，判分逻辑不受影响。
-- ⚠️ 旧答题卡的 section_id 为 NULL，代码里按「单句题」虚拟大题处理，不需要刷数据
-- ============================================================

-- ---------- 1. 大题表 ----------
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

-- ---------- 2. 小题挂到大题下 ----------
ALTER TABLE `homework_question`
    ADD COLUMN `section_id` BIGINT NULL COMMENT '所属大题ID（homework_section.id）；NULL=单句题，无共享材料' AFTER `homework_id`;

ALTER TABLE `homework_question`
    ADD KEY `idx_hq_section` (`section_id`, `seq`);
