package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.service.ParentService;
import com.example.englishtutor.vo.StudentAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家长端接口 — 查看绑定孩子的学习情况
 * 所有接口内部都会校验"孩子必须是当前家长绑定的学生"
 */
@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService service;

    /** 我的孩子列表 */
    @GetMapping("/children")
    public R<List<User>> children() {
        return R.ok(service.myChildren());
    }

    /** 绑定孩子（输入学生的登录账号） */
    @PostMapping("/bind")
    public R<Void> bind(@RequestParam String childUsername) {
        service.bindChild(childUsername);
        return R.ok(null);
    }

    /** 孩子的学习分析报告（作业统计/错题/建议） */
    @GetMapping("/child/{childId}/analysis")
    public R<StudentAnalysisVO> analysis(@PathVariable Long childId) {
        return R.ok(service.childAnalysis(childId));
    }

    /** 孩子的作业列表（含状态与截止时间，逾期标红） */
    @GetMapping("/child/{childId}/homeworks")
    public R<List<Map<String, Object>>> homeworks(@PathVariable Long childId) {
        return R.ok(service.childHomeworks(childId));
    }

    /** 孩子的学习周报（最近 7 天作业/测验/活跃度聚合） */
    @GetMapping("/child/{childId}/weekly-report")
    public R<Map<String, Object>> weeklyReport(@PathVariable Long childId) {
        return R.ok(service.childWeeklyReport(childId));
    }

    /** 孩子的 AI 内容（type=daily_advice 每日学习建议 / weekly_summary 周报总结），读预生成缓存 */
    @GetMapping("/child/{childId}/ai-content")
    public R<Map<String, Object>> aiContent(@PathVariable Long childId, @RequestParam String type) {
        return R.ok(service.childAiContent(childId, type));
    }

    /** 手动重新生成孩子的 AI 内容（覆盖当天/本周缓存） */
    @PostMapping("/child/{childId}/ai-content/refresh")
    public R<Map<String, Object>> refreshAiContent(@PathVariable Long childId, @RequestParam String type) {
        return R.ok(service.refreshChildAiContent(childId, type));
    }

    /** 某份作业的详细内容（题目/提交内容/批改结果） */
    @GetMapping("/child/{childId}/homework/{homeworkId}")
    public R<Map<String, Object>> homeworkDetail(@PathVariable Long childId, @PathVariable Long homeworkId) {
        return R.ok(service.childHomeworkDetail(childId, homeworkId));
    }

    /** 我的站内通知列表（最新在前，最多 50 条） */
    @GetMapping("/notifications")
    public R<List<Map<String, Object>>> notifications() {
        return R.ok(service.myNotifications());
    }

    /** 未读通知数（铃铛角标） */
    @GetMapping("/notifications/unread-count")
    public R<Long> unreadCount() {
        return R.ok(service.unreadCount());
    }

    /** 标记单条通知已读 */
    @PostMapping("/notifications/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return R.ok(null);
    }

    /** 全部标记已读 */
    @PostMapping("/notifications/read-all")
    public R<Void> markAllRead() {
        service.markAllRead();
        return R.ok(null);
    }
}
