package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 答题卡批改结果（逐题）
 * <p>
 * 学生提交后才返回，此时才带上 standardAnswer 与 analysis。
 */
@Data
public class AnswerResultDTO {
    private Long questionId;
    /** 所属大题序号，结果页靠它分组展示 */
    private Integer sectionSeq;
    private String sectionType;
    private String sectionTitle;
    private Integer seq;
    private String questionType;
    private String content;
    private List<QuestionDTO.OptionItem> options;
    private String standardAnswer;
    private String analysis;
    private String studentAnswer;
    /** 1=对 0=错 null=待判 */
    private Integer correct;
    private Integer gotScore;
    /** 该题满分 */
    private Integer score;
    /** 考查的知识点，给学生指一下复习方向 */
    private String knowledgePoint;
    private String aiComment;
    /** 教师是否复核调整过该题 */
    private Integer teacherFixed;
}
