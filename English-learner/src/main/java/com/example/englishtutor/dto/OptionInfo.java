package com.example.englishtutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选项详细信息 — 记录每个选项对应的源单词信息
 * 当学生选对后，前端展示所有选项的来源单词，帮助扩展学习
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionInfo {
    /** 选项文字（如"放弃"） */
    private String optionText;
    /** 来源单词（如"abandon"） */
    private String sourceWord;
    /** 来源单词释义 */
    private String sourceTranslation;
    /** 来源单词词性 */
    private String partOfSpeech;
    /** 来源单词延伸词 */
    private String extensions;
}
