package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.entity.*;
import com.example.englishtutor.mapper.*;
import com.example.englishtutor.service.AIAnswerSheetService;
import com.example.englishtutor.service.AnswerSheetService;
import com.example.englishtutor.service.NotifyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 答题卡作业实现
 * <p>
 * 结构分两层：大题（Section，承载完形填空/阅读的材料）→ 小题（Question，承载答案与判分）。
 * sectionType 只影响展示，判分只看小题的 questionType，两者互不干扰。
 * <p>
 * 判分分工：客观题（单选/多选/判断）和答案能对上的填空题走本地字符串比对，
 * 0 成本且结果确定；只有对不上的填空题和简答题才交给 AI 打分。
 * AI 不可用时这些题留 correct=null 由教师人工批改，不影响提交流程。
 */
@Slf4j
@Service
public class AnswerSheetServiceImpl implements AnswerSheetService {

    /** 本地就能判定对错的题型 */
    private static final Set<String> OBJECTIVE = Set.of("single", "multi", "judge");

    /** 大题类型 → 中文名，用于兜底标题和 AI 评分时的题型提示；solution 供数学等学科的解答题使用 */
    private static final Map<String, String> SECTION_LABEL = Map.of(
            "cloze", "完形填空",
            "reading", "阅读理解",
            "word_bank", "选词填空",
            "blank_fill", "盲填",
            "dialogue", "补全对话",
            "solution", "解答题",
            "writing", "书面表达",
            "standalone", "单句题");

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper submitMapper;
    private final HomeworkReviewMapper reviewMapper;
    private final HomeworkSectionMapper sectionMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final NotifyService notifyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** AI 判分服务（单体同进程直调，未注入时降级为人工批改） */
    @Autowired(required = false)
    private AIAnswerSheetService aiAnswerSheetService;

    public AnswerSheetServiceImpl(HomeworkMapper homeworkMapper, HomeworkSubmitMapper submitMapper,
                                  HomeworkReviewMapper reviewMapper, HomeworkSectionMapper sectionMapper,
                                  HomeworkQuestionMapper questionMapper, HomeworkAnswerMapper answerMapper,
                                  NotifyService notifyService) {
        this.homeworkMapper = homeworkMapper;
        this.submitMapper = submitMapper;
        this.reviewMapper = reviewMapper;
        this.sectionMapper = sectionMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.notifyService = notifyService;
    }

    // ==================== 布置 ====================

    @Override
    public Long assign(AnswerSheetDTO d) {
        if (!"teacher".equals(AuthContext.role()))
            throw new BizException("只有老师可以布置作业");
        int questionTotal = 0;
        if (d.getSections() != null) {
            for (SectionDTO sec : d.getSections()) {
                if (sec.getQuestions() != null) questionTotal += sec.getQuestions().size();
            }
        }
        if (questionTotal == 0) throw new BizException("答题卡至少要有一道题");

        Homework h = new Homework();
        h.setTeacherId(AuthContext.userId());
        h.setStudentId(d.getStudentId());
        h.setTitle(d.getTitle());
        h.setContent(d.getContent());
        h.setType("answer_sheet");
        // 学科随作业落库（空则不写，历史与英语口径由 AI 侧兜底）
        h.setSubject(d.getSubject() == null || d.getSubject().isBlank() ? null : d.getSubject().trim());
        h.setDeadline(d.getDeadline());
        h.setStatus("未提交");
        h.setCreateTime(LocalDateTime.now());
        homeworkMapper.insert(h);

        int sectionSeq = 1;
        int seq = 1;
        for (SectionDTO sec : d.getSections()) {
            if (sec.getQuestions() == null || sec.getQuestions().isEmpty()) continue;   // 空大题不落库

            HomeworkSection se = new HomeworkSection();
            se.setHomeworkId(h.getId());
            se.setSeq(sectionSeq++);
            se.setSectionType(isBlank(sec.getSectionType()) ? "standalone" : sec.getSectionType());
            se.setTitle(isBlank(sec.getTitle())
                    ? SECTION_LABEL.getOrDefault(se.getSectionType(), "题目") : sec.getTitle());
            se.setInstruction(sec.getInstruction());
            // 单句题没有共享材料，避免教师误粘一段文字进来
            se.setPassage("standalone".equals(se.getSectionType()) ? null : sec.getPassage());
            se.setWordBank(writeList(sec.getWordBank()));
            se.setSharedOptions(writeOptions(sec.getSharedOptions()));
            se.setCreateTime(LocalDateTime.now());
            sectionMapper.insert(se);

            for (QuestionDTO q : sec.getQuestions()) {
                HomeworkQuestion e = new HomeworkQuestion();
                e.setHomeworkId(h.getId());
                e.setSectionId(se.getId());
                e.setSeq(seq++);
                e.setQuestionType(q.getQuestionType());
                e.setContent(q.getContent());
                e.setOptions(writeOptions(q.getOptions()));
                e.setStandardAnswer(q.getStandardAnswer());
                e.setAnalysis(q.getAnalysis());
                e.setScore(q.getScore() != null && q.getScore() > 0 ? q.getScore() : 5);
                e.setKnowledgePoint(q.getKnowledgePoint());
                e.setAiGenerated(1);
                e.setConfirmed(1);   // 走到这一步说明教师已在向导里核验过
                e.setCreateTime(LocalDateTime.now());
                questionMapper.insert(e);
            }
        }

        notifyService.onHomeworkNotify(d.getStudentId(), h.getId(), h.getTitle(), "assigned");

        return h.getId();
    }

