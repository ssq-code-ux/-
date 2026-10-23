package com.example.englishtutor.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 布置答题卡作业：作业基本信息 + 教师核验过的大题列表 */
@Data
public class AnswerSheetDTO {
    private Long studentId;
    private String title;
    /** 学科名称（如 英语/数学/语文），随作业落库；空表示未指定（历史数据按英语处理） */
    private String subject;
    /** 作业说明/注意事项 */
    private String content;
    private LocalDateTime deadline;
    /** 按大题组织的题目；教师端出卷向导统一走这个字段 */
    private List<SectionDTO> sections;
}
