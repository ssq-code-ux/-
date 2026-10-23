package com.example.englishtutor.service;

import java.util.List;
import java.util.Map;

/**
 * AI 通用对话服务 — 全局智能助手（支持 Function Calling）
 */
public interface AIChatService {

    /**
     * 发送消息给 AI，获取回复 + 可选操作指令
     *
     * @return Map: { "reply": "文本回复", "action": {...} }
     *         action 为 null 表示无需操作
     */
    Map<String, Object> chat(String message, List<Map<String, String>> history, Map<String, String> context);
}
