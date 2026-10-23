package com.example.englishtutor.service;

import com.example.englishtutor.dto.AnswerFixDTO;
import com.example.englishtutor.dto.AnswerSheetDTO;
import com.example.englishtutor.dto.AnswerSheetSubmitDTO;
import com.example.englishtutor.dto.SectionDTO;
import com.example.englishtutor.dto.StudentSectionDTO;

import java.util.List;
import java.util.Map;

/** 答题卡作业：布置 / 拉题 / 提交判分 / 结果 / 教师复核 */
public interface AnswerSheetService {

    /** 教师布置答题卡作业（题目已在前端核验过），返回作业ID */
    Long assign(AnswerSheetDTO dto);

    /** 教师查看整卷，按大题分组（含标准答案与解析） */
    List<SectionDTO> sectionsForTeacher(Long homeworkId);

    /** 学生拉取答题卡，按大题分组 — 已剥离答案 */
    List<StudentSectionDTO> sectionsForStudent(Long homeworkId);

    /** 学生提交答题卡，同步完成判分，返回 { submitId, totalScore, fullScore, rightCount, total, pendingAi } */
    Map<String, Object> submitSheet(AnswerSheetSubmitDTO dto);

    /** 批改结果：{ gotScore, fullScore, review, sections:[大题材料], items:[AnswerResultDTO] } */
    Map<String, Object> result(Long submitId);

    /** 教师复核调整判定与得分，重算总分 */
    void fix(AnswerFixDTO dto);
}