    // ==================== 拉题 ====================

    @Override
    public List<SectionDTO> sectionsForTeacher(Long homeworkId) {
        Homework h = homeworkMapper.selectById(homeworkId);
        if (h == null) throw new BizException("作业不存在");
        if (!h.getTeacherId().equals(AuthContext.userId()))
            throw new BizException("只能查看自己布置的作业");

        List<SectionDTO> list = new ArrayList<>();
        Map<Long, SectionDTO> byId = new LinkedHashMap<>();
        for (HomeworkSection e : sectionsOf(homeworkId)) {
            SectionDTO s = new SectionDTO();
            s.setId(e.getId());
            s.setSeq(e.getSeq());
            s.setSectionType(e.getSectionType());
            s.setTitle(e.getTitle());
            s.setInstruction(e.getInstruction());
            s.setPassage(e.getPassage());
            s.setWordBank(readList(e.getWordBank()));
            s.setSharedOptions(readOptions(e.getSharedOptions()));
            s.setQuestions(new ArrayList<>());
            byId.put(e.getId(), s);
            list.add(s);
        }

        SectionDTO legacy = null;
        for (HomeworkQuestion e : questionsOf(homeworkId)) {
            SectionDTO target = e.getSectionId() != null ? byId.get(e.getSectionId()) : null;
            if (target == null) {
                // 分层改造之前布置的答题卡，section_id 为空，归到一个虚拟「单句题」大题里
                if (legacy == null) {
                    legacy = new SectionDTO();
                    legacy.setSeq(list.size() + 1);
                    legacy.setSectionType("standalone");
                    legacy.setTitle("单句题");
                    legacy.setQuestions(new ArrayList<>());
                    list.add(legacy);
                }
                target = legacy;
            }
            QuestionDTO q = new QuestionDTO();
            q.setId(e.getId());
            q.setSeq(e.getSeq());
            q.setQuestionType(e.getQuestionType());
            q.setContent(e.getContent());
            q.setOptions(readOptions(e.getOptions()));
            q.setStandardAnswer(e.getStandardAnswer());
            q.setAnalysis(e.getAnalysis());
            q.setScore(e.getScore());
            q.setKnowledgePoint(e.getKnowledgePoint());
            target.getQuestions().add(q);
        }
        return list;
    }

