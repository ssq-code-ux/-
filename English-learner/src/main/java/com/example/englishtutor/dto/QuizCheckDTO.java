package com.example.englishtutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测验检查结果 — 单题即时反馈
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizCheckDTO {
    /** 是否正确 */
    private boolean correct;
    /** 正确答案 */
    private String correctAnswer;
    /** 延伸词（仅答对时返回） */
    private String extensions;
    /** 错误提示（仅答错时返回） */
    private String errorHint;
}
