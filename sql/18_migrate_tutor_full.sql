-- =====================================================================
-- 18_migrate_tutor_full.sql
-- 家教业务整套建表（管理端发布/审核 + 教师接单投递 + 家长需求 + 缴费退费 + 科目模板）
--
-- 服务器版此前没有任何家教相关表，所以这里直接建「最终形态」：
-- 微服务版 15~22 号迁移里那些 ADD COLUMN / MODIFY COLUMN / 存量数据回填
-- 全部合并进了 CREATE TABLE，不再需要按顺序跑一串脚本。
--
-- 幂等性：建表用 IF NOT EXISTS；给存量表 sys_user 加列走 information_schema 判断，
-- 重复执行不会报错。
-- 执行库：english_tutor_system
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) sys_user：账号封禁状态 + 教师邀请码（存量表，需判空后 ALTER）
-- ---------------------------------------------------------------------
SET @has_status = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'status');
SET @sql = IF(@has_status = 0,
              'ALTER TABLE `sys_user` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1正常 0封禁（封禁后登录/续期直接拒绝）'' AFTER `role`',
              'SELECT ''sys_user.status already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- invite_code / inviter_id / 唯一键 / 索引 一起加，用 invite_code 是否存在做闸门
SET @has_invite = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'invite_code');
SET @sql = IF(@has_invite = 0,
              'ALTER TABLE `sys_user` ADD COLUMN `invite_code` VARCHAR(16) DEFAULT NULL COMMENT ''本人邀请码（全表唯一）'', ADD COLUMN `inviter_id` BIGINT DEFAULT NULL COMMENT ''邀请人用户ID（用邀请码注册时写入）'', ADD UNIQUE KEY `uk_invite_code` (`invite_code`), ADD INDEX `idx_inviter` (`inviter_id`)',
              'SELECT ''sys_user.invite_code already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量用户回填：T + 6位用户ID（共 7 位）。
-- 应用层新生成的邀请码固定 6 位，长度不同，与回填码永不碰撞。
UPDATE `sys_user`
SET `invite_code` = CONCAT('T', LPAD(`id`, 6, '0'))
WHERE `invite_code` IS NULL;

-- 管理员种子账号：密码先明文入库，首次登录时 AuthServiceImpl 会自动升级成 BCrypt
INSERT IGNORE INTO `sys_user` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('admin', 'admin123', '系统管理员', 'admin', 1);

-- ---------------------------------------------------------------------
-- 2) 管理端操作审计日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_audit_log`
(
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `operator_id`    BIGINT      DEFAULT NULL COMMENT '操作人（管理员）ID',
    `operator_name`  VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名（冗余）',
    `action`         VARCHAR(50) DEFAULT NULL COMMENT '动作标识，如 BAN_USER / PUBLISH_JOB',
    `target_user_id` BIGINT      DEFAULT NULL COMMENT '被操作的用户ID（封禁/解封时用）',
    `detail`         VARCHAR(500) DEFAULT NULL COMMENT '详情描述',
    `ip`             VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_audit_operator` (`operator_id`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理端操作审计日志';

-- ---------------------------------------------------------------------
-- 3) 家教单（管理端发布，教师端浏览投递）
--    job_type 区分「家教单」与「同行合作单」，pay_to_apply 控制是否缴费解锁投递
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_job`
(
    `id`                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_no`                  VARCHAR(20)  NOT NULL COMMENT '单号；由家长需求发布而来时与 demand_no 同号，管理端可跨界面按单号搜',
    `job_type`                TINYINT      NOT NULL DEFAULT 1 COMMENT '1家教单 2同行合作单',
    `subject`                 VARCHAR(50)  NOT NULL COMMENT '科目（如：数学）',
    `province`                VARCHAR(50)  DEFAULT NULL COMMENT '省份',
    `city`                    VARCHAR(50)  DEFAULT NULL COMMENT '城市',
    `address`                 VARCHAR(200) DEFAULT NULL COMMENT '详细地址',
    `grade`                   VARCHAR(50)  DEFAULT NULL COMMENT '学生年级（如：初三）',
    `student_gender`          VARCHAR(10)  DEFAULT NULL COMMENT '学生性别',
    `expect_gender`           VARCHAR(10)  DEFAULT NULL COMMENT '老师性别要求（空=不限）',
    `frequency`               VARCHAR(100) DEFAULT NULL COMMENT '上课频次（如：每周1次，每次2小时）',
    `salary`                  INT          DEFAULT NULL COMMENT '时薪（元/小时）',
    `situation`               VARCHAR(500) DEFAULT NULL COMMENT '学生情况（如：补基础，提优）',
    `requirement`             VARCHAR(500) DEFAULT NULL COMMENT '要求（如：教学经验丰富，善于引导）',
    `pay_to_apply`            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否需缴费后才能投递：0否 1是（管理端标记教师已缴费时开通）',
    `status`                  TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1开放 0关闭（关闭后教师端不再展示、不能再投递）',
    `publisher_id`            BIGINT       NOT NULL COMMENT '发布管理员ID',
    `publisher_name`          VARCHAR(50)  DEFAULT NULL COMMENT '发布管理员姓名（冗余）',
    `parent_id`               BIGINT       DEFAULT NULL COMMENT '关联的家长用户ID（家长端提交需求生成）',
    `parent_demand_id`        BIGINT       DEFAULT NULL COMMENT '来源的家长需求单ID',
    `approved_teacher_id`     BIGINT       DEFAULT NULL COMMENT '审核通过的老师ID（该单关闭的原因）',
    `approved_application_id` BIGINT       DEFAULT NULL COMMENT '审核通过的投递记录ID',
    `create_time`             DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `close_time`              DATETIME     DEFAULT NULL COMMENT '关闭时间',
    UNIQUE KEY `uk_job_no` (`job_no`),
    INDEX `idx_job_status` (`status`),
    INDEX `idx_job_type` (`job_type`),
    INDEX `idx_job_city` (`city`),
    INDEX `idx_job_parent` (`parent_id`),
    INDEX `idx_job_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='家教信息单（管理端发布，教师端投递）';

-- ---------------------------------------------------------------------
-- 4) 投递记录 + 试课状态机
--    trial_status：简历通过 → 试课中 → 试课成功（订单完成）/ 试课失败（单子重开、投递清空、转退费工单）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_job_application`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_id`        BIGINT        NOT NULL COMMENT '家教单ID',
    `teacher_id`    BIGINT        NOT NULL COMMENT '投递老师ID',
    `teacher_name`  VARCHAR(50)   DEFAULT NULL COMMENT '老师姓名（冗余，列表直接可看）',
    `teacher_phone` VARCHAR(20)   DEFAULT NULL COMMENT '老师手机号（冗余，审核通过后管理员线下联系用）',
    `message`       VARCHAR(1000) DEFAULT NULL COMMENT '投递附言（自我介绍 / 教学经验）',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1通过 2未通过',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
    `review_time`   DATETIME      DEFAULT NULL COMMENT '审核时间',
    `trial_status`  TINYINT       DEFAULT NULL COMMENT '试课状态：0试课中（简历通过后） 1试课成功（订单完成） 2试课失败待退费 3已退费；NULL=未进入试课阶段',
    UNIQUE KEY `uk_job_teacher` (`job_id`, `teacher_id`),
    INDEX `idx_apply_teacher` (`teacher_id`),
    INDEX `idx_apply_trial_status` (`trial_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='家教投递记录（一个单子可多人投递；uk_job_teacher 数据库层兜底防重复投递）';

