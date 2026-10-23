package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/** 学生提交答题卡：逐题作答 */
@Data
public class AnswerSheetSubmitDTO {
    private Long homeworkId;
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        /** 选择题为选项key（多选拼接如 "AC"），填空/简答为文本；未作答传空 */
        private String answer;
    }
}
