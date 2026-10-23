package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 答题卡题目（学生侧）— 已剥离 standardAnswer / analysis
 * <p>
 * 学生作答阶段绝不能拿到答案，所以单独定义一个窄 DTO，而不是把实体的答案字段置空后返回。
 */
@Data
public class StudentQuestionDTO {
    private Long id;
    private Integer seq;
    private String questionType;
    private String content;
    private List<QuestionDTO.OptionItem> options;
    private Integer score;
}
