package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.LearningResource;
import com.example.englishtutor.entity.Subject;
import com.example.englishtutor.mapper.LearningResourceMapper;
import com.example.englishtutor.mapper.SubjectMapper;
import com.example.englishtutor.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    /** 默认学科名，预置且不可删除 */
    public static final String DEFAULT_SUBJECT = "英语";

    private final SubjectMapper mapper;
    private final LearningResourceMapper resourceMapper;

    public List<Subject> list() {
        // 学科全局共享：老师和学生都可见同一份学科列表
        return mapper.selectList(new LambdaQueryWrapper<Subject>().orderByAsc(Subject::getId));
    }

    public void add(String name) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可管理学科");
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new BizException("学科名称不能为空");
        if (n.length() > 50) throw new BizException("学科名称过长（最多50字）");
        Long exist = mapper.selectCount(new LambdaQueryWrapper<Subject>().eq(Subject::getName, n));
        if (exist != null && exist > 0) throw new BizException("学科已存在");
        Subject s = new Subject();
        s.setName(n);
        s.setTeacherId(AuthContext.userId());
        s.setCreateTime(LocalDateTime.now());
        mapper.insert(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可管理学科");
        Subject s = mapper.selectById(id);
        if (s == null) throw new BizException("学科不存在");
        if (DEFAULT_SUBJECT.equals(s.getName())) throw new BizException("默认英语学科不可删除");
        // 该学科下的资料回迁到默认"英语"学科
        Subject english = mapper.selectOne(new LambdaQueryWrapper<Subject>().eq(Subject::getName, DEFAULT_SUBJECT));
        Long englishId = english != null ? english.getId() : null;
        LearningResource upd = new LearningResource();
        upd.setSubjectId(englishId);
        resourceMapper.update(upd, new LambdaQueryWrapper<LearningResource>().eq(LearningResource::getSubjectId, id));
        mapper.deleteById(id);
    }
}
