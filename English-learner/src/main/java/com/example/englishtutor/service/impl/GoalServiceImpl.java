package com.example.englishtutor.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import com.example.englishtutor.common.AuthContext;import com.example.englishtutor.dto.GoalDTO;import com.example.englishtutor.entity.LearningGoal;import com.example.englishtutor.mapper.LearningGoalMapper;import com.example.englishtutor.service.GoalService;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.time.LocalDateTime;import java.util.List;
@Service @RequiredArgsConstructor
public class GoalServiceImpl implements GoalService{
    private final LearningGoalMapper mapper;
    public void add(GoalDTO d){
        LearningGoal g=new LearningGoal();
        g.setStudentId(d.getStudentId());
        g.setTeacherId(AuthContext.userId());
        g.setTitle(d.getTitle());
        g.setDescription(d.getDescription());
        g.setDeadline(d.getDeadline());
        g.setStatus("进行中");
        g.setCreateTime(LocalDateTime.now());
        mapper.insert(g);
    }
    public List<LearningGoal> list(Long studentId){
        LambdaQueryWrapper<LearningGoal> q=new LambdaQueryWrapper<>();
        if(studentId!=null) q.eq(LearningGoal::getStudentId,studentId);
        else if("student".equals(AuthContext.role())) q.eq(LearningGoal::getStudentId,AuthContext.userId());
        else q.eq(LearningGoal::getTeacherId,AuthContext.userId());
        return mapper.selectList(q.orderByDesc(LearningGoal::getCreateTime));
    }
    public void updateStatus(Long id,String status){
        LearningGoal g=mapper.selectById(id);
        g.setStatus(status);mapper.updateById(g);
    }
}
