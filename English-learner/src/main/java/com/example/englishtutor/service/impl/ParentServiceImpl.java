package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.AiContentCache;
import com.example.englishtutor.entity.Homework;
import com.example.englishtutor.entity.HomeworkReview;
import com.example.englishtutor.entity.HomeworkSubmit;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.entity.WordErrorRecord;
import com.example.englishtutor.mapper.AiContentCacheMapper;
import com.example.englishtutor.mapper.HomeworkMapper;
import com.example.englishtutor.mapper.HomeworkReviewMapper;
import com.example.englishtutor.mapper.HomeworkSubmitMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.mapper.WordErrorRecordMapper;
import com.example.englishtutor.service.AIChatService;
import com.example.englishtutor.service.ParentService;
import com.example.englishtutor.service.StudentService;
import com.example.englishtutor.vo.StudentAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentServiceImpl implements ParentService {

    /** AI 内容类型：每日学习建议（一天一份） */
    public static final String TYPE_DAILY = "daily_advice";
    /** AI 内容类型：周报总结（一周一份） */
    public static final String TYPE_WEEKLY = "weekly_summary";

    private final UserMapper userMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper submitMapper;
    private final HomeworkReviewMapper reviewMapper;
    private final WordErrorRecordMapper wordErrorMapper;
    private final NotificationMapper notificationMapper;
    private final StudentService studentService;
    private final AiContentCacheMapper aiCacheMapper;

    /** AI 对话服务（单体同进程直调） */
    @Autowired(required = false)
    private AIChatService aiChatService;

    @Override
    public List<User> myChildren() {
        assertParent();
        List<User> children = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student")
                .eq(User::getParentId, AuthContext.userId()));
        children.forEach(u -> u.setPassword(null));
        return children;
    }

    @Override
    public void bindChild(String childUsername) {
        assertParent();
        if (childUsername == null || childUsername.isBlank()) {
            throw new BizException("请输入孩子的学生账号");
        }
        User child = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, childUsername.trim()));
        if (child == null || !"student".equals(child.getRole())) {
            throw new BizException("该学生账号不存在，请核对孩子的登录账号");
        }
        if (child.getParentId() != null && !child.getParentId().equals(AuthContext.userId())) {
            throw new BizException("该学生已绑定其他家长");
        }
        child.setParentId(AuthContext.userId());
        userMapper.updateById(child);
    }

    @Override
    public StudentAnalysisVO childAnalysis(Long childId) {
        assertMyChild(childId);
        return studentService.analysis(childId);
    }

    @Override
    public List<Map<String, Object>> childHomeworks(Long childId) {
        assertMyChild(childId);
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStudentId, childId)
                .orderByDesc(Homework::getCreateTime)
                .last("LIMIT 10"));
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework h : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("title", h.getTitle());
            m.put("type", h.getType());
            m.put("status", h.getStatus());
            m.put("deadline", h.getDeadline());
            boolean overdue = ("未提交".equals(h.getStatus()) || "需重做".equals(h.getStatus()))
                    && h.getDeadline() != null && h.getDeadline().isBefore(now);
            m.put("overdue", overdue);
            result.add(m);
        }
        return result;
    }

    @Override
    public Map<String, Object> childWeeklyReport(Long childId) {
        assertMyChild(childId);
        return weeklyReportOf(childId);
    }

    /** 最近 7 天聚合（无权限校验）：家长接口与 AI 周报预生成都走这里 */
    private Map<String, Object> weeklyReportOf(Long childId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6); // 最近 7 天（含今天）
        LocalDateTime weekStartTime = weekStart.atStartOfDay();
        LocalDateTime lastWeekStartTime = weekStartTime.minusDays(7);
        LocalDateTime now = LocalDateTime.now();

        // —— 作业维度：本周新布置 / 待完成 / 逾期 ——
        List<Homework> homeworks = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStudentId, childId));
        long newHomeworks = homeworks.stream()
                .filter(h -> h.getCreateTime() != null && h.getCreateTime().isAfter(weekStartTime)).count();
        long pendingHomeworks = homeworks.stream()
                .filter(h -> "未提交".equals(h.getStatus()) || "需重做".equals(h.getStatus())).count();
        long overdueHomeworks = homeworks.stream()
                .filter(h -> ("未提交".equals(h.getStatus()) || "需重做".equals(h.getStatus()))
                        && h.getDeadline() != null && h.getDeadline().isBefore(now)).count();

        // —— 提交维度：本周提交次数 + 学习活跃天数（有提交的日期去重）——
        List<HomeworkSubmit> submits = submitMapper.selectList(new LambdaQueryWrapper<HomeworkSubmit>()
                .eq(HomeworkSubmit::getStudentId, childId)
                .ge(HomeworkSubmit::getSubmitTime, weekStartTime));
        long activeDays = submits.stream()
                .filter(s -> s.getSubmitTime() != null)
                .map(s -> s.getSubmitTime().toLocalDate()).distinct().count();

        // —— 批改维度：本周批改数与平均分，与上周平均分对比 ——
        List<Long> hwIds = homeworks.stream().map(Homework::getId).toList();
        List<HomeworkReview> reviews = hwIds.isEmpty() ? List.of()
                : reviewMapper.selectList(new LambdaQueryWrapper<HomeworkReview>()
                        .in(HomeworkReview::getHomeworkId, hwIds));
        List<HomeworkReview> thisWeekReviews = reviews.stream()
                .filter(r -> r.getReviewTime() != null && r.getReviewTime().isAfter(weekStartTime)).toList();
        double avgScore = thisWeekReviews.stream().filter(r -> r.getScore() != null)
                .mapToInt(HomeworkReview::getScore).average().orElse(0);
        double avgScoreLast = reviews.stream()
                .filter(r -> r.getReviewTime() != null && r.getScore() != null
                        && r.getReviewTime().isAfter(lastWeekStartTime) && r.getReviewTime().isBefore(weekStartTime))
                .mapToInt(HomeworkReview::getScore).average().orElse(0);

        // —— 单词测验维度：本周新增错词 / 已掌握错词 ——
        long newWordErrors = wordErrorMapper.selectCount(new LambdaQueryWrapper<WordErrorRecord>()
                .eq(WordErrorRecord::getStudentId, childId)
                .ge(WordErrorRecord::getCreateTime, weekStartTime));
        long masteredWords = wordErrorMapper.selectCount(new LambdaQueryWrapper<WordErrorRecord>()
                .eq(WordErrorRecord::getStudentId, childId)
                .eq(WordErrorRecord::getReviewed, 1)
                .ge(WordErrorRecord::getLastReviewTime, weekStartTime));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rangeStart", weekStart.toString());
        result.put("rangeEnd", today.toString());
        result.put("newHomeworks", newHomeworks);
        result.put("submitCount", submits.size());
        result.put("reviewedCount", thisWeekReviews.size());
        result.put("avgScore", Math.round(avgScore * 10) / 10.0);
        result.put("avgScoreLast", Math.round(avgScoreLast * 10) / 10.0);
        result.put("pendingHomeworks", pendingHomeworks);
        result.put("overdueHomeworks", overdueHomeworks);
        result.put("newWordErrors", newWordErrors);
        result.put("masteredWords", masteredWords);
        result.put("activeDays", activeDays);
        return result;
    }

    @Override
    public Map<String, Object> childHomeworkDetail(Long childId, Long homeworkId) {
        assertMyChild(childId);
        Homework h = homeworkMapper.selectById(homeworkId);
        if (h == null || !Objects.equals(h.getStudentId(), childId)) {
            throw new BizException("作业不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("title", h.getTitle());
        m.put("content", h.getContent());
        m.put("type", h.getType());
        m.put("attachmentUrl", h.getAttachmentUrl());
        m.put("status", h.getStatus());
        m.put("deadline", h.getDeadline());
        m.put("createTime", h.getCreateTime());
        boolean overdue = ("未提交".equals(h.getStatus()) || "需重做".equals(h.getStatus()))
                && h.getDeadline() != null && h.getDeadline().isBefore(LocalDateTime.now());
        m.put("overdue", overdue);
        // 最近一次提交（含文字答案与附件）
        List<HomeworkSubmit> submits = submitMapper.selectList(new LambdaQueryWrapper<HomeworkSubmit>()
                .eq(HomeworkSubmit::getHomeworkId, homeworkId)
                .orderByDesc(HomeworkSubmit::getSubmitTime));
        if (!submits.isEmpty()) {
            HomeworkSubmit s = submits.get(0);
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("textAnswer", s.getTextAnswer());
            sm.put("fileUrl", s.getFileUrl());
            sm.put("status", s.getStatus());
            sm.put("submitTime", s.getSubmitTime());
            m.put("submit", sm);
            HomeworkReview review = reviewMapper.selectOne(new LambdaQueryWrapper<HomeworkReview>()
                    .eq(HomeworkReview::getSubmitId, s.getId())
                    .orderByDesc(HomeworkReview::getReviewTime)
                    .last("LIMIT 1"));
            if (review != null) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("score", review.getScore());
                rm.put("comment", review.getComment());
                rm.put("needRedo", review.getNeedRedo());
                rm.put("reviewTime", review.getReviewTime());
                m.put("review", rm);
            }
        }
        return m;
    }

    // ==================== AI 内容（学习建议 / 周报总结）====================

    @Override
    public Map<String, Object> childAiContent(Long childId, String type) {
        assertMyChild(childId);
        checkAiType(type);
        AiContentCache cache = aiCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getStudentId, childId)
                .eq(AiContentCache::getContentType, type)
                .eq(AiContentCache::getPeriodKey, periodKeyOf(type))
                .last("LIMIT 1"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", cache != null ? cache.getContent() : null);
        m.put("genTime", cache != null ? cache.getCreateTime() : null);
        return m;
    }

    @Override
    public Map<String, Object> refreshChildAiContent(Long childId, String type) {
        assertMyChild(childId);
        checkAiType(type);
        User child = userMapper.selectById(childId);
        String content = generateAiContent(child, type);
        AiContentCache saved = upsertCache(childId, type, content);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", saved.getContent());
        m.put("genTime", saved.getCreateTime());
        return m;
    }

    @Override
    public String pregenerateAiContent(Long studentId, String type) {
        checkAiType(type);
        User child = userMapper.selectById(studentId);
        if (child == null || !"student".equals(child.getRole())) {
            throw new BizException("学生不存在：" + studentId);
        }
        String content = generateAiContent(child, type);
        upsertCache(studentId, type, content);
        return content;
    }

    @Override
    public Map<String, Object> weeklyReportData(Long studentId) {
        return weeklyReportOf(studentId);
    }

    /**
     * 生成孩子指定类型的 AI 内容（家长手动刷新与定时预生成共用，不做权限校验）。
     * AI 不可用时抛 BizException，由调用方决定降级方式。
     */
    private String generateAiContent(User child, String type) {
        if (aiChatService == null) {
            throw new BizException("AI 服务暂未接入，请稍后再试");
        }
        String prompt = TYPE_WEEKLY.equals(type)
                ? buildWeeklySummaryPrompt(child)
                : buildDailyAdvicePrompt(child);
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("role", "parent");
        ctx.put("page", "/parent");
        ctx.put("userName", childName(child));
        try {
            Map<String, Object> resp = aiChatService.chat(prompt, List.of(), ctx);
            Object reply = resp != null ? resp.get("reply") : null;
            if (reply == null || reply.toString().isBlank()) {
                throw new BizException("AI 没有返回内容，请稍后重试");
            }
            return reply.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[家长端AI] 生成失败 type={} studentId={}：{}", type, child.getId(), e.getMessage());
            throw new BizException("AI 服务暂时不可用，请稍后重试");
        }
    }

    /** 周期键：daily_advice=当天日期；weekly_summary=ISO 周号（如 2026-W35），一周内不变 */
    private String periodKeyOf(String type) {
        LocalDate today = LocalDate.now();
        if (TYPE_WEEKLY.equals(type)) {
            WeekFields wf = WeekFields.ISO;
            return String.format("%d-W%02d", today.get(wf.weekBasedYear()), today.get(wf.weekOfWeekBasedYear()));
        }
        return today.toString();
    }

    /** 写缓存：同一 (学生,类型,周期) 只留一条，手动刷新覆盖旧内容 */
    private AiContentCache upsertCache(Long studentId, String type, String content) {
        AiContentCache old = aiCacheMapper.selectOne(new LambdaQueryWrapper<AiContentCache>()
                .eq(AiContentCache::getStudentId, studentId)
                .eq(AiContentCache::getContentType, type)
                .eq(AiContentCache::getPeriodKey, periodKeyOf(type))
                .last("LIMIT 1"));
        if (old != null) {
            old.setContent(content);
            old.setCreateTime(LocalDateTime.now());
            aiCacheMapper.updateById(old);
            return old;
        }
        AiContentCache one = new AiContentCache();
        one.setStudentId(studentId);
        one.setContentType(type);
        one.setPeriodKey(periodKeyOf(type));
        one.setContent(content);
        one.setCreateTime(LocalDateTime.now());
        aiCacheMapper.insert(one);
        return one;
    }

    /** 每日学习建议的提示词：孩子的真实学习数据 + 家教老师口吻的分条建议 */
    private String buildDailyAdvicePrompt(User child) {
        StudentAnalysisVO a = studentService.analysis(child.getId());
        List<Homework> recent = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStudentId, child.getId())
                .orderByDesc(Homework::getCreateTime)
                .last("LIMIT 5"));
        LocalDateTime now = LocalDateTime.now();

        StringBuilder sb = new StringBuilder();
        sb.append("学生「").append(childName(child)).append("」最近的学习数据如下：\n");
        sb.append("作业提交率 ").append(a.getSubmitRate() != null ? Math.round(a.getSubmitRate()) : 0)
                .append("%，作业平均分 ").append(a.getAverageScore() != null ? Math.round(a.getAverageScore()) : 0)
                .append(" 分；\n");
        long errorTotal = a.getErrorTypeCount() == null ? 0
                : a.getErrorTypeCount().values().stream().mapToLong(Long::longValue).sum();
        sb.append("测验错题共 ").append(errorTotal).append(" 个");
        if (a.getErrorTypeCount() != null && !a.getErrorTypeCount().isEmpty()) {
            StringJoiner sj = new StringJoiner("、");
            a.getErrorTypeCount().forEach((k, v) -> sj.add(k + " " + v + " 个"));
            sb.append("（").append(sj).append("）");
        }
        sb.append("；\n");
        Long overdueCount = homeworkMapper.selectCount(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStudentId, child.getId())
                .in(Homework::getStatus, "未提交", "需重做")
                .lt(Homework::getDeadline, now));
        if (overdueCount != null && overdueCount > 0) {
            sb.append("⚠️ 当前有 ").append(overdueCount).append(" 项作业逾期未提交；\n");
        }
        if (!recent.isEmpty()) {
            StringJoiner sj = new StringJoiner("、");
            for (Homework h : recent) {
                boolean od = ("未提交".equals(h.getStatus()) || "需重做".equals(h.getStatus()))
                        && h.getDeadline() != null && h.getDeadline().isBefore(now);
                sj.add(h.getTitle() + "[" + (od ? "已逾期" : h.getStatus()) + "]");
            }
            sb.append("最近作业：").append(sj).append("。\n");
        }
        sb.append("请你作为资深英语家教老师，面向家长给出 3~5 条简短、具体、可执行的学习建议（用中文，分条列出，每条一行，突出孩子的优点与需要督促的地方）。");
        return sb.toString();
    }

    /** 周报总结的提示词：一周聚合数据 + 面向家长的温暖点评 */
    private String buildWeeklySummaryPrompt(User child) {
        Map<String, Object> r = weeklyReportOf(child.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("学生「").append(childName(child)).append("」最近一周（")
                .append(r.get("rangeStart")).append(" ~ ").append(r.get("rangeEnd")).append("）的学习周报数据：\n");
        sb.append("新收到作业 ").append(r.get("newHomeworks")).append(" 份，提交作业 ").append(r.get("submitCount"))
                .append(" 次，获教师批改 ").append(r.get("reviewedCount")).append(" 份；\n");
        sb.append("本周作业平均分 ").append(r.get("avgScore")).append(" 分（上周 ").append(r.get("avgScoreLast")).append(" 分）；\n");
        sb.append("单词测验新增错词 ").append(r.get("newWordErrors")).append(" 个，掌握错词 ").append(r.get("masteredWords")).append(" 个；\n");
        sb.append("有学习记录的天数 ").append(r.get("activeDays")).append("/7 天");
        Object overdue = r.get("overdueHomeworks");
        if (overdue instanceof Number n && n.longValue() > 0) {
            sb.append("；⚠️ 当前有 ").append(n).append(" 项作业逾期未提交");
        }
        sb.append("。\n");
        sb.append("请你作为资深英语家教老师，用面向家长的口吻写一段 100 字左右的本周学习总结（中文，先肯定亮点再给 1~2 条下周督促建议，语气温暖）。");
        return sb.toString();
    }

    private String childName(User child) {
        return child.getRealName() != null && !child.getRealName().isBlank()
                ? child.getRealName() : child.getUsername();
    }

    private void checkAiType(String type) {
        if (!TYPE_DAILY.equals(type) && !TYPE_WEEKLY.equals(type)) {
            throw new BizException("不支持的 AI 内容类型");
        }
    }

    @Override
    public List<Map<String, Object>> myNotifications() {
        assertParent();
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .orderByDesc(Notification::getCreateTime)
                .last("LIMIT 50"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("bizId", n.getBizId());
            m.put("isRead", n.getIsRead());
            m.put("createTime", n.getCreateTime());
            result.add(m);
        }
        return result;
    }

    @Override
    public Long unreadCount() {
        assertParent();
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long notificationId) {
        assertParent();
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !Objects.equals(n.getUserId(), AuthContext.userId())) {
            throw new BizException("通知不存在");
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    @Override
    public void markAllRead() {
        assertParent();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    // ==================== 私有方法 ====================

    /** 校验当前登录用户是家长角色 */
    private void assertParent() {
        if (!"parent".equals(AuthContext.role())) {
            throw new BizException("只有家长账号可以访问");
        }
    }

    /** 校验 childId 是当前家长绑定的学生，防止越权查看 */
    private void assertMyChild(Long childId) {
        assertParent();
        User child = userMapper.selectById(childId);
        if (child == null || !"student".equals(child.getRole())
                || !Objects.equals(child.getParentId(), AuthContext.userId())) {
            throw new BizException("该学生未绑定到您的账号下");
        }
    }
}
