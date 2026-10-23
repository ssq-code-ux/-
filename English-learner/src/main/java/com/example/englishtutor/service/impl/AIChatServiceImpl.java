package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.entity.*;
import com.example.englishtutor.mapper.*;
import com.example.englishtutor.service.AIChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * AI 通用对话服务（支持真正的 Function Calling）
 * <p>
 * AI 可以查询数据库获取实时信息，然后以自然语言回复用户。
 * 流程：用户消息 → AI判断 → 如需数据→执行tool→喂回AI→自然语言回复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatServiceImpl implements AIChatService {

    @Qualifier("deepseekRestClient")
    private final RestClient deepseekRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 注入 Mapper 用于数据查询
    private final StudentProfileMapper studentProfileMapper;
    private final UserMapper userMapper;
    private final HomeworkMapper homeworkMapper;
    private final VocabularyMapper vocabularyMapper;
    private final LearningResourceMapper learningResourceMapper;

    @Value("${ai.deepseek.model}")
    private String model;

    /** Qwen3 的思考模式会先吐一大段思考过程再给答案，聊天回复用不上还拖慢响应，默认关掉 */
    @Value("${ai.deepseek.enable-thinking:false}")
    private boolean enableThinking;

    private static final String SYSTEM_PROMPT = """
        你是"英语学习助手"系统的 AI 助手，能直接查询系统数据并执行操作。

        ## 核心能力
        你不仅能聊天，还能查询数据库中的实时信息（学生、作业、词汇等），
        并操控系统界面（跳转页面、打开对话框、预填表单）。

        ## 🔍 数据查询工具
        当用户问的问题需要查询数据时（如"XX有几份作业"、"词库有多少单词"），
        你必须使用工具查询，不要猜测或编造数据！

        可用工具（在回复中用 ```json 包含 tool_call）：

        ### countHomeworks — 统计某学生的作业情况
        ```json
        {"tool_call":{"name":"countHomeworks","params":{"studentName":"姓名"}}}
        ```

        ### listStudents — 列出学生及绑定状态
        ```json
        {"tool_call":{"name":"listStudents","params":{}}}
        ```

        ### countVocabulary — 统计词汇量
        ```json
        {"tool_call":{"name":"countVocabulary","params":{}}}
        ```

        ## 🎬 界面操作工具
        跳转+对话框+预填内容。prefill 是可选的，如果用户要求生成内容（如"添加感叹句资料"），
        你必须生成完整的标题、类型、内容，填入 prefill。

        ### 📍 可用页面清单（path 只能从这里选，禁止编造其他路径！）
        教师端：/teacher/students(学生管理) /teacher/homework(作业管理) /teacher/vocabulary(词汇管理) /teacher/resources(学习资料)
        学生端：/student/dashboard(学习看板) /student/homework(我的作业) /student/vocabulary(词汇学习) /student/flashcards(记忆卡片背单词) /student/resources(学习资料)
        ⚠️ 当前角色是学生时只能用 /student/* 路径，教师只能用 /teacher/* 路径！

        ### 添加资料（⭐ 生成内容预填）
        ```json
        {"action":{"type":"navigate","path":"/teacher/resources","openDialog":"resource","prefill":{"title":"资料标题","type":"grammar","content":"详细内容..."}}}
        ```

        ### 布置作业（⭐ 生成题目预填）
        ```json
        {"action":{"type":"navigate","path":"/teacher/homework","studentName":"姓名","openDialog":"homework","prefill":{"title":"作业标题","content":"作业内容..."}}}
        ```

        ### 添加单词（⭐ AI扩展）
        ```json
        {"action":{"type":"navigate","path":"/teacher/vocabulary","openDialog":"aiExpand","prefillWord":"单词"}}
        ```

        ### 查看学生作业
        ```json
        {"action":{"type":"navigate","path":"/teacher/homework","studentName":"姓名"}}
        ```

        ### 查看学生档案
        ```json
        {"action":{"type":"navigate","path":"/teacher/students","studentName":"姓名","openProfile":true}}
        ```

        ### 学生端搜索资料/词汇（⭐ 学生常用）
        学生说"查询资料中的XX"/"搜索XX资料"/"找一下XX" → 直接跳转+搜索：
        ```json
        {"action":{"type":"navigate","path":"/student/resources","searchKeyword":"关键词"}}
        ```
        学生说"查单词XX"/"搜索XX单词" → 跳转词汇学习+搜索：
        ```json
        {"action":{"type":"navigate","path":"/student/vocabulary","searchKeyword":"关键词"}}
        ```

        ### 学生端开始词汇测验
        学生说"我要做测验"/"开始测验"/"考考我" → 跳转词汇学习+打开测验：
        ```json
        {"action":{"type":"navigate","path":"/student/vocabulary","openDialog":"quiz"}}
        ```

        ### 学生端听写模式
        学生说"我要听写"/"听写单词"/"考听力拼写" → 跳转词汇学习+打开听写：
        ```json
        {"action":{"type":"navigate","path":"/student/vocabulary","openDialog":"dictation"}}
        ```

        ### 学生端背单词
        学生说"我要背单词"/"记单词" → {"action":{"type":"navigate","path":"/student/flashcards"}}
        （以上同样必须用 ```json 代码块输出）

        ### 页面跳转（通用）
        ```json
        {"action":{"type":"navigate","path":"/teacher/vocabulary","searchKeyword":"关键词"}}
        ```

        ## 🚨 铁律（违反将导致功能失效！）
        1. 用户说"跳转""搜索""打开""添加""查看XX页面"等操作请求 → **必须**在回复末尾输出 ```json action 代码块，**禁止只描述不执行**！
        2. 用户问"几份作业""词汇量"等数据查询 → **必须**用 tool_call 查询
        3. 纯知识问答（"什么是XX""怎样学XX"）→ 不需要 JSON
        4. tool_call 和 action 只能出现一个，优先 tool_call
        5. **绝对不要**说"页面将自动跳转"然后不给 action！给了 action 页面才会真的跳转！
        6. path 必须严格从"可用页面清单"中选择，且必须与当前角色匹配，否则跳转会被系统拒绝！

        ## 📝 回复模板
        错误❌："好的，页面将自动跳转并搜索"（没给 action，页面不会动）
        正确✅："好的～\n```json\n{\"action\":{\"type\":\"navigate\",\"path\":\"/student/resources\",\"searchKeyword\":\"词性\"}}\n```"
        """;

    @Override
    public Map<String, Object> chat(String message, List<Map<String, String>> history, Map<String, String> context) {
        try {
            // 第一轮：发送用户消息给 AI
            log.info("AI第一轮调用: message={}", message.length() > 50 ? message.substring(0, 50) + "..." : message);
            String firstResponse = callAI(message, history, context, null);
            log.debug("AI第一轮回复: {}", firstResponse.length() > 200 ? firstResponse.substring(0, 200) + "..." : firstResponse);

            // 解析是否有 tool_call
            Map<String, Object> toolCall = extractJsonBlock(firstResponse, "tool_call");
            Map<String, Object> action = extractJsonBlock(firstResponse, "action");

            if (toolCall != null) {
                log.info("AI请求工具: {}", toolCall.get("name"));
                // 执行工具查询，传入上下文以识别当前用户
                String toolResult = executeTool(toolCall, context);
                log.info("AI工具结果: {} → {} 字符", toolCall.get("name"), toolResult != null ? toolResult.length() : 0);

                // 第二轮：把查询结果喂给 AI，让它生成友好回复
                String finalReply = callAI(message, history, context, toolResult);

                // 第二轮可能也包含 action（AI 查完数据后想帮用户操作）
                Map<String, Object> finalAction = extractJsonBlock(finalReply, "action");
                String cleanReply = cleanJsonBlock(finalReply);

                Map<String, Object> result = new HashMap<>();
                result.put("reply", cleanReply);
                result.put("action", finalAction);
                return result;
            }

            // 没有 tool_call，直接返回
            String cleanReply = cleanJsonBlock(firstResponse);
            Map<String, Object> result = new HashMap<>();
            result.put("reply", cleanReply);
            result.put("action", action);
            return result;
        } catch (Exception e) {
            log.error("AI对话异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("reply", "抱歉，处理你的请求时出错了：" + e.getMessage());
            return result;
        }
    }

    // ==================== 工具执行 ====================

    @SuppressWarnings("unchecked")
    private String executeTool(Map<String, Object> toolCall, Map<String, String> ctx) {
        String name = (String) toolCall.get("name");
        Map<String, Object> params = (Map<String, Object>) toolCall.getOrDefault("params", Map.of());

        try {
            return switch (name) {
                case "countHomeworks" -> execCountHomeworks(params, ctx);
                case "listStudents" -> execListStudents();
                case "countVocabulary" -> execCountVocabulary();
                default -> "未知工具: " + name;
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}", name, e);
            return "查询失败: " + e.getMessage();
        }
    }

    private String execCountHomeworks(Map<String, Object> params, Map<String, String> ctx) {
        String studentName = (String) params.get("studentName");
        // 如果没指定姓名，且当前用户是学生，就用当前用户
        if ((studentName == null || studentName.isEmpty()) && ctx != null && "student".equals(ctx.get("role"))) {
            studentName = ctx.get("userName");
        }
        if (studentName == null || studentName.isEmpty()) return "请指定学生姓名";
        // 查找学生
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRealName, studentName)
                .eq(User::getRole, "student"));
        if (user == null) {
            // 模糊匹配
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .like(User::getRealName, studentName)
                    .eq(User::getRole, "student"));
            if (users.isEmpty()) return "未找到学生: " + studentName;
            user = users.get(0);
        }
        // 统计作业
        List<Homework> homeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getStudentId, user.getId()));
        long total = homeworks.size();
        long submitted = homeworks.stream().filter(h -> "已提交".equals(h.getStatus()) || "已批改".equals(h.getStatus())).count();
        long reviewed = homeworks.stream().filter(h -> "已批改".equals(h.getStatus())).count();
        long overdue = homeworks.stream().filter(h -> "未提交".equals(h.getStatus())).count();

        return String.format("学生:%s(ID:%d), 作业总数:%d, 已提交:%d, 已批改:%d, 未提交:%d, 各作业详情:%s",
                user.getRealName(), user.getId(), total, submitted, reviewed, overdue,
                homeworks.stream().map(h -> String.format("[%s 状态:%s 截止:%s]",
                        h.getTitle(), h.getStatus(), h.getDeadline() != null ? h.getDeadline().toString() : "无"))
                        .collect(Collectors.joining(",")));
    }

    private String execListStudents() {
        List<User> students = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "student"));
        return students.stream()
                .map(s -> {
                    StudentProfile sp = studentProfileMapper.selectById(s.getId());
                    String teacher = "未绑定";
                    if (sp != null && sp.getTeacherId() != null) {
                        User t = userMapper.selectById(sp.getTeacherId());
                        teacher = t != null ? t.getRealName() : "未知";
                    }
                    return String.format("学生:%s(ID:%d) 老师:%s", s.getRealName(), s.getId(), teacher);
                })
                .collect(Collectors.joining("\n"));
    }

    private String execCountVocabulary() {
        long count = vocabularyMapper.selectCount(null);
        return "词库共有 " + count + " 个单词";
    }

    // ==================== AI 调用 ====================

    private String callAI(String userMessage, List<Map<String, String>> history,
                          Map<String, String> context, String toolResult) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 系统提示
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", buildContext(SYSTEM_PROMPT, context));
        messages.add(sysMsg);

        // 历史对话
        if (history != null) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> h = history.get(i);
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", h.get("role"));
                msg.put("content", h.get("content"));
                messages.add(msg);
            }
        }

        // 当前消息
        if (toolResult != null) {
            // 第二轮：告诉 AI 这是查询结果，请生成友好回复
            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "user");
            toolMsg.put("content", "以下是查询结果，请根据这些数据以友好、鼓励的语气回复用户（不要返回任何JSON）：\n\n" + toolResult);
            messages.add(toolMsg);
        } else {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
        }

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("temperature", 0.3);
        body.put("enable_thinking", enableThinking);
        body.put("messages", messages);

        try {
            String resp = deepseekRestClient.post()
                    .uri("/v1/chat/completions").body(body).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("AI调用失败", e);
            return "抱歉，我暂时无法处理，请稍后重试 😅";
        }
    }

    // ==================== JSON 解析工具 ====================

    private Map<String, Object> extractJsonBlock(String text, String key) {
        int start = text.indexOf("```json");
        if (start < 0) return null;
        int contentStart = text.indexOf("\n", start) + 1;
        int end = text.indexOf("```", contentStart);
        if (contentStart <= 0 || end <= contentStart) return null;
        try {
            Map<String, Object> json = objectMapper.readValue(
                    text.substring(contentStart, end).trim(),
                    new TypeReference<Map<String, Object>>() {});
            if (json.containsKey(key)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) json.get(key);
                return result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanJsonBlock(String text) {
        int start = text.indexOf("```json");
        if (start < 0) return text;
        int end = text.indexOf("```", start + 7);
        if (end < 0) return text;
        return (text.substring(0, start) + text.substring(end + 3)).trim();
    }

    private String buildContext(String prompt, Map<String, String> ctx) {
        if (ctx == null) return prompt;
        StringBuilder sb = new StringBuilder(prompt);
        String role = ctx.get("role");
        String page = ctx.get("page");
        if (role != null) sb.append("\n当前角色:").append("teacher".equals(role) ? "教师" : "学生");
        if (page != null) sb.append(" 页面:").append(page);
        return sb.toString();
    }
}
