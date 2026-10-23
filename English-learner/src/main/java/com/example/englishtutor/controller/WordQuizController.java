package com.example.englishtutor.controller;

import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.OptionInfo;
import com.example.englishtutor.dto.QuizCheckDTO;
import com.example.englishtutor.dto.QuizWordDTO;
import com.example.englishtutor.dto.SpellWordDTO;
import com.example.englishtutor.entity.Vocabulary;
import com.example.englishtutor.entity.WordErrorRecord;
import com.example.englishtutor.service.VocabularyService;
import com.example.englishtutor.service.WordErrorRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单词测验控制器 — 两阶段测验 + 错题库复习
 *
 * 阶段一（看词选义）：给出单词，从4个选项中选正确意思
 * 阶段二（看义写词）：给出意思，默写单词
 * 错题库：记录错误，支持复习重测
 */
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class WordQuizController {

    private final VocabularyService vocabularyService;
    private final WordErrorRecordService errorService;

    /**
     * 开始一轮新测验 — 返回 N 个随机单词（含选项）
     */
    @PostMapping("/start")
    public R<Map<String, Object>> startQuiz(@RequestBody Map<String, Object> body) {
        int count = body.get("count") == null ? 10 : Integer.parseInt(body.get("count").toString());
        // 可选词库：教师在所选词库内测验；学生端不传则默认「学生词库」
        String bank = body.get("bank") == null ? null : body.get("bank").toString();
        if (count < 1 || count > 50) {
            throw new BizException("单词数量需在 1~50 之间");
        }

        // 获取该词库词汇，随机选取 count 个
        List<Vocabulary> allWords = vocabularyService.random((long) count * 3, bank); // 多取一些用于生成干扰项
        if (allWords.size() < Math.min(count, 2)) {
            return R.fail("词汇库不足，请联系老师添加更多单词");
        }

        // 随机选取 count 个作为考题
        Collections.shuffle(allWords);
        List<Vocabulary> quizWords = allWords.stream()
                .distinct()
                .limit(count)
                .collect(Collectors.toList());

        if (quizWords.size() < count) {
            return R.fail("词汇库不足，当前仅有 " + quizWords.size() + " 个单词");
        }

        // 构建带选项的测验单词列表
        List<QuizWordDTO> result = quizWords.stream().map(w -> {
            List<String> options = generateOptions(w, allWords);
            List<OptionInfo> optionDetails = buildOptionDetails(options, allWords);
            return QuizWordDTO.builder()
                    .id(w.getId())
                    .word(w.getWord())
                    .translation(w.getTranslation())
                    .partOfSpeech(w.getPartOfSpeech())
                    .extensions(w.getExtensions())
                    .options(options)
                    .optionDetails(optionDetails)
                    .build();
        }).collect(Collectors.toList());

        return R.ok(Map.of("words", result));
    }

    /**
     * 阶段二：获取拼写题列表（同一批单词，看意思写单词）
     */
    @PostMapping("/spell-words")
    public R<Map<String, Object>> getSpellWords(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("wordIds");
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BizException("请提供单词ID列表");
        }
        // Jackson 可能反序列化为 Integer，需要统一转 Long
        List<Long> wordIds = rawIds.stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());

        List<Vocabulary> words = vocabularyService.getByIds(wordIds);
        List<SpellWordDTO> result = words.stream().map(w -> SpellWordDTO.builder()
                .id(w.getId())
                .translation(w.getTranslation())
                .partOfSpeech(w.getPartOfSpeech())
                .correctWord(w.getWord())
                .build()).collect(Collectors.toList());

        // 随机打乱顺序
        Collections.shuffle(result);
        return R.ok(Map.of("words", result));
    }

    /**
     * 阶段二：检查拼写
     */
    @PostMapping("/check-spelling")
    public R<QuizCheckDTO> checkSpelling(@RequestBody Map<String, Object> body) {
        Long wordId = Long.valueOf(body.get("wordId").toString());
        String userInput = body.get("userInput").toString().trim();

        Vocabulary word = vocabularyService.getById(wordId);
        if (word == null) {
            throw new BizException("单词不存在");
        }

        // 忽略大小写、忽略首尾空格
        boolean correct = word.getWord().equalsIgnoreCase(userInput);

        QuizCheckDTO result = QuizCheckDTO.builder()
                .correct(correct)
                .correctAnswer(word.getWord())
                .extensions(correct ? word.getExtensions() : null)
                .errorHint(correct ? null : buildSpellingHint(word.getWord(), userInput))
                .build();

        // 答错 → 自动记录到错题库
        if (!correct) {
            Long studentId = AuthContext.userId();
            if (studentId != null) {
                errorService.recordError(
                        studentId, word.getId(), word.getWord(), word.getPhonetic(), word.getTranslation(),
                        word.getPartOfSpeech(), word.getExtensions(),
                        "spelling", userInput, word.getWord(), word.getBank()
                );
            }
        }

        return R.ok(result);
    }

    /**
     * 记录阶段一的错误（前端判断答错后调用）
     */
    @PostMapping("/record-error")
    public R<Map<String, Object>> recordError(@RequestBody Map<String, Object> body) {
        Long wordId = Long.valueOf(body.get("wordId").toString());
        String wrongAnswer = body.get("wrongAnswer") != null ? body.get("wrongAnswer").toString() : "";
        String errorType = body.getOrDefault("errorType", "meaning").toString();

        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }

        Vocabulary word = vocabularyService.getById(wordId);
        if (word == null) {
            throw new BizException("单词不存在");
        }

        errorService.recordError(
                studentId, word.getId(), word.getWord(), word.getPhonetic(), word.getTranslation(),
                word.getPartOfSpeech(), word.getExtensions(),
                errorType, wrongAnswer, word.getTranslation(), word.getBank()
        );

        return R.ok(Map.of("recorded", true));
    }

    // ==================== 错题库 ====================

    /**
     * 获取错题列表
     */
    @GetMapping("/errors")
    public R<Map<String, Object>> getErrors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String bank,
            @RequestParam(defaultValue = "error") String book) {
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }
        var p = errorService.getBookPaged(studentId, page, size, bank, book);
        return R.ok(Map.of(
                "records", p.getRecords(),
                "total", p.getTotal(),
                "errorCount", errorService.countBook(studentId, bank, "error"),
                "reviewCount", errorService.countBook(studentId, bank, "review"),
                "masteredCount", errorService.countBook(studentId, bank, "mastered")
        ));
    }

    /**
     * 将错词标记为已掌握：错题本 → 复习本
     */
    @PutMapping("/errors/{id}/mastered")
    public R<Void> markMastered(@PathVariable Long id) {
        Long studentId = requireLogin();
        errorService.markMastered(id, studentId);
        return R.ok(null);
    }

    /**
     * 复习本再次掌握：复习本 → 已掌握本
     */
    @PutMapping("/errors/{id}/review-mastered")
    public R<Void> reviewMastered(@PathVariable Long id) {
        Long studentId = requireLogin();
        errorService.reviewMastered(id, studentId);
        return R.ok(null);
    }

    /**
     * 忘了：从已掌握本退回错题本
     */
    @PutMapping("/errors/{id}/forget")
    public R<Void> forget(@PathVariable Long id) {
        Long studentId = requireLogin();
        errorService.forget(id, studentId);
        return R.ok(null);
    }

    /**
     * 按单词标记为已掌握（看词选意/默写两阶段都通过时调用）：不存在则新建，存在则移入已掌握本
     */
    @PostMapping("/errors/mark-mastered")
    public R<Void> markWordMastered(@RequestBody Map<String, Object> body) {
        Long userId = requireLogin();
        if (body.get("wordId") == null) throw new BizException("缺少单词ID");
        Long wordId = Long.valueOf(body.get("wordId").toString());
        Vocabulary word = vocabularyService.getById(wordId);
        if (word == null) throw new BizException("单词不存在");
        errorService.markWordMastered(userId, wordId, word.getWord(), word.getPhonetic(),
                word.getTranslation(), word.getPartOfSpeech(), word.getExtensions(), word.getBank());
        return R.ok(null);
    }

    private Long requireLogin() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        return userId;
    }

    /**
     * 开始错题复习 — 返回 N 个错词作为测验题
     */
    @PostMapping("/errors/review")
    public R<Map<String, Object>> reviewErrors(@RequestBody Map<String, Object> body) {
        int count = body.get("count") == null ? 10 : Integer.parseInt(body.get("count").toString());
        // 干扰项来源词库（可选）：教师错题复习时与所选词库保持一致
        String bank = body.get("bank") == null ? null : body.get("bank").toString();
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }

        List<WordErrorRecord> errors = errorService.randomErrors(studentId, count, bank);
        if (errors.isEmpty()) {
            return R.ok(Map.of("words", Collections.emptyList(), "total", 0));
        }

        // 获取错词对应的完整词汇信息（含延伸词）
        List<Long> wordIds = errors.stream().map(WordErrorRecord::getWordId).collect(Collectors.toList());
        List<Vocabulary> vocabList = vocabularyService.getByIds(wordIds);
        Map<Long, Vocabulary> vocabMap = vocabList.stream()
                .collect(Collectors.toMap(Vocabulary::getId, v -> v));

        // 获取一些额外词汇用于生成干扰项
        List<Vocabulary> allWords = vocabularyService.random(30L, bank);

        List<QuizWordDTO> result = errors.stream().map(e -> {
            Vocabulary v = vocabMap.get(e.getWordId());
            String translation = v != null ? v.getTranslation() : e.getTranslation();
            List<String> options = generateOptions(translation, allWords, e.getWord());
            // 确保当前单词的信息也能在选项详情中展示（把当前单词加入查找池）
            List<Vocabulary> poolWithCurrent = new ArrayList<>(allWords);
            if (v != null && poolWithCurrent.stream().noneMatch(p -> p.getId().equals(v.getId()))) {
                poolWithCurrent.add(v);
            }
            List<OptionInfo> optionDetails = buildOptionDetails(options, poolWithCurrent);
            return QuizWordDTO.builder()
                    .id(e.getWordId())
                    .word(e.getWord())
                    .translation(translation)
                    .partOfSpeech(v != null ? v.getPartOfSpeech() : null)
                    .extensions(v != null ? v.getExtensions() : null)
                    .options(options)
                    .optionDetails(optionDetails)
                    .build();
        }).collect(Collectors.toList());

        return R.ok(Map.of("words", result, "total", result.size()));
    }

    /**
     * 清零某词库的已掌握进度：全部已掌握单词退回错题本（状态=不熟）
     */
    @PostMapping("/errors/reset-mastered")
    public R<Map<String, Object>> resetMastered(@RequestParam(required = false) String bank) {
        Long userId = requireLogin();
        int n = errorService.resetMastered(userId, bank);
        return R.ok(Map.of("reset", n));
    }

    /**
     * 删除错题记录
     */
    @DeleteMapping("/errors/{id}")
    public R<Void> deleteError(@PathVariable Long id) {
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }
        errorService.deleteError(id, studentId);
        return R.ok(null);
    }

    /**
     * 获取错题统计
     */
    @GetMapping("/errors/stats")
    public R<Map<String, Object>> errorStats(@RequestParam(required = false) String bank) {
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }
        return R.ok(Map.of(
                "errorCount", errorService.countBook(studentId, bank, "error"),
                "reviewCount", errorService.countBook(studentId, bank, "review"),
                "masteredCount", errorService.countBook(studentId, bank, "mastered"),
                // 兼容旧字段：unreviewed=错题本，dueReview=复习本
                "unreviewedCount", errorService.countBook(studentId, bank, "error"),
                "dueReviewCount", errorService.countBook(studentId, bank, "review")
        ));
    }

    // ==================== 艾宾浩斯今日复习（三本模式后前端已不再使用，保留接口兼容） ====================

    /**
     * 获取今日到期需复习的错词（艾宾浩斯调度）
     */
    @GetMapping("/review/today")
    public R<Map<String, Object>> todayReview(@RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String bank) {
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }
        List<WordErrorRecord> records = errorService.getDueReviews(studentId, limit, bank);
        return R.ok(Map.of("records", records, "total", records.size()));
    }

    /**
     * 提交单条复习结果：记住 → 推进下一记忆阶段；没记住 → 重置回第一阶段，明天再复习
     */
    @PostMapping("/review/submit")
    public R<Map<String, Object>> submitReviewResult(@RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.userId();
        if (studentId == null) {
            throw new BizException("请先登录");
        }
        if (body.get("recordId") == null || body.get("correct") == null) {
            throw new BizException("参数不完整");
        }
        Long recordId = Long.valueOf(body.get("recordId").toString());
        boolean correct = Boolean.parseBoolean(body.get("correct").toString());
        errorService.submitReviewResult(recordId, studentId, correct);
        return R.ok(Map.of("submitted", true));
    }

    // ==================== 工具方法 ====================

    /** 为单词生成4个选项（含1个正确答案 + 3个干扰项） */
    private List<String> generateOptions(Vocabulary word, List<Vocabulary> pool) {
        return generateOptions(word.getTranslation(), pool, word.getWord());
    }

    private List<String> generateOptions(String correctTranslation, List<Vocabulary> pool, String excludeWord) {
        // 从词库中选取不同于当前单词的释义作为干扰项
        List<String> distractors = pool.stream()
                .filter(v -> !v.getWord().equals(excludeWord))
                .map(Vocabulary::getTranslation)
                .distinct()
                .filter(t -> !t.equals(correctTranslation))
                .collect(Collectors.toList());

        Collections.shuffle(distractors);
        List<String> options = new ArrayList<>();
        options.add(correctTranslation);
        distractors.stream().limit(3).forEach(options::add);

        // 确保有4个选项，不足则用通用干扰项补齐
        while (options.size() < 4) {
            String filler = getFillerOption(options);
            if (filler == null) break;
            options.add(filler);
        }

        Collections.shuffle(options);
        return options;
    }

    private String getFillerOption(List<String> existing) {
        String[] fillers = {"动词", "名词", "形容词", "副词", "介词", "连词"};
        for (String f : fillers) {
            if (!existing.contains(f)) return f;
        }
        return null;
    }

    /** 拼写错误提示：给出首字母 + 长度暗示 */
    private String buildSpellingHint(String correct, String userInput) {
        int len = correct.length();
        String firstLetter = correct.substring(0, 1);
        // 显示首字母 + 长度，如 "a______ (7 letters)"
        return "首字母: " + firstLetter + "，共 " + len + " 个字母。你写的是: " + userInput;
    }

    /** 为每个选项查找对应的源单词信息，构建 OptionInfo 列表 */
    private List<OptionInfo> buildOptionDetails(List<String> options, List<Vocabulary> pool) {
        return options.stream().map(opt -> {
            // 从词库中查找该选项文字对应的单词
            Vocabulary matched = pool.stream()
                    .filter(v -> v.getTranslation().equals(opt))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return OptionInfo.builder()
                        .optionText(opt)
                        .sourceWord(matched.getWord())
                        .sourceTranslation(matched.getTranslation())
                        .partOfSpeech(matched.getPartOfSpeech())
                        .extensions(matched.getExtensions())
                        .build();
            } else {
                // 通用填充项（如"动词""名词"等）
                return OptionInfo.builder()
                        .optionText(opt)
                        .sourceWord("—")
                        .sourceTranslation(opt)
                        .partOfSpeech("—")
                        .extensions(null)
                        .build();
            }
        }).collect(Collectors.toList());
    }
}
