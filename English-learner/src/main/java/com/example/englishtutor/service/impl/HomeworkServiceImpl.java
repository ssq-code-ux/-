package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.*;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.entity.*;
import com.example.englishtutor.mapper.*;
import com.example.englishtutor.service.HomeworkService;
import com.example.englishtutor.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeworkServiceImpl implements HomeworkService {
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper submitMapper;
    private final HomeworkReviewMapper reviewMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;

    public void assign(HomeworkDTO d) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("只有老师可以布置作业");
        Homework h = new Homework();
        h.setTeacherId(AuthContext.userId());
        h.setStudentId(d.getStudentId());
        h.setTitle(d.getTitle());
        h.setContent(d.getContent());
        h.setType(d.getType());
        h.setSubject(resolveSubject(d.getSubject()));
        h.setAttachmentUrl(d.getAttachmentUrl());
        h.setDeadline(d.getDeadline());
        h.setStatus("未提交");
        h.setCreateTime(LocalDateTime.now());
        homeworkMapper.insert(h);

        // 布置即通知学生（站内铃铛 + 微信推送），失败不影响布置流程
        notifyService.onHomeworkNotify(d.getStudentId(), h.getId(), h.getTitle(), "assigned");
    }

    public void update(Long id, HomeworkDTO d) {
        Homework h = homeworkMapper.selectById(id);
        if (h == null) throw new BizException("作业不存在");
        if (!h.getTeacherId().equals(AuthContext.userId())) throw new BizException("只能编辑自己布置的作业");
        h.setTitle(d.getTitle());
        h.setContent(d.getContent());
        h.setType(d.getType());
        h.setSubject(resolveSubject(d.getSubject()));
        h.setAttachmentUrl(d.getAttachmentUrl());
        h.setDeadline(d.getDeadline());
        homeworkMapper.updateById(h);
    }

    /** 学科为空时默认「英语」（存量/未传场景） */
    private String resolveSubject(String subject) {
        return (subject == null || subject.trim().isEmpty()) ? "英语" : subject.trim();
    }

    public List<Homework> list(Long studentId) {
        LambdaQueryWrapper<Homework> q = new LambdaQueryWrapper<>();
        if ("teacher".equals(AuthContext.role())) {
            q.eq(Homework::getTeacherId, AuthContext.userId());
            if (studentId != null) q.eq(Homework::getStudentId, studentId);
        } else q.eq(Homework::getStudentId, AuthContext.userId());
        q.orderByDesc(Homework::getCreateTime);
        return homeworkMapper.selectList(q);
    }

    public Homework getById(Long id) {
        Homework h = homeworkMapper.selectById(id);
        if (h == null) throw new BizException("作业不存在");
        return h;
    }

    public Map<String, Object> listGrouped() {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("只有老师可以查看");
        List<Homework> all = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>().eq(Homework::getTeacherId, AuthContext.userId()).orderByDesc(Homework::getCreateTime));
        Map<Long, User> studentMap = userMapper.selectBatchIds(all.stream().map(Homework::getStudentId).distinct().collect(Collectors.toList())).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, List<Homework>> grouped = all.stream().collect(Collectors.groupingBy(Homework::getStudentId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> students = new ArrayList<>();
        grouped.forEach((sid, hws) -> {
            User s = studentMap.get(sid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("studentId", sid);
            entry.put("studentName", s != null ? s.getRealName() : "未知学生");
            entry.put("studentUsername", s != null ? s.getUsername() : "");
            long submitted = hws.stream().filter(h -> !"未提交".equals(h.getStatus())).count();
            long reviewed = hws.stream().filter(h -> "已批改".equals(h.getStatus())).count();
            entry.put("total", hws.size());
            entry.put("submitted", submitted);
            entry.put("reviewed", reviewed);
            entry.put("homeworks", hws);
            students.add(entry);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("students", students);
        result.put("total", all.size());
        return result;
    }

    public void submit(SubmitDTO d) {
        Homework h = homeworkMapper.selectById(d.getHomeworkId());
        if (h == null) throw new BizException("作业不存在");
        if (!h.getStudentId().equals(AuthContext.userId())) throw new BizException("只能提交自己的作业");
        HomeworkSubmit s = new HomeworkSubmit();
        s.setHomeworkId(d.getHomeworkId());
        s.setStudentId(AuthContext.userId());
        s.setTextAnswer(d.getTextAnswer());
        s.setFileUrl(d.getFileUrl());
        s.setStatus("已提交");
        s.setSubmitTime(LocalDateTime.now());
        submitMapper.insert(s);
        h.setStatus("已提交");
        homeworkMapper.updateById(h);
    }

    public List<HomeworkSubmit> submits(Long homeworkId) {
        return submitMapper.selectList(new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, homeworkId).orderByDesc(HomeworkSubmit::getSubmitTime));
    }

    public void review(ReviewDTO d) {
        HomeworkSubmit s = submitMapper.selectById(d.getSubmitId());
        if (s == null) throw new BizException("提交记录不存在");
        Homework h = homeworkMapper.selectById(s.getHomeworkId());
        if (!h.getTeacherId().equals(AuthContext.userId())) throw new BizException("只能批改自己学生的作业");
        HomeworkReview r = new HomeworkReview();
        r.setHomeworkId(h.getId());
        r.setSubmitId(s.getId());
        r.setTeacherId(AuthContext.userId());
        r.setScore(d.getScore());
        r.setComment(d.getComment());
        r.setVoiceUrl(d.getVoiceUrl());
        r.setNeedRedo(d.getNeedRedo());
        r.setReviewTime(LocalDateTime.now());
        reviewMapper.insert(r);
        s.setStatus("已批改");
        submitMapper.updateById(s);
        h.setStatus("true".equals(d.getNeedRedo()) ? "需重做" : "已批改");
        homeworkMapper.updateById(h);

        // 通知学生+家长批改结果（站内铃铛 + 微信推送）
        notifyService.onHomeworkNotify(h.getStudentId(), h.getId(), h.getTitle(), "reviewed");
    }

    public Map<String, Object> reviewResult(Long homeworkId) {
        HomeworkReview review = reviewMapper.selectOne(new LambdaQueryWrapper<HomeworkReview>().eq(HomeworkReview::getHomeworkId, homeworkId).orderByDesc(HomeworkReview::getReviewTime).last("limit 1"));
        if (review == null) return null;
        HomeworkSubmit submit = submitMapper.selectById(review.getSubmitId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("review", review);
        result.put("submit", submit);
        return result;
    }

    public HomeworkReview getReviewBySubmitId(Long submitId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<HomeworkReview>().eq(HomeworkReview::getSubmitId, submitId).orderByDesc(HomeworkReview::getReviewTime).last("limit 1"));
    }

    public List<Homework> overdue() {
        LambdaQueryWrapper<Homework> q = new LambdaQueryWrapper<>();
        q.eq(Homework::getStudentId, AuthContext.userId());
        q.in(Homework::getStatus, "未提交", "需重做");
        q.lt(Homework::getDeadline, LocalDateTime.now());
        return homeworkMapper.selectList(q);
    }

    public void delete(Long id) {
        Homework h = homeworkMapper.selectById(id);
        if (h == null) throw new BizException("作业不存在");
        if (!h.getTeacherId().equals(AuthContext.userId())) throw new BizException("只能删除自己布置的作业");
        // 答题卡作业还挂着题目和逐题作答记录，一并清掉避免留孤儿数据
        List<Long> submitIds = submitMapper.selectList(new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, id)).stream().map(HomeworkSubmit::getId).collect(Collectors.toList());
        if (!submitIds.isEmpty()) {
            answerMapper.delete(new LambdaQueryWrapper<HomeworkAnswer>().in(HomeworkAnswer::getSubmitId, submitIds));
        }
        questionMapper.delete(new LambdaQueryWrapper<HomeworkQuestion>().eq(HomeworkQuestion::getHomeworkId, id));
        reviewMapper.delete(new LambdaQueryWrapper<HomeworkReview>().eq(HomeworkReview::getHomeworkId, id));
        submitMapper.delete(new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, id));
        homeworkMapper.deleteById(id);
    }
}
