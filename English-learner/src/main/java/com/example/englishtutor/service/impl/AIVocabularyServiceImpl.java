package com.example.englishtutor.service.impl;

import com.example.englishtutor.dto.AIExpandResultDTO;
import com.example.englishtutor.service.AIVocabularyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * AI 词汇扩展服务实现
 * <p>
 * 用户输入一个单词 → DeepSeek 返回主词信息 + 延伸词建议 → 用户确认后保存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIVocabularyServiceImpl implements AIVocabularyService {

    @Qualifier("deepseekRestClient")
    private final RestClient deepseekRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.deepseek.model}")
    private String model;

    /** Qwen3 的思考模式会先吐一大段思考过程再给答案，词汇分析用不上还拖慢响应，默认关掉 */
    @Value("${ai.deepseek.enable-thinking:false}")
    private boolean enableThinking;

    @Override
    public AIExpandResultDTO expand(String word) {
        // 1. 调用 DeepSeek 分析单词
        Map<String, Object> aiResponse = callDeepSeekAnalyze(word);

        // 2. 构建返回结果
        @SuppressWarnings("unchecked")
        List<Map<String, String>> extList = (List<Map<String, String>>) aiResponse.get("extensions");

        List<AIExpandResultDTO.ExtensionItem> extensions = new ArrayList<>();
        if (extList != null) {
            for (Map<String, String> ext : extList) {
                extensions.add(AIExpandResultDTO.ExtensionItem.builder()
                        .word(ext.get("word"))
                        .translation(ext.get("translation"))
                        .selected(true)  // 默认全选
                        .build());
            }
        }

        return AIExpandResultDTO.builder()
                .word((String) aiResponse.get("word"))
                .translation((String) aiResponse.get("translation"))
                .partOfSpeech((String) aiResponse.get("partOfSpeech"))
                .extensions(extensions)
                .build();
    }

    /**
     * 调用 DeepSeek 分析单词，返回主词信息 + 延伸词列表
     */
    private Map<String, Object> callDeepSeekAnalyze(String word) {
        String prompt = """
            你是一个英语词汇学习助手。请分析以下单词：

            单词：%s

            请提供：
            1. 该单词的中文释义
            2. 词性（名词/动词/形容词/副词等）
            3. 延伸词列表（5-10个），包括：
               - 同根词（如 happy → happiness, happily）
               - 近义词（如 happy → glad, joyful）
               - 反义词（如 happy → sad, unhappy）
               - 常见搭配（如 happy → happy birthday）

            请严格按以下JSON格式返回，不要任何其他文字：
            {
              "word": "happy",
              "translation": "快乐的",
              "partOfSpeech": "形容词",
              "extensions": [
                {"word": "happiness", "translation": "幸福（名词）"},
                {"word": "unhappy", "translation": "不快乐的"},
                {"word": "happily", "translation": "快乐地"}
              ]
            }
            """.formatted(word);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 2048);
        requestBody.put("temperature", 0.3);
        requestBody.put("enable_thinking", enableThinking);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        try {
            String response = deepseekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.debug("DeepSeek 响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(content, Map.class);
            return result;

        } catch (Exception e) {
            log.error("调用 DeepSeek API 失败", e);
            throw new RuntimeException("AI 分析失败: " + e.getMessage(), e);
        }
    }
}