    @Override
    public List<StudentSectionDTO> sectionsForStudent(Long homeworkId) {
        Homework h = homeworkMapper.selectById(homeworkId);
        if (h == null) throw new BizException("作业不存在");
        if (!h.getStudentId().equals(AuthContext.userId()))
            throw new BizException("只能查看自己的作业");

        List<StudentSectionDTO> list = new ArrayList<>();
        Map<Long, StudentSectionDTO> byId = new LinkedHashMap<>();
        for (HomeworkSection e : sectionsOf(homeworkId)) {
            StudentSectionDTO s = new StudentSectionDTO();
            s.setSeq(e.getSeq());
            s.setSectionType(e.getSectionType());
            s.setTitle(e.getTitle());
            s.setInstruction(e.getInstruction());
            s.setPassage(e.getPassage());
            s.setWordBank(readList(e.getWordBank()));
            s.setSharedOptions(readOptions(e.getSharedOptions()));
            s.setQuestions(new ArrayList<>());
            byId.put(e.getId(), s);
            list.add(s);
        }

        StudentSectionDTO legacy = null;
        for (HomeworkQuestion e : questionsOf(homeworkId)) {
            if (e.getConfirmed() == null || e.getConfirmed() != 1) continue; // 未核验的题不下发
            StudentSectionDTO target = e.getSectionId() != null ? byId.get(e.getSectionId()) : null;
            if (target == null) {
                if (legacy == null) {
                    legacy = new StudentSectionDTO();
                    legacy.setSeq(list.size() + 1);
                    legacy.setSectionType("standalone");
                    legacy.setTitle("单句题");
                    legacy.setQuestions(new ArrayList<>());
                    list.add(legacy);
                }
                target = legacy;
            }
            StudentQuestionDTO q = new StudentQuestionDTO();
            q.setId(e.getId());
            q.setSeq(e.getSeq());
            q.setQuestionType(e.getQuestionType());
            q.setContent(e.getContent());
            q.setOptions(readOptions(e.getOptions()));
            q.setScore(e.getScore());
            target.getQuestions().add(q);
        }
        // 大题里的题全被过滤掉（都没核验）就不要留空壳子给学生
        list.removeIf(s -> s.getQuestions().isEmpty());
        return list;
    }

    // ==================== 提交并判分 ====================

