package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 答题卡题目（教师侧 / AI 出题结果）— 含标准答案与解析
 * <p>
 * ⚠️ 该 DTO 只能返回给教师，学生端请用 {@link StudentQuestionDTO}
 */
@Data
public class QuestionDTO {
    /** 已有题目的ID，新增时为 null */
    private Long id;
    private Integer seq;
    /** single=单选 multi=多选 judge=判断 blank=填空 short=简答/写作 */
    private String questionType;
    private String content;
    private List<OptionItem> options;
    private String standardAnswer;
    private String analysis;
    private Integer score;
    private String knowledgePoint;

    @Data
    public static class OptionItem {
        private String key;
        private String text;
    }
}
