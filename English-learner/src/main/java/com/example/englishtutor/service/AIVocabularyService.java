package com.example.englishtutor.service;

import com.example.englishtutor.dto.AIExpandResultDTO;

/**
 * AI 词汇扩展服务
 */
public interface AIVocabularyService {

    /**
     * AI 智能分析单词
     * <p>
     * 输入一个英文单词，AI 返回：
     * - 主词的中文释义和词性
     * - 建议的延伸词（近义词、反义词、同根词、搭配等）
     * <p>
     * 不直接入库，由用户确认后再保存。
     */
    AIExpandResultDTO expand(String word);
}
