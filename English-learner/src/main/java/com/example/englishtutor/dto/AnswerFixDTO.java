package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 教师复核答题卡：调整个别题目的判定与得分
 * <p>
 * AI 不是终审 —— 一次误判如果没法改，老师就不敢再用自动批改。
 */
@Data
public class AnswerFixDTO {
    private Long submitId;
    private List<FixItem> items;
    /** 教师整体评语，为空则保留 AI 生成的评语 */
    private String comment;

    @Data
    public static class FixItem {
        private Long questionId;
        /** 1=判对 0=判错 */
        private Integer correct;
        private Integer gotScore;
        /** 教师点评，覆盖 AI 点评 */
        private String comment;
    }
}
