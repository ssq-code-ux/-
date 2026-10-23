package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.AIExpandResultDTO;
import com.example.englishtutor.service.AIVocabularyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 智能词汇扩展接口
 * <p>
 * 输入一个单词 → AI 返回主词信息 + 延伸词建议 → 用户确认后通过词汇管理接口保存
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/vocabulary")
@RequiredArgsConstructor
public class AIVocabularyController {

    private final AIVocabularyService aiVocabularyService;

    /**
     * 🤖 AI分析单词
     * <p>
     * POST /api/ai/vocabulary/expand
     * Body: { "word": "happy" }
     * <p>
     * 返回：
     * {
     *   "word": "happy",
     *   "translation": "快乐的",
     *   "partOfSpeech": "形容词",
     *   "extensions": [
     *     {"word": "happiness", "translation": "幸福", "selected": true},
     *     {"word": "unhappy", "translation": "不快乐的", "selected": true}
     *   ]
     * }
     */
    @PostMapping("/expand")
    public R<AIExpandResultDTO> expand(@RequestBody Map<String, String> request) {
        String word = request.get("word");
        if (word == null || word.trim().isEmpty()) {
            return R.fail("请输入一个英文单词");
        }

        log.info("AI分析单词：{}", word);
        var result = aiVocabularyService.expand(word.trim());
        log.info("AI分析完成 — 主词:{}, 延伸词:{}个",
                result.getWord(), result.getExtensions().size());

        return R.ok(result);
    }
}
