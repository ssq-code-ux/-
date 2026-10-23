package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 答题卡大题（学生侧）— 已剥离小题的 standardAnswer / analysis
 * <p>
 * 材料、词库、共用选项是作答必需的信息，不属于答案，可以下发。
 */
@Data
public class StudentSectionDTO {
    private Integer seq;
    private String sectionType;
    private String title;
    private String instruction;
    private String passage;
    private List<String> wordBank;
    private List<QuestionDTO.OptionItem> sharedOptions;
    private List<StudentQuestionDTO> questions;
}
