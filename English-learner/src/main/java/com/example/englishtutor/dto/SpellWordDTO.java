package com.example.englishtutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 阶段二拼写单词 — 看意思写单词
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpellWordDTO {
    private Long id;
    /** 中文意思（题干） */
    private String translation;
    /** 词性提示 */
    private String partOfSpeech;
    /** 正确答案（后端比对用，前端不应展示） */
    private String correctWord;
}
