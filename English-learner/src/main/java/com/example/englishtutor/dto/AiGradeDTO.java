package com.example.englishtutor.dto;

import lombok.Data;
import java.util.List;

/**
 * 主观题 AI 评分请求（homework 服务 → ai 服务）
 * <p>
 * 一次请求带上全部主观题，AI 一次返回所有题的得分，比逐题调用省 token 也更快。
 */
@Data
public class AiGradeDTO {
    /** 这张卷子的学科（如 英语/数学/语文），AI 据此选择评分口径；空按英语处理 */
    private String subject;
    private List<GradeItem> items;

    @Data
    public static class GradeItem {
        private Long questionId;
        /** 判分题型：single/multi/judge/blank/short，告诉 AI 该按填空还是问答的尺度打分 */
        private String questionType;
        /** 所属大题类型（writing=作文），作文没有标准答案，AI 要换一套维度化评分规则 */
        private String sectionType;
        private String content;
        private String standardAnswer;
        private String studentAnswer;
        /** 该题满分 */
        private Integer fullScore;
    }
}