-- ---------------------------------------------------------------------
-- 5) 家长需求单（家长端只填联系方式即可提交，管理端审核后生成家教单）
--    demand_no 与生成出来的家教单 job_no 同号，两个界面看到的是同一个编号
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `parent_demand`
(
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `demand_no`        VARCHAR(20)  NOT NULL COMMENT '需求单编号（XQ+6位数字，唯一）',
    `parent_id`        BIGINT       NOT NULL COMMENT '家长用户ID',
    `contact_name`     VARCHAR(50)  DEFAULT NULL COMMENT '称呼（选填）',
    `phone`            VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `wechat`           VARCHAR(50)  DEFAULT NULL COMMENT '微信号（选填）',
    `student_level`    VARCHAR(200) DEFAULT NULL COMMENT '学生程度（如：基础较弱，需补基础）',
    `grade`            VARCHAR(50)  DEFAULT NULL COMMENT '年级（如：初二）',
    `student_gender`   VARCHAR(10)  DEFAULT NULL COMMENT '学生性别：男生/女生',
    `subject`          VARCHAR(50)  DEFAULT NULL COMMENT '学科（选填，发布家教单时由管理员补全）',
    `expect_gender`    VARCHAR(10)  DEFAULT NULL COMMENT '老师性别要求（空=不限）',
    `other_require`    VARCHAR(500) DEFAULT NULL COMMENT '对老师的其他要求',
    `salary`           INT          DEFAULT NULL COMMENT '能提供的时薪（元/小时）',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待发布（管理员还没生成家教单） 1已发布（已关联家教单）',
    `delete_requested` TINYINT      NOT NULL DEFAULT 0 COMMENT '家长是否申请删除：0否 1已申请（等管理端确认）',
    `job_id`           BIGINT       DEFAULT NULL COMMENT '关联生成的家教单ID',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `publish_time`     DATETIME     DEFAULT NULL COMMENT '管理员发布关联家教单的时间',
    UNIQUE KEY `uk_demand_no` (`demand_no`),
    INDEX `idx_demand_parent` (`parent_id`),
    INDEX `idx_demand_status` (`status`),
    INDEX `idx_demand_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='家长找家教需求单（家长端提交，管理端审核后生成家教单）';

-- ---------------------------------------------------------------------
-- 6) 教师接单简历（教师端维护，投递时自动附加；主键即教师用户ID）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `teacher_profile`
(
    `teacher_id`      BIGINT        NOT NULL COMMENT '教师用户ID（主键）',
    `subjects`        VARCHAR(200)  DEFAULT NULL COMMENT '接单科目',
    `available_time`  VARCHAR(200)  DEFAULT NULL COMMENT '可上课时间',
    `long_term`       VARCHAR(20)   DEFAULT NULL COMMENT '是否可长期授课：可/不可/面议',
    `name`            VARCHAR(50)   DEFAULT NULL COMMENT '姓名',
    `gender`          VARCHAR(10)   DEFAULT NULL COMMENT '性别',
    `age`             INT           DEFAULT NULL COMMENT '年龄',
    `school`          VARCHAR(100)  DEFAULT NULL COMMENT '学校',
    `major`           VARCHAR(100)  DEFAULT NULL COMMENT '专业',
    `grade_year`      VARCHAR(20)   DEFAULT NULL COMMENT '大几（如：大三）',
    `teach_years`     VARCHAR(20)   DEFAULT NULL COMMENT '教龄（如：2年）',
    `hometown`        VARCHAR(50)   DEFAULT NULL COMMENT '籍贯',
    `textbook_ver`    VARCHAR(200)  DEFAULT NULL COMMENT '可教教材版本',
    `teach_scope`     VARCHAR(200)  DEFAULT NULL COMMENT '可辅导年级及科目',
    `has_material`    VARCHAR(20)   DEFAULT NULL COMMENT '是否备有辅导教材：有/无',
    `other_advantage` VARCHAR(500)  DEFAULT NULL COMMENT '其他优势',
    `teach_advantage` VARCHAR(500)  DEFAULT NULL COMMENT '辅导优势',
    `photo_url`       VARCHAR(500)  DEFAULT NULL COMMENT '个人照URL（走现有 /api/files/upload）',
    `award_imgs`      VARCHAR(2000) DEFAULT NULL COMMENT '奖项证明图片URL（逗号分隔，非必填）',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`teacher_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='教师接单简历（教师端维护，投递时自动附加）';

-- ---------------------------------------------------------------------
-- 7) 管理端接单配置（单行表：接单须知 + 联系方式 + 收款码）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_job_config`
(
    `id`          INT AUTO_INCREMENT PRIMARY KEY,
    `notice_text` TEXT COMMENT '接单须知（文字）',
    `notice_imgs` TEXT COMMENT '接单须知图片URL，逗号分隔（多张）',
    `phone`       VARCHAR(20) COMMENT '管理端联系电话',
    `wechat`      VARCHAR(50) COMMENT '管理端微信号',
    `pay_qr_url`  VARCHAR(500) COMMENT '收款码图片URL',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updater_id`  BIGINT   DEFAULT NULL COMMENT '最后修改的管理员',
    -- 单行表：只允许一条记录
    UNIQUE KEY `uk_single_row` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理端接单配置（单行：须知/电话/微信/收款码）';