    @Override
    public Map<String, Object> submitSheet(AnswerSheetSubmitDTO d) {
        Homework h = homeworkMapper.selectById(d.getHomeworkId());
        if (h == null) throw new BizException("作业不存在");
        if (!h.getStudentId().equals(AuthContext.userId()))
            throw new BizException("只能提交自己的作业");
        if (!"answer_sheet".equals(h.getType()))
            throw new BizException("该作业不是答题卡形式");

        List<HomeworkQuestion> questions = questionsOf(h.getId());
        if (questions.isEmpty()) throw new BizException("答题卡没有题目");
        // 主观题交给 AI 时要告诉它这题属于哪类大题，否则“第 3 空”这种题干根本无法判断
        Map<Long, HomeworkSection> sectionOfQuestion =
                sectionByQuestion(sectionsWithLegacy(h.getId(), questions), questions);

        // 学生答案按题目ID索引
        Map<Long, String> submitted = new HashMap<>();
        if (d.getAnswers() != null) {
            for (AnswerSheetSubmitDTO.AnswerItem it : d.getAnswers()) {
                if (it.getQuestionId() != null) submitted.put(it.getQuestionId(), it.getAnswer());
            }
        }

        // 1. 落提交记录（textAnswer 存一份 JSON 快照，便于事后核对）
        HomeworkSubmit s = new HomeworkSubmit();
        s.setHomeworkId(h.getId());
        s.setStudentId(AuthContext.userId());
        s.setTextAnswer(writeJson(submitted));
        s.setStatus("已提交");
        s.setSubmitTime(LocalDateTime.now());
        submitMapper.insert(s);

        // 2. 本地判分，同时收集需要 AI 评分的题
        List<HomeworkAnswer> rows = new ArrayList<>();
        List<AiGradeDTO.GradeItem> aiItems = new ArrayList<>();
        int fullScore = 0;
        for (HomeworkQuestion q : questions) {
            int qScore = q.getScore() != null ? q.getScore() : 0;
            fullScore += qScore;
            String stuAns = submitted.get(q.getId());

            HomeworkAnswer row = new HomeworkAnswer();
            row.setSubmitId(s.getId());
            row.setQuestionId(q.getId());
            row.setStudentAnswer(stuAns);
            row.setTeacherFixed(0);
            row.setCreateTime(LocalDateTime.now());

            boolean objective = OBJECTIVE.contains(q.getQuestionType());
            boolean matched = matchAnswer(q.getQuestionType(), q.getStandardAnswer(), stuAns);
            // 填空题内容命中但书写不规范（典型如句首字母没大写）：
            // 不再本地直接给满分，交给 AI 按书写规范扣分，comment 才能告诉学生差在哪
            boolean handwritingOnly = matched && !objective
                    && !strictBlankMatch(q.getStandardAnswer(), stuAns);

            if ((objective || matched) && !handwritingOnly) {
                row.setCorrect(matched ? 1 : 0);
                row.setGotScore(matched ? qScore : 0);
            } else if (isBlank(stuAns)) {
                // 未作答的主观题不必浪费 AI 调用
                row.setCorrect(0);
                row.setGotScore(0);
                row.setAiComment("未作答");
            } else {
                row.setGotScore(0);   // 先占位，AI 返回后回填
                HomeworkSection sec = sectionOfQuestion.get(q.getId());
                AiGradeDTO.GradeItem item = new AiGradeDTO.GradeItem();
                item.setQuestionId(q.getId());
                item.setQuestionType(q.getQuestionType());
                item.setSectionType(sec != null ? sec.getSectionType() : null);
                item.setContent(gradeContext(sec, q.getContent()));
                item.setStandardAnswer(q.getStandardAnswer());
                item.setStudentAnswer(stuAns);
                item.setFullScore(qScore);
                aiItems.add(item);
            }
            rows.add(row);
        }

        // 3. 主观题交给 AI 批量评分，失败则降级为待人工批改
        boolean pendingAi = false;
        if (!aiItems.isEmpty()) {
            // 学科一并带给 AI，英语/数学/语文等按各自口径打分
            Map<Long, AiGradeResultDTO> aiResult = callAiGrade(aiItems, h.getSubject());
            if (aiResult == null) {
                pendingAi = true;
                for (HomeworkAnswer row : rows) {
                    if (row.getCorrect() == null) row.setAiComment("AI 暂时不可用，等待老师批改");
                }
            } else {
                Map<Long, Integer> fullScoreOf = new HashMap<>();
                for (AiGradeDTO.GradeItem it : aiItems) fullScoreOf.put(it.getQuestionId(), it.getFullScore());
                for (HomeworkAnswer row : rows) {
                    if (row.getCorrect() != null) continue;
                    AiGradeResultDTO r = aiResult.get(row.getQuestionId());
                    int full = fullScoreOf.getOrDefault(row.getQuestionId(), 0);
                    if (r == null) {
                        row.setAiComment("AI 未返回该题结果，等待老师批改");
                        pendingAi = true;
                        continue;
                    }
                    int got = r.getGotScore() != null ? Math.max(0, Math.min(full, r.getGotScore())) : 0;
                    row.setGotScore(got);
                    row.setCorrect(got >= full ? 1 : 0);   // 拿满分才算全对，部分分记为错题便于复习
                    row.setAiComment(r.getComment());
                }
            }
        }

        int gotSum = 0, rightCount = 0;
        for (HomeworkAnswer row : rows) {
            answerMapper.insert(row);
            gotSum += row.getGotScore() != null ? row.getGotScore() : 0;
            if (Integer.valueOf(1).equals(row.getCorrect())) rightCount++;
        }

        // 4. 汇总成一条批改记录，走原有"已批改"链路（学生+家长通知复用现成逻辑）
        int total100 = fullScore > 0 ? (int) Math.round(gotSum * 100.0 / fullScore) : 0;
        String comment = String.format("答题卡自动批改：%d/%d 分，%d/%d 题全对。",
                gotSum, fullScore, rightCount, questions.size())
                + (pendingAi ? "部分主观题待老师复核。" : "");

        HomeworkReview r = new HomeworkReview();
        r.setHomeworkId(h.getId());
        r.setSubmitId(s.getId());
        r.setTeacherId(h.getTeacherId());
        r.setScore(total100);
        r.setComment(comment);
        r.setNeedRedo("false");
        r.setReviewTime(LocalDateTime.now());
        reviewMapper.insert(r);

        s.setStatus("已批改");
        submitMapper.updateById(s);
        h.setStatus("已批改");
        homeworkMapper.updateById(h);

        notifyService.onHomeworkNotify(h.getStudentId(), h.getId(), h.getTitle(), "reviewed");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("submitId", s.getId());
        res.put("gotScore", gotSum);
        res.put("fullScore", fullScore);
        res.put("totalScore", total100);
        res.put("rightCount", rightCount);
        res.put("total", questions.size());
        res.put("pendingAi", pendingAi);
        return res;
    }

    // ==================== 结果 ====================

