-- 英语家教系统 新增表结构
-- 请在 english_tutor_system 数据库中执行

CREATE TABLE IF NOT EXISTS `vocabulary` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `word` VARCHAR(100) NOT NULL COMMENT '单词',
    `phonetic` VARCHAR(100) COMMENT '音标',
    `translation` VARCHAR(255) NOT NULL COMMENT '释义',
    `part_of_speech` VARCHAR(50) COMMENT '词性',
    `level` VARCHAR(20) DEFAULT '初级' COMMENT '等级',
    `example` VARCHAR(500) COMMENT '例句',
    `category` VARCHAR(100) COMMENT '分类',
    `teacher_id` BIGINT COMMENT '创建老师ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词汇表';

CREATE TABLE IF NOT EXISTS `learning_resource` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `type` VARCHAR(50) COMMENT '类型(grammar/reading/listening/writing)',
    `level` VARCHAR(20) DEFAULT '初级' COMMENT '等级',
    `content` TEXT COMMENT '内容',
    `tags` VARCHAR(255) COMMENT '标签',
    `teacher_id` BIGINT COMMENT '创建老师ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资料表';

CREATE TABLE IF NOT EXISTS `daily_checkin` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `check_date` DATE NOT NULL COMMENT '打卡日期',
    `status` VARCHAR(20) DEFAULT '已打卡' COMMENT '状态',
    `remark` VARCHAR(255) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_student_date` (`student_id`, `check_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日打卡表';

-- 插入测试词汇数据
INSERT INTO `vocabulary` (`word`, `phonetic`, `translation`, `part_of_speech`, `level`, `example`, `category`, `teacher_id`) VALUES
('abandon', '/əˈbændən/', '放弃；抛弃', 'verb', '中级', 'He abandoned his plan to travel.', '动词', 1),
('brilliant', '/ˈbrɪliənt/', '杰出的；明亮的', 'adj', '中级', 'She had a brilliant idea.', '形容词', 1),
('curious', '/ˈkjʊriəs/', '好奇的', 'adj', '初级', 'The child is curious about everything.', '形容词', 1),
('demonstrate', '/ˈdemənstreɪt/', '演示；证明', 'verb', '高级', 'Please demonstrate how it works.', '动词', 1),
('essential', '/ɪˈsenʃəl/', '必要的；本质的', 'adj', '中级', 'Water is essential for life.', '形容词', 1),
('flexible', '/ˈfleksəbl/', '灵活的', 'adj', '中级', 'We need a flexible schedule.', '形容词', 1),
('gratitude', '/ˈɡrætɪtjuːd/', '感激', 'noun', '高级', 'I express my gratitude to you.', '名词', 1),
('hesitate', '/ˈhezɪteɪt/', '犹豫', 'verb', '中级', 'Don''t hesitate to ask questions.', '动词', 1),
('impressive', '/ɪmˈpresɪv/', '令人印象深刻的', 'adj', '中级', 'That was an impressive performance.', '形容词', 1),
('journey', '/ˈdʒɜːni/', '旅程', 'noun', '初级', 'Life is a journey.', '名词', 1);

-- 插入测试学习资料
INSERT INTO `learning_resource` (`title`, `type`, `level`, `content`, `tags`, `teacher_id`) VALUES
('基础语法：时态总结', 'grammar', '中级', '英语有16种时态，其中最常用的有：一般现在时、一般过去时、一般将来时、现在完成时、现在进行时...', '语法,时态', 1),
('阅读练习：The Little Prince', 'reading', '初级', 'Once when I was six years old I saw a magnificent picture in a book...', '阅读,文学', 1),
('听力技巧：关键词捕捉', 'listening', '中级', '在听力考试中，学会捕捉关键词非常重要。以下是几个实用技巧...', '听力,技巧', 1);
