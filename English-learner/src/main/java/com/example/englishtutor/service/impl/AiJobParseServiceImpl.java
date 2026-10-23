package com.example.englishtutor.service.impl;

import com.example.englishtutor.service.AiJobParseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家教信息批量解析实现。
 * <p>
 * 与 AIChatServiceImpl 同一套调用方式（deepseekRestClient + ```json 代码块解析），
 * 差别在于这是“结构化抽取”任务：temperature 调到 0.1 求稳，max_tokens 放大到 4096
 * 以容纳多条结果，并强制要求模型只输出 JSON、抽不到的字段留空而不是编造。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobParseServiceImpl implements AiJobParseService {

    /** 单次最多解析条数，防止管理端粘一整篇导致 token 爆掉 */
    private static final int MAX_JOBS = 30;

    /** 原文超过这个长度直接截断，避免打爆模型上下文 */
    private static final int MAX_TEXT_LEN = 12000;

    @Qualifier("deepseekRestClient")
    private final RestClient deepseekRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.deepseek.model}")
    private String model;

    /** 与聊天一致：思考模式会先吐一大段推理，结构化抽取用不上还拖慢响应 */
    @Value("${ai.deepseek.enable-thinking:false}")
    private boolean enableThinking;

    private static final String SYSTEM_PROMPT = """
            你是家教平台的订单录入助手。用户会粘贴一段文本，里面包含一条或多条家教招聘/同行合作信息。
            你的任务是把它们逐条拆分，并抽取成结构化字段。

            ## 输出格式（严格遵守）
            只输出一个 ```json 代码块，不要任何解释、前后缀或多余文字：
            ```json
            {"jobs":[
              {
                "subject":"科目，如 数学",
                "grade":"年级，如 初三；没有则空字符串",
                "province":"省份，如 河南省；没有则空字符串",
                "city":"城市，如 郑州市；没有则空字符串",
                "address":"详细地址；没有则空字符串",
                "studentGender":"学生性别，只能是 男/女/不限；无法判断填 不限",
                "expectGender":"对老师的性别要求，只能是 男/女/不限；没有则填 不限",
                "frequency":"上课频次，如 每周2次，每次2小时；没有则空字符串",
                "salary":时薪数字(元/小时)，没有则 0,
                "situation":"学生情况，如 基础较弱需补基础；没有则空字符串",
                "requirement":"对老师的要求；没有则空字符串",
                "raw":"该条对应的原文片段（原样摘抄，便于人工核对）"
              }
            ]}
            ```

            ## 规则
            1. 一段文本里有几条独立的家教信息就输出几条，禁止把多条合并成一条，也禁止把一条拆成多条。
            2. 除 salary 必须是数字外，其余字段一律是字符串。
            3. 抽不到的字段给空字符串（salary 给 0），**绝对不要编造地址、薪资、年级等信息**。
            4. 若原文是“元/月”“元/次”等非小时计价，请换算成时薪，并在 situation 里注明原始计价方式。
            5. 联系电话、微信号、QQ 号不要单独成字段（平台不需要），但保留在 raw 里即可。
            6. 最多输出 %d 条，超出的忽略。
            """.formatted(MAX_JOBS);

    @Override
    public List<Map<String, Object>> parseJobs(String text, Integer jobType) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待解析文本不能为空");
        }
        int type = jobType == null ? 1 : jobType;

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        messages.add(sys);
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        String clipped = text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;
        user.put("content", "请解析以下家教信息：\n\n" + clipped);
        messages.add(user);

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("temperature", 0.1);
        body.put("enable_thinking", enableThinking);
        body.put("messages", messages);

        String resp;
        try {
            resp = deepseekRestClient.post()
                    .uri("/v1/chat/completions").body(body).retrieve().body(String.class);
        } catch (Exception e) {
            log.error("[家教解析] AI 调用失败", e);
            throw new IllegalStateException("AI 服务调用失败：" + e.getMessage(), e);
        }

        String content;
        try {
            content = objectMapper.readTree(resp)
                    .path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("[家教解析] AI 响应解析失败：{}", resp, e);
            throw new IllegalStateException("AI 响应格式异常", e);
        }

        // 统一补齐 jobType、归一化性别与薪资，保证下游 fillFromBody 不会因类型问题炸掉
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> j : extractJobs(content)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobType", type);
            m.put("subject", strOf(j.get("subject")));
            m.put("grade", strOf(j.get("grade")));
            m.put("province", strOf(j.get("province")));
            m.put("city", strOf(j.get("city")));
            m.put("address", strOf(j.get("address")));
            m.put("studentGender", genderOf(j.get("studentGender")));
            m.put("expectGender", genderOf(j.get("expectGender")));
            m.put("frequency", strOf(j.get("frequency")));
            m.put("salary", intOf(j.get("salary")));
            m.put("situation", strOf(j.get("situation")));
            m.put("requirement", strOf(j.get("requirement")));
            m.put("raw", strOf(j.get("raw")));
            // 科目是发布必填项，AI 没抽到就没法发布，直接丢掉这条并留痕
            if (m.get("subject") == null) {
                log.warn("[家教解析] 跳过一条缺少科目的解析结果：{}", j);
                continue;
            }
            result.add(m);
        }
        log.info("[家教解析] 原文 {} 字 → 解析出 {} 条", clipped.length(), result.size());
        return result;
    }

    /** 遍历所有 ```json 代码块，取第一个含 jobs 数组的（模型偶尔会输出多个块） */
    private List<Map<String, Object>> extractJobs(String text) {
        if (text == null) return new ArrayList<>();
        int from = 0;
        while (true) {
            int start = text.indexOf("```json", from);
            if (start < 0) break;
            int contentStart = start + 7;
            int end = text.indexOf("```", contentStart);
            if (end < 0) break;
            List<Map<String, Object>> parsed = readJobs(text.substring(contentStart, end).trim());
            if (parsed != null) return parsed;
            from = end + 3;
        }
        // 退路：模型没按格式包代码块，直接把整段当 JSON 试一次
        List<Map<String, Object>> parsed = readJobs(text.trim());
        return parsed == null ? new ArrayList<>() : parsed;
    }

    /** 解析一段 JSON 文本并取出 jobs 数组；不含 jobs 或解析失败返回 null */
    private List<Map<String, Object>> readJobs(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            Object jobs = root.get("jobs");
            if (!(jobs instanceof List<?> list)) return null;
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> mm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> casted = (Map<String, Object>) mm;
                    out.add(casted);
                }
                if (out.size() >= MAX_JOBS) break;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String strOf(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 性别只允许 男/女/不限，模型抽出的“男生”“female”等一律归一化 */
    private String genderOf(Object v) {
        String s = strOf(v);
        if (s == null) return null;
        if (s.contains("男")) return "男";
        if (s.contains("女")) return "女";
        return "不限";
    }

    /** 薪资兜底：模型可能返回 "120元/小时" 这类字符串，剔掉非数字再取整；0 或负数视为未填 */
    private Integer intOf(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v).replaceAll("[^0-9.]", "");
            if (s.isBlank()) return null;
            int n = (int) Math.round(Double.parseDouble(s));
            return n <= 0 ? null : n;
        } catch (Exception e) {
            return null;
        }
    }
}
