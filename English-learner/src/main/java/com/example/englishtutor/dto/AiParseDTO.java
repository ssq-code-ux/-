package com.example.englishtutor.dto;

import lombok.Data;

/** AI 解析题目出卷请求：教师粘贴的原始题目文本 */
@Data
public class AiParseDTO {
    /** 原始题目文本，可以是从试卷复制的一段，允许不带答案 */
    private String rawText;
    /** 学科名称（如 英语/数学/语文），为空按英语处理；决定出卷 prompt 用哪套题型与解析口径 */
    private String subject;
    /** 未指明分值时每题默认分值，默认 5 */
    private Integer defaultScore;
    /**
     * 大题类型提示：教师在向导里指定时传，AI 就不再自行判断，识别准确率明显更高。
     * 取值同 {@link SectionDTO#getSectionType()}；为空表示让 AI 自动识别。
     */
    private String sectionHint;
}
