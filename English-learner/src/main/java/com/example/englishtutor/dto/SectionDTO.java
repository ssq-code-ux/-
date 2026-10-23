package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 答题卡大题（教师侧 / AI 出卷结果）— 材料 + 该大题下的小题
 * <p>
 * ⚠️ 内含 standardAnswer / analysis，只能返回给教师，学生端请用 {@link StudentSectionDTO}
 */
@Data
public class SectionDTO {
    /** 已有大题的ID，新增时为 null */
    private Long id;
    private Integer seq;
    /**
     * cloze=完形填空 reading=阅读理解 word_bank=选词填空
     * blank_fill=盲填 dialogue=补全对话 writing=书面表达 standalone=单句题
     */
    private String sectionType;
    private String title;
    /** 答题要求 */
    private String instruction;
    /** 材料原文，单句题为 null */
    private String passage;
    /** 选词填空的备选词库 */
    private List<String> wordBank;
    /** 大题共用选项（补全对话 7选5），小题不再各自带 options */
    private List<QuestionDTO.OptionItem> sharedOptions;
    private List<QuestionDTO> questions;
}
