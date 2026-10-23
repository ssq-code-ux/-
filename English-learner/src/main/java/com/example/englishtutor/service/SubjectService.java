package com.example.englishtutor.service;
import com.example.englishtutor.entity.Subject;
import java.util.List;

public interface SubjectService {
    /** 学科列表（全局共享，老师和学生都可见） */
    List<Subject> list();
    /** 添加学科（仅老师） */
    void add(String name);
    /** 删除学科（仅老师；默认"英语"不可删，其下资料自动归入英语） */
    void delete(Long id);
}