INSERT INTO `admin_job_config` (`id`)
VALUES (1)
ON DUPLICATE KEY UPDATE `id` = 1;

-- ---------------------------------------------------------------------
-- 8) 教师缴费记录（当前状态：按家教单维度，缴费后解锁该单投递权，退费后清零）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_job_payment`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_id`      BIGINT        NOT NULL COMMENT '家教单ID',
    `teacher_id`  BIGINT        NOT NULL COMMENT '教师用户ID',
    `amount`      DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '已缴金额（元）；0 表示未缴费/已退清',
    `paid_time`   DATETIME      DEFAULT NULL COMMENT '最近一次标记缴费时间',
    `refund_time` DATETIME      DEFAULT NULL COMMENT '退费时间（退费后金额清零）',
    `note`        VARCHAR(300)  DEFAULT NULL COMMENT '备注（缴费/退费说明）',
    `operator_id` BIGINT        DEFAULT NULL COMMENT '操作管理员ID',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_job_teacher` (`job_id`, `teacher_id`),
    INDEX `idx_pay_teacher` (`teacher_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='教师缴费记录（缴费解锁投递；退费清零）';

-- ---------------------------------------------------------------------
-- 9) 试课失败退费工单
--    试课失败后 tutor_job_application 会被整单清空，退费状态不能再挂在投递记录上，
--    独立成表：管理端「试课失败待退费」计数、办理退费、教师端看退费进度都从这里取。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_refund_task`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_id`        BIGINT         NOT NULL COMMENT '家教单ID',
    `job_no`        VARCHAR(20)    DEFAULT NULL COMMENT '单号（冗余，列表直接可看）',
    `teacher_id`    BIGINT         NOT NULL COMMENT '应退费教师ID',
    `teacher_name`  VARCHAR(50)    DEFAULT NULL COMMENT '教师姓名（冗余）',
    `teacher_phone` VARCHAR(20)    DEFAULT NULL COMMENT '教师电话（冗余，投递记录已删，只能靠这里联系）',
    `amount`        DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '应退金额；0=免费单，仅需标记已处理',
    `reason`        VARCHAR(300)   DEFAULT NULL COMMENT '退费原因',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0待退费 1已退费/已处理',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '试课失败登记时间',
    `refund_time`   DATETIME       DEFAULT NULL COMMENT '实际办结时间',
    `operator_id`   BIGINT         DEFAULT NULL COMMENT '办理人（管理员）',
    `note`          VARCHAR(300)   DEFAULT NULL COMMENT '办理备注',
    INDEX `idx_task_status` (`status`),
    INDEX `idx_task_job` (`job_id`),
    INDEX `idx_task_teacher` (`teacher_id`),
    INDEX `idx_task_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='试课失败退费工单（投递记录清空后退费状态的唯一载体）';

-- ---------------------------------------------------------------------
-- 10) 缴费/退费流水（发生过什么，每次各一条，只增不改）
--     tutor_job_payment 是「当前状态」，退费后金额清零、时间被覆盖，历史只能靠这张表还原
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_payment_log`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `job_id`        BIGINT         NOT NULL,
    `job_no`        VARCHAR(20)    DEFAULT NULL COMMENT '单号（冗余）',
    `teacher_id`    BIGINT         NOT NULL,
    `teacher_name`  VARCHAR(50)    DEFAULT NULL COMMENT '教师姓名（冗余）',
    `type`          TINYINT        NOT NULL COMMENT '1缴费 2退费',
    `amount`        DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '本次发生金额',
    `note`          VARCHAR(300)   DEFAULT NULL COMMENT '备注',
    `operator_id`   BIGINT         DEFAULT NULL COMMENT '经办管理员ID',
    `operator_name` VARCHAR(50)    DEFAULT NULL COMMENT '经办管理员姓名',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    INDEX `idx_log_job` (`job_id`),
    INDEX `idx_log_teacher` (`teacher_id`),
    INDEX `idx_log_type` (`type`),
    INDEX `idx_log_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='缴费/退费流水（时间记录，只增不改）';

-- ---------------------------------------------------------------------
-- 11) 各科目家教单用例模板（管理端发布时可选用后修改）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tutor_job_template`
(
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `subject`        VARCHAR(50)  NOT NULL COMMENT '科目',
    `name`           VARCHAR(100) NOT NULL COMMENT '模板名称',
    `job_type`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1家教单 2同行合作单',
    `grade`          VARCHAR(50)  DEFAULT NULL,
    `province`       VARCHAR(50)  DEFAULT NULL,
    `city`           VARCHAR(50)  DEFAULT NULL,
    `address`        VARCHAR(200) DEFAULT NULL,
    `student_gender` VARCHAR(10)  DEFAULT NULL,
    `expect_gender`  VARCHAR(10)  DEFAULT NULL,
    `frequency`      VARCHAR(100) DEFAULT NULL,
    `salary`         INT          DEFAULT NULL,
    `situation`      VARCHAR(500) DEFAULT NULL,
    `requirement`    VARCHAR(500) DEFAULT NULL,
    `pay_to_apply`   TINYINT      NOT NULL DEFAULT 1 COMMENT '0免费投递 1需缴费解锁',
    `enabled`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_tpl_subject_type` (`subject`, `job_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='各科目家教单用例模板（管理端可选用后修改发布）';

INSERT INTO `tutor_job_template` (`subject`, `name`, `job_type`, `grade`, `province`, `city`, `address`,
                                  `student_gender`, `expect_gender`, `frequency`, `salary`,
                                  `situation`, `requirement`, `pay_to_apply`, `enabled`)
VALUES ('语文', '语文·初中一对一（模板）', 1, '初二', '河南省', '郑州市', '金水区（具体地址待定）',
        '不限', '不限', '每周2次，每次2小时', 100,
        '基础中等，阅读理解与作文失分较多，需系统梳理答题思路',
        '中文或师范类相关专业，有初中语文带教经验，能针对阅读与作文专项训练', 1, 1),
       ('数学', '数学·初三冲刺（模板）', 1, '初三', '河南省', '郑州市', '中原区（具体地址待定）',
        '不限', '不限', '每周3次，每次2小时', 120,
        '基础较弱，函数与几何证明薄弱，需补基础并冲刺中考',
        '数学相关专业，熟悉河南中考考纲，有毕业班带教经验，善于引导', 1, 1),
       ('英语', '英语·高中提优（模板）', 1, '高一', '河南省', '郑州市', '郑东新区（具体地址待定）',
        '不限', '女', '每周2次，每次2小时', 130,
        '成绩中上，语法与完形填空需提优，口语较弱',
        '英语专业八级或有留学经历，发音标准，能兼顾应试与口语', 1, 1),
       ('物理', '物理·高二电磁学（模板）', 1, '高二', '河南省', '郑州市', '二七区（具体地址待定）',
        '男', '不限', '每周1次，每次3小时', 140,
        '力学尚可，电磁学部分概念混乱，实验题得分低',
        '物理相关专业，有高中物理教学经验，能配合实验演示讲解', 1, 1),
       ('化学', '化学·初三入门（模板）', 1, '初三', '河南省', '郑州市', '管城回族区（具体地址待定）',
        '不限', '不限', '每周2次，每次1.5小时', 110,
        '刚接触化学，元素符号与化学方程式记忆困难',
        '化学相关专业，善于总结口诀与归纳，有耐心', 1, 1),
       ('生物', '生物·高三一轮复习（模板）', 1, '高三', '河南省', '郑州市', '惠济区（具体地址待定）',
        '不限', '不限', '每周2次，每次2小时', 130,
        '知识点零散，遗传计算题失分严重，需一轮系统复习',
        '生物相关专业，熟悉高考命题趋势，能专项突破遗传计算', 1, 1),
       ('政治', '政治·高三主观题（模板）', 1, '高三', '河南省', '郑州市', '金水区（具体地址待定）',
        '不限', '不限', '每周1次，每次2小时', 100,
        '选择题稳定，主观题答题不规范、要点抓不全',
        '政治相关专业，能训练答题模板与时政热点结合', 1, 1),
       ('历史', '历史·高中材料题（模板）', 1, '高二', '河南省', '郑州市', '中原区（具体地址待定）',
        '不限', '不限', '每周1次，每次2小时', 100,
        '时间线混乱，材料题概括能力不足',
        '历史相关专业，擅长构建时间轴与史料分析训练', 1, 1),
       ('地理', '地理·高中自然地理（模板）', 1, '高一', '河南省', '郑州市', '郑东新区（具体地址待定）',
        '不限', '不限', '每周1次，每次2小时', 100,
        '自然地理（气候、洋流、地球运动）理解困难，需图示讲解',
        '地理相关专业，能结合地图与示意图讲解，有高中教学经验', 1, 1)
ON DUPLICATE KEY UPDATE `name`        = VALUES(`name`),
                        `grade`       = VALUES(`grade`),
                        `frequency`   = VALUES(`frequency`),
                        `salary`      = VALUES(`salary`),
                        `situation`   = VALUES(`situation`),
                        `requirement` = VALUES(`requirement`),
                        `update_time` = CURRENT_TIMESTAMP;
