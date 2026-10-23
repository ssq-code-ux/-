package com.example.englishtutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 词汇扩展结果
 * <p>
 * 输入一个单词，AI 返回主词信息 + 建议的延伸词列表。
 * 用户确认后，主词作为一条 Vocabulary 记录存入，
 * 延伸词以逗号分隔存入 extensions 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIExpandResultDTO {

    /** 主词 */
    private String word;

    /** 中文释义 */
    private String translation;

    /** 词性 */
    private String partOfSpeech;

    /** AI 建议的延伸词列表 */
    private List<ExtensionItem> extensions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionItem {
        private String word;
        private String translation;
        /** 前端勾选状态，默认 true */
        private boolean selected;
    }
}
