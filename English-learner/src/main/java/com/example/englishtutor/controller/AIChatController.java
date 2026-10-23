package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.AIChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 通用对话接口 — 全局智能助手（支持操作执行）
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;

    /**
     * 💬 AI 对话
     * POST /api/ai/chat
     * Body: { message, history, context }
     * 返回: { reply, action? }
     */
    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return R.fail("请输入消息");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) request.get("history");

        @SuppressWarnings("unchecked")
        Map<String, String> context = (Map<String, String>) request.get("context");

        log.info("AI对话 — 角色:{}, 页面:{}, 消息:{}",
                context != null ? context.get("role") : "?",
                context != null ? context.get("page") : "?",
                message.length() > 60 ? message.substring(0, 60) + "..." : message);

        Map<String, Object> result = aiChatService.chat(message.trim(), history, context);

        Object action = result.get("action");
        if (action != null) {
            log.info("AI动作: {}", action);
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("reply", result.get("reply"));
        data.put("action", action);  // action 可以为 null，HashMap 允许

        return R.ok(data);
    }
}
