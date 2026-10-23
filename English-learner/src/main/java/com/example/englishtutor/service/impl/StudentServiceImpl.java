package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.*;
import com.example.englishtutor.dto.ProfileDTO;
import com.example.englishtutor.entity.*;
import com.example.englishtutor.mapper.*;
import com.example.englishtutor.service.StudentService;
import com.example.englishtutor.vo.StudentAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {
    private final UserMapper userMapper;
    private final StudentProfileMapper profileMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper submitMapper;
    private final HomeworkReviewMapper reviewMapper;
    private final ErrorRecordMapper errorMapper;
    private final VocabularyMapper vocabularyMapper;

    public List<User> myStudents() {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student").eq(User::getTeacherId, AuthContext.userId()));
    }

    public List<User> listTeachers() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "teacher"));
    }

    public List<User> searchStudents(String name) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student")
                .and(w -> w.isNull(User::getTeacherId).or().eq(User::getTeacherId, AuthContext.userId()));
        if (name != null && !name.isEmpty()) q.like(User::getRealName, name);
        q.orderByAsc(User::getRealName).last("LIMIT 50");
        return userMapper.selectList(q);
    }

    public void bindStudent(Long studentId) {
        User student = userMapper.selectById(studentId);
        if (student == null || !"student".equals(student.getRole())) {
            throw new BizException("学生不存在");
        }
        if (student.getTeacherId() != null && !student.getTeacherId().equals(AuthContext.userId())) {
            throw new BizException("该学生已绑定其他老师");
        }
        student.setTeacherId(AuthContext.userId());
        userMapper.updateById(student);
    }

    public User myTeacher() {
        User me = userMapper.selectById(AuthContext.userId());
        if (me == null || me.getTeacherId() == null) return null;
        User teacher = userMapper.selectById(me.getTeacherId());
        if (teacher != null) {
            teacher.setPassword(null);
        }
        return teacher;
    }

    public void saveProfile(ProfileDTO d) {
        StudentProfile p = profileMapper.selectOne(
                new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getStudentId, d.getStudentId()));
        if (p == null) { p = new StudentProfile(); p.setCreateTime(LocalDateTime.now()); }
        p.setStudentId(d.getStudentId());
        p.setTeacherId(AuthContext.userId());
        p.setGrade(d.getGrade());
        p.setSubject(d.getSubject());
        p.setTextbook(d.getTextbook());
        p.setLevel(d.getLevel());
        p.setGoals(d.getGoals());
        p.setWeakPoints(d.getWeakPoints());
        if (p.getId() == null) profileMapper.insert(p);
        else profileMapper.updateById(p);
    }

    public StudentProfile profile(Long studentId) {
        return profileMapper.selectOne(
                new LambdaQueryWrapper<StudentProfile>().eq(StudentProfile::getStudentId, studentId));
    }

    public StudentAnalysisVO analysis(Long studentId) {
        StudentAnalysisVO vo = new StudentAnalysisVO();
        vo.setStudentId(studentId);

        // 作业列表一次查出复用：总量/提交统计与批改关联都用它（避免多次回表）
        List<Homework> hws = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getStudentId, studentId));
        long total = hws.size();
        // 按作业当前状态计提交：“需重做”未重新提交、以及“未提交”的不算已提交
        long submitted = hws.stream()
                .filter(h -> "已提交".equals(h.getStatus()) || "已批改".equals(h.getStatus())).count();
        vo.setTotalHomework(total);
        vo.setSubmittedCount(submitted);
        vo.setSubmitRate(total == 0 ? 0.0 : submitted * 100.0 / total);

        // 评分统计：按学生作业 ID 一次查出全部批改（替代全表扫描+逐条回查作业表）
        List<Long> hwIds = hws.stream().map(Homework::getId).toList();
        List<HomeworkReview> rs = hwIds.isEmpty() ? List.of()
                : reviewMapper.selectList(new LambdaQueryWrapper<HomeworkReview>()
                        .in(HomeworkReview::getHomeworkId, hwIds));
        vo.setAverageScore(rs.stream().filter(r -> r.getScore() != null)
                .mapToInt(HomeworkReview::getScore).average().orElse(0));

        // 错误统计
        List<ErrorRecord> es = errorMapper.selectList(
                new LambdaQueryWrapper<ErrorRecord>().eq(ErrorRecord::getStudentId, studentId));
        Map<String, Long> m = es.stream()
                .collect(Collectors.groupingBy(ErrorRecord::getErrorType, Collectors.counting()));
        vo.setErrorTypeCount(m);

        // 词汇量
        vo.setVocabCount(vocabularyMapper.selectCount(null));

        // 分数趋势（最近7次已批改作业：未批改的不画 0 分；重做后以最新一次批改为准）
        List<Map<String, Object>> scoreTrend = new ArrayList<>();
        List<Homework> recentHw = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getStudentId, studentId)
                        .orderByDesc(Homework::getCreateTime).last("LIMIT 20"));
        Collections.reverse(recentHw);
        // 一次查齐这 20 份作业的全部批改，内存取每份“最新一条有分数”的（替代循环逐份查询）
        Map<Long, Integer> latestScoreByHw = new HashMap<>();
        if (!recentHw.isEmpty()) {
            List<Long> recentIds = recentHw.stream().map(Homework::getId).toList();
            reviewMapper.selectList(new LambdaQueryWrapper<HomeworkReview>()
                            .in(HomeworkReview::getHomeworkId, recentIds)
                            .orderByDesc(HomeworkReview::getReviewTime))
                    .forEach(r -> {
                        if (r.getScore() != null) latestScoreByHw.putIfAbsent(r.getHomeworkId(), r.getScore());
                    });
        }
        for (Homework h : recentHw) {
            if (scoreTrend.size() >= 7) break;
            Integer score = latestScoreByHw.get(h.getId());
            if (score == null) continue; // 未批改，跳过不画 0
            Map<String, Object> point = new HashMap<>();
            point.put("title", h.getTitle());
            point.put("score", score);
            scoreTrend.add(point);
        }
        vo.setScoreTrend(scoreTrend);

        // 提交趋势（最近7天）：区间内一次查出，内存按天分组（替代按天循环查询）
        List<Map<String, Object>> submitTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Map<String, Long> submitCountByDate = submitMapper.selectList(
                        new LambdaQueryWrapper<HomeworkSubmit>()
                                .eq(HomeworkSubmit::getStudentId, studentId)
                                .ge(HomeworkSubmit::getSubmitTime, today.minusDays(6).atStartOfDay()))
                .stream().filter(s -> s.getSubmitTime() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getSubmitTime().toLocalDate().toString(), Collectors.counting()));
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> point = new HashMap<>();
            point.put("date", d.toString());
            point.put("count", submitCountByDate.getOrDefault(d.toString(), 0L));
            submitTrend.add(point);
        }
        vo.setSubmitTrend(submitTrend);

        // 建议
        List<String> sug = new ArrayList<>();
        m.forEach((k, v) -> { if (v >= 3) sug.add("建议加强【" + k + "】专项训练，连续安排3-5次针对性练习。"); });
        if (vo.getSubmitRate() < 60) sug.add("作业提交率偏低，建议降低单次任务量并增加提醒。");
        if (vo.getAverageScore() < 70 && submitted > 0) sug.add("平均分低于70，建议先复习基础语法和核心词汇。");
        if (vo.getSubmitRate() >= 80 && vo.getAverageScore() >= 80)
            sug.add("表现优秀！建议挑战更高难度的学习内容。");
        vo.setSuggestions(sug);

        return vo;
    }
}
