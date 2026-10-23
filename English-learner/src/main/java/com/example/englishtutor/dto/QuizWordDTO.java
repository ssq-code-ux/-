package com.example.englishtutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 测验单词 — 包含选项（用于阶段一：看单词选意思）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizWordDTO {
    private Long id;
    private String word;
    private String translation;
    private String partOfSpeech;
    /** 延伸词列表（逗号分隔的原始字符串） */
    private String extensions;
    /** 4个选项（含正确答案），前端随机打乱 */
    private List<String> options;
    /** 每个选项对应的源单词详细信息（用于选对后展示所有选项的来源单词） */
    private List<OptionInfo> optionDetails;
}
