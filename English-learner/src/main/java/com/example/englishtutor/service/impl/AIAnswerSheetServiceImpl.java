package com.example.englishtutor.service.impl;

import com.example.englishtutor.dto.AiGradeDTO;
import com.example.englishtutor.dto.AiGradeResultDTO;
import com.example.englishtutor.dto.QuestionDTO;
import com.example.englishtutor.dto.SectionDTO;
import com.example.englishtutor.service.AIAnswerSheetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 答题卡 AI 服务实现
 * <p>
 * 出卷：教师粘贴题目文本 → AI 拆成「大题 + 小题」并补出标准答案/解析 → 返回给教师核验<br>
 * 评分：客观题由 homework 服务本地比对，只有主观题走这里，一次请求批量打分
 * <p>
 * 为什么要分大题：完形填空、阅读理解、选词填空、补全对话都是多个小题共享一段材料，
 * 若直接拆成独立小题，文章就丢了，学生看不到上下文无法作答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnswerSheetServiceImpl implements AIAnswerSheetService {

    /** 大题类型 → 中文名，用于 prompt 里的题型提示和兜底标题；solution 供数学/物理等学科的解答题使用 */
    private static final Map<String, String> SECTION_LABEL = Map.of(
            "cloze", "完形填空",
            "reading", "阅读理解",
            "word_bank", "选词填空",
            "blank_fill", "盲填",
            "dialogue", "补全对话",
            "solution", "解答题",
            "writing", "书面表达",
            "standalone", "单句题");

    /**
     * 学科未指定或为「英语」时走英语专用 prompt（题型集与评分规则都是英语专属），
     * 其余学科走多学科通用 prompt，保证历史英语行为完全不变。
     */
    private static boolean isEnglish(String subject) {
        return subject == null || subject.isBlank() || "英语".equals(subject.trim());
    }

    @Qualifier("deepseekRestClient")
    private final RestClient deepseekRestClient;

    /** AI 返回的字段可能比 DTO 多，遇到未知字段直接忽略而不是抛异常 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${ai.deepseek.model}")
    private String model;

    /** 图片识别出卷用的多模态模型，与文本模型走同一个 OpenAI 兼容接口，只是模型名不同 */
    @Value("${ai.deepseek.vision-model:qwen-vl-plus}")
    private String visionModel;

    /** Qwen3 的思考模式会先吐一大段思考过程再给答案，对出卷/判分这类 JSON 任务是纯开销，默认关掉 */
    @Value("${ai.deepseek.enable-thinking:false}")
    private boolean enableThinking;

    @Override
    public List<SectionDTO> parse(String rawText, Integer defaultScore, String sectionHint, String subject) {
        int score = (defaultScore == null || defaultScore <= 0) ? 5 : defaultScore;

        String prompt = isEnglish(subject)
                ? englishParsePrompt(rawText, score, sectionHint)
                : subjectParsePrompt(subject.trim(), rawText, score, sectionHint);

        // 材料原文要原样回传，输出量比旧版扁平结构大得多，max_tokens 给到上限
        String content = callAI(prompt, 8192, 0.2);
        try {
            List<SectionDTO> list = objectMapper.readValue(content, new TypeReference<List<SectionDTO>>() {});
            normalize(list, score);
            return list;
        } catch (Exception e) {
            log.error("解析 AI 出卷结果失败，原始内容: {}", content, e);
            throw new RuntimeException("AI 出卷结果解析失败，请调整题目文本后重试");
        }
    }

    /** 英语出卷 prompt：题型集是英语专属（完形/补全对话等），与历史行为完全一致 */
    private String englishParsePrompt(String rawText, int score, String sectionHint) {
        return """
            你是一名英语教师的出卷助手。请把老师提供的题目原文整理成「大题 + 小题」两层结构的答题卡。
            %s
            题目原文：
            ---
            %s
            ---

            【第一步：划分大题】
            把原文按大题（题组）切开，sectionType 只能取以下值之一：
              cloze      = 完形填空（一篇文章挖若干空，每空给四个选项）
              reading    = 阅读理解（一篇文章/图表，后面跟若干设问）
              word_bank  = 选词填空（给出备选词库，从中选词填入，可能需要变形）
              blank_fill = 盲填 / 首字母填空（不给备选词，直接填单词）
              dialogue   = 补全对话（对话原文挖空，可能给共用选项框如七选五）
              writing    = 书面表达 / 作文
              standalone = 单句题（互相独立的语法选择、单句填空等，没有共享材料）

            【第二步：大题字段】
            - passage 材料原文：完形填空/阅读/选词填空/补全对话的文章或对话必须原样照抄，
              不得改写、缩写、省略或翻译；文章里的空统一写成 _1_ _2_ _3_（下划线包住小题号），
              空的编号要和下面 questions 里的 seq 一一对应；
              writing 的 passage 放写作要求与提示词；standalone 的 passage 为 null
            - instruction 放答题要求那句话（如“从每题所给的四个选项中选出最佳选项”），没有就填 null
            - title 大题标题，如“完形填空”“阅读理解 A 篇”
            - wordBank 只有 word_bank 类型才填，是备选词数组如 ["make","careful"]；其他类型为 null
            - sharedOptions 只有整个大题共用一个选项框时才填（补全对话七选五），
              完形填空每个空的选项各不相同，属于小题自己的 options，不要放这里

            【第三步：小题字段】
            - questionType 决定怎么判分，只能取：single=单选 multi=多选 judge=判断 blank=填空 short=简答/写作
              完形填空的每个空 → single；阅读的设问 → single（判断正误用 judge）；
              选词填空/盲填的每个空 → blank；补全对话给了选项框 → single，没给 → blank；作文 → short
            - content 题干：阅读设问、单句题写完整题干；
              完形填空/选词填空/盲填这种“只是文章里的一个空”，content 写“第 N 空”即可，不要重复整篇文章
            - options 只有小题自己带选项时才填（完形填空、阅读设问），key 用大写 A/B/C/D；
              判断题固定 A=正确 B=错误；填空/简答为 null；已放进 sharedOptions 的不要重复
            - standardAnswer：选择题填选项 key（多选连写如 "AC"）；填空填答案文本，
              多种都算对的写法用竖线分隔如 "is|is not"；作文给参考要点。原文没给答案的你要自己解出正确答案
            - analysis 写一到两句中文解析；knowledgePoint 写知识点标签，不超过 10 个字
            - score 分值：原文标注了就用原文的；没标注时完形/阅读/填空每题用 %d 分，作文用 15 分
            - seq 小题号在整张答题卡内连续递增（第一个大题 1..15，第二个大题接着 16 开始）

            请严格返回 JSON 数组，不要任何其他文字、不要 markdown 代码块：
            [
              {
                "seq": 1,
                "sectionType": "cloze",
                "title": "完形填空",
                "instruction": "阅读下面的短文，从每小题所给的四个选项中选出最佳选项。",
                "passage": "Tom was a quiet boy. He _1_ to school by bike every day.",
                "wordBank": null,
                "sharedOptions": null,
                "questions": [
                  {
                    "seq": 1,
                    "questionType": "single",
                    "content": "第 1 空",
                    "options": [{"key":"A","text":"go"},{"key":"B","text":"goes"},{"key":"C","text":"went"},{"key":"D","text":"going"}],
                    "standardAnswer": "C",
                    "analysis": "全文为过去时叙述，用一般过去时 went。",
                    "score": 1,
                    "knowledgePoint": "一般过去时"
                  }
                ]
              }
            ]
            """.formatted(hintLine(sectionHint), rawText, score);
    }

    /** 多学科出卷 prompt：题型集通用化（材料题/解答题等），适配数学、语文、物理、化学等学科 */
    private String subjectParsePrompt(String subject, String rawText, int score, String sectionHint) {
        return """
            你是一名「%s」教师的出卷助手。请把老师提供的题目原文整理成「大题 + 小题」两层结构的答题卡。
            %s
            题目原文：
            ---
            %s
            ---

            【第一步：划分大题】
            把原文按大题（题组）切开，sectionType 只能取以下值之一：
              reading    = 阅读理解 / 材料题（一篇文章、图表、实验材料或史料，后面跟若干设问）
              word_bank  = 选词填空 / 选句填空（给出备选词句库，从中选出填入）
              blank_fill = 填空题（直接填写答案，不给备选项）
              solution   = 解答题 / 计算题 / 证明题（需要完整解题过程，按步骤给分）
              writing    = 作文 / 写作
              standalone = 独立小题（互相独立的选择、判断、填空等，没有共享材料）

            【第二步：大题字段】
            - passage 材料原文：阅读理解/材料题的文章、图表内容、实验过程或对话必须原样照抄，
              不得改写、缩写、省略或翻译；文章里的空统一写成 _1_ _2_ _3_（下划线包住小题号），
              空的编号要和下面 questions 里的 seq 一一对应；
              writing 的 passage 放写作要求与提示；solution / standalone 的 passage 为 null
            - instruction 放答题要求那句话（如“解答应写出必要的文字说明、证明过程或演算步骤”），没有就填 null
            - title 大题标题，如“现代文阅读”“解答题”“实验探究”
            - wordBank 只有 word_bank 类型才填，是备选词/句数组；其他类型为 null
            - sharedOptions 只有整个大题共用一个选项框时才填，其他情况为 null

            【第三步：小题字段】
            - questionType 决定怎么判分，只能取：single=单选 multi=多选 judge=判断 blank=填空 short=简答/解答/写作
              材料题的选择设问 → single（判断正误用 judge）；填空 → blank；
              解答题/计算题/证明题/简答题 → short；作文 → short
            - content 题干：完整写出每道小题的题干（解答题要含全部已知条件与求解目标），
              不要把题干丢在 passage 里；材料里“只是文章中的一个空”才写“第 N 空”
            - options 只有小题自己带选项时才填，key 用大写 A/B/C/D；
              判断题固定 A=正确 B=错误；填空/解答为 null；已放进 sharedOptions 的不要重复
            - standardAnswer：选择题填选项 key（多选连写如 "AC"）；填空填答案文本，
              数值答案要带单位、多种都算对的写法用竖线分隔；解答题/计算题给出关键步骤要点
              （每步用分号分隔）并以“答案：”开头写出最终结果；作文给参考要点。
              原文没给答案的你要自己解出正确答案
            - analysis 写一到两句中文解析；knowledgePoint 写知识点标签，不超过 10 个字
            - score 分值：原文标注了就用原文的；没标注时每题用 %d 分，作文用 15 分
            - seq 小题号在整张答题卡内连续递增（第一个大题 1..15，第二个大题接着 16 开始）

            请严格返回 JSON 数组，不要任何其他文字、不要 markdown 代码块：
            [
              {
                "seq": 1,
                "sectionType": "solution",
                "title": "解答题",
                "instruction": "解答应写出必要的文字说明、证明过程或演算步骤。",
                "passage": null,
                "wordBank": null,
                "sharedOptions": null,
                "questions": [
                  {
                    "seq": 1,
                    "questionType": "short",
                    "content": "解方程：2x + 6 = 20。",
                    "options": null,
                    "standardAnswer": "移项得 2x = 14；两边同除以 2；答案：x = 7",
                    "analysis": "移项合并同类项后，系数化为 1。",
                    "score": 5,
                    "knowledgePoint": "一元一次方程"
                  }
                ]
              }
            ]
            """.formatted(subject, hintLine(sectionHint), rawText, score);
    }

    @Override
    public List<SectionDTO> parseImage(byte[] image, String mimeType, Integer defaultScore, String sectionHint, String subject) {
        String rawText = recognizeImage(image, mimeType, subject);
        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("未能从图片中识别出题目，请换一张更清晰的图片重试");
        }
        log.info("图片识别出题目文本 {} 字，继续走文本出卷流程", rawText.length());
        // 转录结果直接交给既有出卷流程，后续核验/发布与粘贴文本完全一致
        return parse(rawText, defaultScore, sectionHint, subject);
    }

    /**
     * 多模态模型识别题目图片：只做忠实转录（OCR），不改写、不答题、不省略，
     * 转出来的文本质量越接近原文，后面的出卷解析就越准。
     */
    private String recognizeImage(byte[] image, String mimeType, String subject) {
        String type = (mimeType == null || mimeType.isBlank()) ? "image/jpeg" : mimeType;
        // OpenAI 兼容接口支持 base64 data URL，免去为识别专门传 OSS 的中转
        String dataUrl = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(image);

        String prompt = """
            请把图片里的%s题目完整、忠实地转录成文字，后续会交给出卷程序解析，要求：
            1. 原样照抄题干、文章、对话、选项、备选词库、答题要求，不改写、不翻译、不省略、不纠正错别字；
               图片里有的答案/解析也要一并转录出来并标注（如“参考答案：C”）
            2. 文章/对话里的空保持原样；若图中是横线或括号空，统一写成 _1_ _2_（编号按出现顺序递增）
            3. 选择题选项按 A. xxx  B. xxx 的格式逐行列出，不要漏选项
            4. 多个大题按原顺序全部输出，大题标题（如“二、完形填空”）与分值说明也要保留，大题之间空一行
            5. 只输出转录文本本身，不要 markdown 代码块、不要添加任何图片以外的内容或点评，
               看不清的字用 □ 代替，不要瞎猜、不要留空行省略
            """.formatted(isEnglish(subject) ? "英语" : subject.trim());

        Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
        Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
        Map<String, Object> userMessage = Map.of("role", "user", "content", List.of(textPart, imagePart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", visionModel);
        requestBody.put("max_tokens", 8192);
        // OCR 要的是确定性输出，温度尽量压低，避免模型自由发挥改词
        requestBody.put("temperature", 0.1);
        requestBody.put("enable_thinking", enableThinking);
        requestBody.put("messages", List.of(userMessage));

        try {
            String response = deepseekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            // 多模态接口返回的 content 可能是字符串，也可能是 [{type:"text",text:"..."}] 数组，两种都兼容
            String content = contentNode.isArray()
                    ? StreamSupport.stream(contentNode.spliterator(), false)
                            .filter(n -> "text".equals(n.path("type").asText()))
                            .map(n -> n.path("text").asText())
                            .collect(Collectors.joining("\n"))
                    : contentNode.asText();
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```[a-zA-Z]*\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }
            return content;
        } catch (Exception e) {
            log.error("调用多模态模型识别图片失败", e);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 教师指定了题型就写进 prompt 强约束，比让 AI 自己猜准得多。
     * 前端题型提示是自由输入：命中已知类型（cloze/reading/...）直接锁定 sectionType；
     * 自由文本（如“文言文阅读”“计算题”）只做语义提示，类型由 AI 归到最合适的枚举值。
     */
    private String hintLine(String sectionHint) {
        if (sectionHint == null || sectionHint.isBlank() || "auto".equals(sectionHint)) {
            return "";
        }
        String hint = sectionHint.trim();
        if (SECTION_LABEL.containsKey(hint)) {
            return "\n老师已明确这段内容是【" + SECTION_LABEL.get(hint) + "】（sectionType=" + hint
                    + "），请直接按这个类型解析，不要再归到其他类型。\n";
        }
        return "\n老师已明确这段内容是【" + hint
                + "】，请把它归到最合适的 sectionType，大题标题可直接用老师给的名称。\n";
    }


    /**
     * AI 偶尔会漏字段或把题号排乱，这里统一兜底，避免落库时 seq/score 为空。
     * 小题号按全卷重新连续编号，保证学生端的题号和文章里的 _N_ 对得上。
     */
    private void normalize(List<SectionDTO> list, int defaultScore) {
        int sectionSeq = 1;
        int questionSeq = 1;
        for (SectionDTO s : list) {
            s.setSeq(sectionSeq++);
            if (s.getSectionType() == null || s.getSectionType().isBlank()) {
                s.setSectionType("standalone");
            }
            if ("standalone".equals(s.getSectionType())) {
                s.setPassage(null);   // 单句题没有共享材料，AI 有时会把题干重复填进来
            }
            if (s.getTitle() == null || s.getTitle().isBlank()) {
                s.setTitle(SECTION_LABEL.getOrDefault(s.getSectionType(), "题目"));
            }
            if (s.getQuestions() == null) {
                s.setQuestions(new ArrayList<>());
            }
            for (QuestionDTO q : s.getQuestions()) {
                q.setSeq(questionSeq);
                if (q.getScore() == null || q.getScore() <= 0) {
                    q.setScore("writing".equals(s.getSectionType()) ? 15 : defaultScore);
                }
                if (q.getQuestionType() == null || q.getQuestionType().isBlank()) {
                    q.setQuestionType("short");
                }
                if (q.getContent() == null || q.getContent().isBlank()) {
                    q.setContent("第 " + questionSeq + " 空");
                }
                questionSeq++;
            }
        }
    }

    @Override
    public List<AiGradeResultDTO> grade(AiGradeDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        for (AiGradeDTO.GradeItem it : dto.getItems()) {
            sb.append("题目ID: ").append(it.getQuestionId()).append('\n')
              .append("题型: ").append(nvl(it.getQuestionType())).append('\n')
              .append("大题类型: ").append(nvl(it.getSectionType())).append('\n')
              .append("满分: ").append(it.getFullScore()).append('\n')
              .append("题干: ").append(nvl(it.getContent())).append('\n')
              .append("参考答案: ").append(nvl(it.getStandardAnswer())).append('\n')
              .append("学生答案: ").append(nvl(it.getStudentAnswer()).isEmpty() ? "（未作答）" : it.getStudentAnswer())
              .append("\n\n");
        }

        String prompt = isEnglish(dto.getSubject())
                ? englishGradePrompt(sb)
                : subjectGradePrompt(dto.getSubject().trim(), sb);

        String content = callAI(prompt, 2048, 0.3);
        try {
            return objectMapper.readValue(content, new TypeReference<List<AiGradeResultDTO>>() {});
        } catch (Exception e) {
            log.error("解析 AI 评分结果失败，原始内容: {}", content, e);
            throw new RuntimeException("AI 评分结果解析失败");
        }
    }

    /** 英语评分 prompt：书写规范（大小写/拼写）等英语专属规则，与历史行为完全一致 */
    private String englishGradePrompt(StringBuilder sb) {
        return """
            你是一名严谨但鼓励式教学的英语老师，请给下面这些主观题打分。

            %s
            评分规则按题型分两套，先看每题的「题型/大题类型」再选规则：

            【A. 填空/简答题】（题型 blank 或 short，且大题类型不是 writing）
            1. gotScore 必须是 0 到该题满分之间的整数，可以给部分分
            2. 内容与书写完全正确才给满分
            3. 内容对但有书写规范问题的，必须扣分，扣该题分的 20%%~50%%：
               - 句首字母未大写（如 What 写成 what）
               - 专有名词未大写（English、China、人名、I 等）
               - 明显拼写错误（apple 写成 appel）
               并在 comment 里明确指出问题，如「内容对，但句首字母要大写」
            4. 意思完全不对才给 0 分；未作答给 0 分且 comment 写"未作答"

            【B. 写作题】（大题类型 writing）
            1. 参考答案只是内容要点提示，不是唯一标准；学生用自己的话表达，切题有理即可
            2. 从四个维度综合给分：内容切题与要点覆盖、语法准确性、词汇与句式、结构连贯与书写规范
            3. 评分参考：切题、语法基本正确、表达通顺 → 70%%以上；语法错误较多或部分偏题 → 40%%~70%%；
               严重偏题、字数过少或大量语法错误 → 低于 40%%
            4. comment 要具体：先点出 1 个亮点（可引用学生写的词句），再指出 1~2 个最需改进的问题并举例

            通用要求：
            1. comment 用中文，50 字以内，先说对的地方再说要改什么；不要空着
            2. 每道题都必须返回一条结果，questionId 用上面给的题目ID原值

            请严格返回 JSON 数组，不要任何其他文字、不要 markdown 代码块：
            [
              {"questionId": 12, "gotScore": 4, "comment": "内容正确，但句首字母 W 未大写，注意书写规范。"}
            ]
            """.formatted(sb);
    }

    /** 多学科评分 prompt：填空/简答按内容与规范给分，解答题按步骤给部分分，作文按要点与表达给分 */
    private String subjectGradePrompt(String subject, StringBuilder sb) {
        return """
            你是一名严谨但鼓励式教学的「%s」老师，请给下面这些主观题打分。

            %s
            评分规则按题型分三套，先看每题的「题型/大题类型」再选规则：

            【A. 填空/简答题】（题型 blank 或 short，且大题类型不是 writing/solution）
            1. gotScore 必须是 0 到该题满分之间的整数，可以给部分分
            2. 答案内容与形式完全正确才给满分（数值、单位、符号、用词、别字都要检查）
            3. 内容基本对但有规范问题的，酌情扣该题分的 20%%~50%%，
               如漏写单位、符号不规范、错别字、语句不通顺，并在 comment 里明确指出问题
            4. 意思完全不对才给 0 分；未作答给 0 分且 comment 写"未作答"

            【B. 解答题/计算题/证明题】（大题类型 solution，或题型 short 且答案带解题过程）
            1. 按步骤给分：思路方法、公式运用、过程推演、最终结果各占一定比例，可以给部分分
            2. 仅最终结果正确但过程缺失或有错的，最多给该题满分的一半
            3. 前面出错但后续方法正确、代入无误的，正确部分照常给分（跟进给分）
            4. comment 指出丢分在哪一步、该怎么改

            【C. 写作题】（大题类型 writing）
            1. 参考答案只是内容要点提示，不是唯一标准；学生用自己的话表达，切题有理即可
            2. 从四个维度综合给分：内容切题与要点覆盖、表达准确性、结构条理、书写规范
            3. 评分参考：切题、表达基本准确、条理清楚 → 70%%以上；错误较多或部分偏题 → 40%%~70%%；
               严重偏题、字数过少 → 低于 40%%
            4. comment 要具体：先点出 1 个亮点，再指出 1~2 个最需改进的问题并举例

            通用要求：
            1. comment 用中文，50 字以内，先说对的地方再说要改什么；不要空着
            2. 每道题都必须返回一条结果，questionId 用上面给的题目ID原值

            请严格返回 JSON 数组，不要任何其他文字、不要 markdown 代码块：
            [
              {"questionId": 12, "gotScore": 3, "comment": "思路和公式都对，但第二步移项出错，最终结果不对。"}
            ]
            """.formatted(subject, sb);
    }


    /**
     * 调用大模型并取出回复正文，顺带去掉可能包裹的 markdown 代码块
     */
    private String callAI(String prompt, int maxTokens, double temperature) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("enable_thinking", enableThinking);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        requestBody.put("messages", List.of(userMessage));

        try {
            String response = deepseekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText().trim();

            if (content.startsWith("```")) {
                content = content.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }
            return content;
        } catch (Exception e) {
            log.error("调用 AI 接口失败", e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
