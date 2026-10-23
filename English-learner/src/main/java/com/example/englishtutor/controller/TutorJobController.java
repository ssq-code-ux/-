package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.TutorJobService;
import com.example.englishtutor.service.TutorPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 教师端家教接单接口 — 浏览开放中的单子、投递简历、查看我的投递/退费、转发好单。
 * 角色校验在 Service 层 assertTeacher()。
 * 网关路由：/api/tutor-jobs/** → user-service。
 */
@RestController
@RequestMapping("/api/tutor-jobs")
@RequiredArgsConstructor
public class TutorJobController {

    private final TutorJobService tutorJobService;
    private final TutorPaymentService paymentService;

    /** 开放中的单子分页（keyword 搜科目/城市/单号；province/city 筛选；jobType=1家教单 2同行合作单） */
    @GetMapping
    public R<Map<String, Object>> openJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer jobType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(tutorJobService.openJobs(keyword, province, city, jobType, page, size));
    }

    /** 投递简历（body.message 为附言；同一单同一老师只能投一次） */
    @PostMapping("/{id}/apply")
    public R<Void> apply(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String message = body == null ? null : (String) body.get("message");
        tutorJobService.apply(id, message);
        return R.ok(null);
    }

    /** 我投递过的简历列表（含审核状态 + 试课状态） */
    @GetMapping("/my-applications")
    public R<Map<String, Object>> myApplications(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(tutorJobService.myApplications(page, size));
    }

    /** 确认试课结果（body.success=true/false）：成功→订单完成；失败→单子重开 + 清空投递 + 登记退费工单 */
    @PostMapping("/applications/{applicationId}/trial")
    public R<Void> trialResult(@PathVariable Long applicationId,
                               @RequestBody Map<String, Object> body) {
        boolean success = body != null && Boolean.parseBoolean(String.valueOf(body.get("success")));
        tutorJobService.trialResult(applicationId, success);
        return R.ok(null);
    }

    /** 需求 1：我的退费工单（试课失败后投递记录已被清空，退费进度只能从这里看） */
    @GetMapping("/my-refund-tasks")
    public R<Map<String, Object>> myRefundTasks() {
        return R.ok(paymentService.myRefundTasks());
    }

    /** 需求 5：取转发文案（可直接复制到微信，末尾附自己的邀请码） */
    @GetMapping("/{id}/share-text")
    public R<Map<String, Object>> shareText(@PathVariable Long id) {
        return R.ok(tutorJobService.shareText(id));
    }
}
