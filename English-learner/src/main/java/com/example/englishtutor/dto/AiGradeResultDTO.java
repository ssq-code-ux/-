package com.example.englishtutor.dto;

import lombok.Data;

/** 主观题 AI 评分结果（ai 服务 → homework 服务），一题一条 */
@Data
public class AiGradeResultDTO {
    private Long questionId;
    private Integer gotScore;
    /** 扣分理由/改进建议，展示给学生 */
    private String comment;
}
