package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.service.AnswerSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 答题卡作业接口
 * <p>
 * 挂在 /api/homeworks 下，网关已有 /api/homeworks/** 路由，无需额外配置。
 */
@RestController
@RequestMapping("/api/homeworks/answer-sheet")
@RequiredArgsConstructor
public class AnswerSheetController {

    private final AnswerSheetService service;

    /** 教师布置答题卡，返回作业ID */
    @PostMapping
    public R<Long> assign(@RequestBody AnswerSheetDTO d) {
        return R.ok(service.assign(d));
    }

    /** 教师查看整卷，按大题分组（含答案与解析） */
    @GetMapping("/{homeworkId}/questions")
    public R<List<SectionDTO>> teacherQuestions(@PathVariable("homeworkId") Long homeworkId) {
        return R.ok(service.sectionsForTeacher(homeworkId));
    }

    /** 学生拉取答题卡，按大题分组（不含答案与解析） */
    @GetMapping("/{homeworkId}/paper")
    public R<List<StudentSectionDTO>> studentPaper(@PathVariable("homeworkId") Long homeworkId) {
        return R.ok(service.sectionsForStudent(homeworkId));
    }

    /** 学生提交答题卡，同步返回判分结果 */
    @PostMapping("/submit")
    public R<Map<String, Object>> submit(@RequestBody AnswerSheetSubmitDTO d) {
        return R.ok(service.submitSheet(d));
    }

    /** 逐题批改结果（学生本人或授课老师可看） */
    @GetMapping("/result/{submitId}")
    public R<Map<String, Object>> result(@PathVariable("submitId") Long submitId) {
        return R.ok(service.result(submitId));
    }

    /** 教师复核调分 */
    @PostMapping("/fix")
    public R<Void> fix(@RequestBody AnswerFixDTO d) {
        service.fix(d);
        return R.ok(null);
    }
}