    @Override
    public Map<String, Object> result(Long submitId) {
        HomeworkSubmit s = submitMapper.selectById(submitId);
        if (s == null) throw new BizException("提交记录不存在");
        Homework h = homeworkMapper.selectById(s.getHomeworkId());
        Long me = AuthContext.userId();
        boolean isOwner = s.getStudentId().equals(me);
        boolean isTeacher = h != null && h.getTeacherId().equals(me);
        if (!isOwner && !isTeacher) throw new BizException("无权查看该批改结果");

        Map<Long, HomeworkAnswer> answers = new HashMap<>();
        for (HomeworkAnswer a : answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>().eq(HomeworkAnswer::getSubmitId, submitId))) {
            answers.put(a.getQuestionId(), a);
        }

        List<HomeworkQuestion> questions = questionsOf(s.getHomeworkId());
        List<HomeworkSection> sections = sectionsWithLegacy(s.getHomeworkId(), questions);
        Map<Long, HomeworkSection> sectionOfQuestion = sectionByQuestion(sections, questions);

        List<AnswerResultDTO> items = new ArrayList<>();
        int gotSum = 0, fullScore = 0, rightCount = 0;
        for (HomeworkQuestion q : questions) {
            HomeworkAnswer a = answers.get(q.getId());
            AnswerResultDTO it = new AnswerResultDTO();
            it.setQuestionId(q.getId());
            HomeworkSection sec = sectionOfQuestion.get(q.getId());
            if (sec != null) {
                it.setSectionSeq(sec.getSeq());
                it.setSectionType(sec.getSectionType());
                it.setSectionTitle(sec.getTitle());
            }
            it.setSeq(q.getSeq());
            it.setQuestionType(q.getQuestionType());
            it.setContent(q.getContent());
            it.setOptions(readOptions(q.getOptions()));
            it.setStandardAnswer(q.getStandardAnswer());
            it.setAnalysis(q.getAnalysis());
            it.setScore(q.getScore());
            it.setKnowledgePoint(q.getKnowledgePoint());
            if (a != null) {
                it.setStudentAnswer(a.getStudentAnswer());
                it.setCorrect(a.getCorrect());
                it.setGotScore(a.getGotScore());
                it.setAiComment(a.getAiComment());
                it.setTeacherFixed(a.getTeacherFixed());
                gotSum += a.getGotScore() != null ? a.getGotScore() : 0;
                if (Integer.valueOf(1).equals(a.getCorrect())) rightCount++;
            }
            fullScore += q.getScore() != null ? q.getScore() : 0;
            items.add(it);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("review", reviewMapper.selectOne(
                new LambdaQueryWrapper<HomeworkReview>()
                        .eq(HomeworkReview::getSubmitId, submitId)
                        .orderByDesc(HomeworkReview::getReviewTime)
                        .last("limit 1")));
        res.put("submitId", submitId);
        res.put("gotScore", gotSum);
        res.put("fullScore", fullScore);
        res.put("rightCount", rightCount);
        res.put("total", items.size());
        // 大题材料一并返回，学生看错题时还能回去重读那篇文章
        List<StudentSectionDTO> sectionMeta = new ArrayList<>();
        for (HomeworkSection e : sections) {
            StudentSectionDTO m = new StudentSectionDTO();
            m.setSeq(e.getSeq());
            m.setSectionType(e.getSectionType());
            m.setTitle(e.getTitle());
            m.setInstruction(e.getInstruction());
            m.setPassage(e.getPassage());
            m.setWordBank(readList(e.getWordBank()));
            m.setSharedOptions(readOptions(e.getSharedOptions()));
            sectionMeta.add(m);
        }
        res.put("sections", sectionMeta);
        res.put("items", items);
        return res;
    }

    // ==================== 教师复核 ====================

    @Override
    public void fix(AnswerFixDTO d) {
        HomeworkSubmit s = submitMapper.selectById(d.getSubmitId());
        if (s == null) throw new BizException("提交记录不存在");
        Homework h = homeworkMapper.selectById(s.getHomeworkId());
        if (h == null || !h.getTeacherId().equals(AuthContext.userId()))
            throw new BizException("只能复核自己学生的作业");

        List<HomeworkAnswer> rows = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>().eq(HomeworkAnswer::getSubmitId, d.getSubmitId()));
        Map<Long, HomeworkAnswer> byQuestion = new HashMap<>();
        for (HomeworkAnswer a : rows) byQuestion.put(a.getQuestionId(), a);

        Map<Long, Integer> fullScoreOf = new HashMap<>();
        for (HomeworkQuestion q : questionsOf(s.getHomeworkId())) {
            fullScoreOf.put(q.getId(), q.getScore() != null ? q.getScore() : 0);
        }

        if (d.getItems() != null) {
            for (AnswerFixDTO.FixItem f : d.getItems()) {
                HomeworkAnswer a = byQuestion.get(f.getQuestionId());
                if (a == null) continue;
                int full = fullScoreOf.getOrDefault(f.getQuestionId(), 0);
                if (f.getCorrect() != null) a.setCorrect(f.getCorrect());
                if (f.getGotScore() != null) a.setGotScore(Math.max(0, Math.min(full, f.getGotScore())));
                if (f.getComment() != null && !f.getComment().isBlank()) a.setAiComment(f.getComment());
                a.setTeacherFixed(1);
                answerMapper.updateById(a);
            }
        }

        // 重算总分并更新批改记录
        int gotSum = 0, fullScore = 0, rightCount = 0;
        for (HomeworkAnswer a : answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>().eq(HomeworkAnswer::getSubmitId, d.getSubmitId()))) {
            gotSum += a.getGotScore() != null ? a.getGotScore() : 0;
            if (Integer.valueOf(1).equals(a.getCorrect())) rightCount++;
        }
        for (Integer v : fullScoreOf.values()) fullScore += v;

        HomeworkReview r = reviewMapper.selectOne(
                new LambdaQueryWrapper<HomeworkReview>()
                        .eq(HomeworkReview::getSubmitId, d.getSubmitId())
                        .orderByDesc(HomeworkReview::getReviewTime)
                        .last("limit 1"));
        if (r == null) throw new BizException("批改记录不存在");
        r.setScore(fullScore > 0 ? (int) Math.round(gotSum * 100.0 / fullScore) : 0);
        r.setComment(d.getComment() != null && !d.getComment().isBlank()
                ? d.getComment()
                : String.format("老师已复核：%d/%d 分，%d 题全对。", gotSum, fullScore, rightCount));
        r.setTeacherId(AuthContext.userId());
        r.setReviewTime(LocalDateTime.now());
        reviewMapper.updateById(r);
    }

    // ==================== 内部工具 ====================

    private List<HomeworkQuestion> questionsOf(Long homeworkId) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<HomeworkQuestion>()
                        .eq(HomeworkQuestion::getHomeworkId, homeworkId)
                        .orderByAsc(HomeworkQuestion::getSeq));
    }

    private List<HomeworkSection> sectionsOf(Long homeworkId) {
        return sectionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSection>()
                        .eq(HomeworkSection::getHomeworkId, homeworkId)
                        .orderByAsc(HomeworkSection::getSeq));
    }

    /**
     * 大题列表，末尾按需补一个虚拟「单句题」大题
     * <p>
     * 分层改造之前布置的答题卡 section_id 为空，用一个 id=null 的内存大题兜住这些散题，
     * 这样判分和结果页不用关心数据是新是旧，也就不必回刷历史数据。
     */
    private List<HomeworkSection> sectionsWithLegacy(Long homeworkId, List<HomeworkQuestion> questions) {
        List<HomeworkSection> list = new ArrayList<>(sectionsOf(homeworkId));
        boolean hasLegacy = false;
        for (HomeworkQuestion q : questions) {
            if (q.getSectionId() == null) {
                hasLegacy = true;
                break;
            }
        }
        if (hasLegacy) {
            HomeworkSection legacy = new HomeworkSection();
            legacy.setHomeworkId(homeworkId);
            legacy.setSeq(list.size() + 1);
            legacy.setSectionType("standalone");
            legacy.setTitle("单句题");
            list.add(legacy);
        }
        return list;
    }

    /** 小题ID → 所属大题，找不到归属的落到虚拟大题 */
    private Map<Long, HomeworkSection> sectionByQuestion(List<HomeworkSection> sections,
                                                        List<HomeworkQuestion> questions) {
        Map<Long, HomeworkSection> byId = new HashMap<>();
        HomeworkSection legacy = null;
        for (HomeworkSection s : sections) {
            if (s.getId() != null) byId.put(s.getId(), s);
            else legacy = s;
        }
        Map<Long, HomeworkSection> map = new HashMap<>();
        for (HomeworkQuestion q : questions) {
            HomeworkSection s = q.getSectionId() != null ? byId.get(q.getSectionId()) : null;
            if (s == null) s = legacy;
            if (s != null) map.put(q.getId(), s);
        }
        return map;
    }

    /**
     * 给 AI 评分的题干补上大题信息
     * <p>
     * 分层后填空小题的题干只剩「第 3 空」，单独喂给 AI 无从判断，
     * 这里补上题型（选词填空再附上词库），比把整篇文章重复喂给每道题省得多。
     */
    private String gradeContext(HomeworkSection sec, String content) {
        if (sec == null || "standalone".equals(sec.getSectionType())) return content;
        StringBuilder sb = new StringBuilder("【")
                .append(SECTION_LABEL.getOrDefault(sec.getSectionType(), "题目"))
                .append("】")
                .append(content == null ? "" : content);
        List<String> bank = readList(sec.getWordBank());
        if (bank != null && !bank.isEmpty())
            sb.append("（备选词：").append(String.join("、", bank)).append("）");
        return sb.toString();
    }

    /**
     * 判定学生答案是否命中标准答案
     * <p>
     * 多选题忽略选项顺序；填空题忽略大小写、首尾空格与多余空格，
     * 标准答案用竖线分隔多种都算对的写法。
     */
    private boolean matchAnswer(String type, String standard, String student) {
        if (isBlank(standard) || isBlank(student)) return false;
        if ("multi".equals(type)) {
            return sortKeys(standard).equals(sortKeys(student));
        }
        if ("single".equals(type) || "judge".equals(type)) {
            return standard.trim().equalsIgnoreCase(student.trim());
        }
        String stu = normalize(student);
        for (String one : standard.split("\\|")) {
            if (normalize(one).equals(stu)) return true;
        }
        return false;
    }

    private String sortKeys(String s) {
        char[] cs = s.replaceAll("[^A-Za-z]", "").toUpperCase().toCharArray();
        Arrays.sort(cs);
        return new String(cs);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ").replaceAll("[.。!！?？,，;；]+$", "");
    }

    /**
     * 填空题的严格比对：只宽容空格与末尾标点，保留大小写。
     * 用来区分「内容全对」和「内容对但书写不规范」（如句首字母、专有名词没大写）——
     * 后者交 AI 扣分，而不是本地宽松比对直接给满分。
     */
    private boolean strictBlankMatch(String standard, String student) {
        if (isBlank(standard) || isBlank(student)) return false;
        String stu = strictKey(student);
        for (String one : standard.split("\\|")) {
            if (strictKey(one).equals(stu)) return true;
        }
        return false;
    }

    private String strictKey(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").replaceAll("[.。!！?？,，;；]+$", "");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 调 AI 评分，任何异常都返回 null 表示降级；subject 让 AI 按学科选评分口径 */
    private Map<Long, AiGradeResultDTO> callAiGrade(List<AiGradeDTO.GradeItem> items, String subject) {
        if (aiAnswerSheetService == null) {
            log.warn("[答题卡] AI 判分服务不可用，{} 道主观题留待人工批改", items.size());
            return null;
        }
        try {
            AiGradeDTO req = new AiGradeDTO();
            req.setSubject(subject);
            req.setItems(items);

            List<AiGradeResultDTO> resp = aiAnswerSheetService.grade(req);
            if (resp == null) return null;
            Map<Long, AiGradeResultDTO> map = new HashMap<>();
            for (AiGradeResultDTO r : resp) {
                if (r.getQuestionId() != null) map.put(r.getQuestionId(), r);
            }
            return map;
        } catch (Exception e) {
            log.warn("[答题卡] AI 评分失败，降级为人工批改：{}", e.getMessage());
            return null;
        }
    }

    private String writeOptions(List<QuestionDTO.OptionItem> options) {
        if (options == null || options.isEmpty()) return null;
        return writeJson(options);
    }

    private List<QuestionDTO.OptionItem> readOptions(String json) {
        if (isBlank(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionDTO.OptionItem>>() {});
        } catch (Exception e) {
            log.warn("[答题卡] 选项 JSON 解析失败：{}", json);
            return null;
        }
    }

    private String writeList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return writeJson(list);
    }

    private List<String> readList(String json) {
        if (isBlank(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[答题卡] 词库 JSON 解析失败：{}", json);
            return null;
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
