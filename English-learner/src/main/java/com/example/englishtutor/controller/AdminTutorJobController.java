package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.TutorJobService;
import com.example.englishtutor.service.TutorJobTemplateService;
import com.example.englishtutor.service.TutorPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端家教信息接口 — 发布/编辑/下架/重开 + 查看投递并审核
 * + 同行合作单（jobType=2）/ 科目模板 / AI 批量发布 / 退费工单 / 缴费流水。
 * 角色校验在 Service 层 assertAdmin()。
 * 路径在 /api/admin/** 下，由 AuthInterceptor 做登录校验。
 */
@RestController
@RequestMapping("/api/admin/tutor-jobs")
@RequiredArgsConstructor
public class AdminTutorJobController {

    private final TutorJobService tutorJobService;
    private final TutorJobTemplateService templateService;
    private final TutorPaymentService paymentService;

    /**
     * 单子分页（keyword/jobType 筛选；jobType=1家教单 2同行合作单 不传全部）。
     * view 给管理端的状态下拉与概览条共用：
     * open/closed/trial/done/pendingApply/refund，传了 view 就忽略 status。
     */
    @GetMapping
    public R<Map<String, Object>> jobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer jobType,
            @RequestParam(required = false) String view,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(tutorJobService.adminJobs(keyword, status, jobType, view, page, size));
    }

    /** 发布单子（body 可带 jobType 与 templateId；templateId 存在时先用模板预填再被 body 覆盖） */
    @PostMapping
    public R<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        return R.ok(tutorJobService.publish(body));
    }

    /** 需求 7：AI 把一大段多条家教信息拆分成草稿（不落库，等管理端核对修改后再批量发布） */
    @PostMapping("/parse")
    public R<List<Map<String, Object>>> parse(@RequestBody Map<String, Object> body) {
        String text = body.get("text") == null ? null : String.valueOf(body.get("text"));
        Integer jobType = body.get("jobType") == null ? null
                : Integer.parseInt(String.valueOf(body.get("jobType")));
        return R.ok(templateService.parseJobs(text, jobType));
    }

    /** 需求 7：批量发布（逐条独立成败，失败原因原样返回让管理端继续改） */
    @PostMapping("/batch")
    public R<Map<String, Object>> batchPublish(@RequestBody List<Map<String, Object>> jobs) {
        return R.ok(templateService.batchPublish(jobs));
    }

    /** 需求 8：科目订单模板列表 */
    @GetMapping("/templates")
    public R<Map<String, Object>> templates(@RequestParam(required = false) Integer jobType) {
        return R.ok(templateService.templates(jobType));
    }

    /** 需求 8：新增/编辑科目模板（同一科目同一单类型只允许一份） */
    @PostMapping("/templates")
    public R<Void> saveTemplate(@RequestBody Map<String, Object> body) {
        templateService.saveTemplate(body);
        return R.ok(null);
    }

    /** 需求 8：删除科目模板 */
    @PostMapping("/templates/{templateId}/delete")
    public R<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.deleteTemplate(templateId);
        return R.ok(null);
    }

    /** 需求 1：退费工单分页（试课失败后投递记录已被清空，退费只能从这里办理） */
    @GetMapping("/refund-tasks")
    public R<Map<String, Object>> refundTasks(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(paymentService.refundTasks(status, keyword, page, size));
    }

    /** 需求 1：办结单张退费工单（有缴费则退款清零，免费单仅标记已处理） */
    @PostMapping("/refund-tasks/{taskId}/settle")
    public R<Void> settleRefundTask(@PathVariable Long taskId,
                                    @RequestParam(required = false) String note) {
        paymentService.settleRefundTask(taskId, note);
        return R.ok(null);
    }

    /** 需求 2：缴费/退费流水分页（只增不改，时间永久留痕） */
    @GetMapping("/payment-logs")
    public R<Map<String, Object>> paymentLogs(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(paymentService.paymentLogs(jobId, type, page, size));
    }

    /** 编辑家教单（仅开放中的单可编辑） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        tutorJobService.update(id, body);
        return R.ok(null);
    }

    /** 手动下架（关闭） */
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        tutorJobService.close(id);
        return R.ok(null);
    }

    /** 重新上架（开放，清掉旧的通过标记） */
    @PostMapping("/{id}/reopen")
    public R<Void> reopen(@PathVariable Long id) {
        tutorJobService.reopen(id);
        return R.ok(null);
    }

    /** 彻底删除单子（连带投递、缴费、退费工单与流水；关联的家长需求回退为待发布） */
    @PostMapping("/{id}/delete")
    public R<Void> delete(@PathVariable Long id) {
        tutorJobService.adminDeleteJob(id);
        return R.ok(null);
    }

    /** 家教单详情（完整信息 + 投递数 + 家长联系方式） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(tutorJobService.adminJobDetail(id));
    }

    /** 某个家教单的投递列表（审核用；附该单家长联系方式） */
    @GetMapping("/{id}/applications")
    public R<Map<String, Object>> applications(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(tutorJobService.applications(id, page, size));
    }

    /** 审核投递：通过后该单自动关闭不再开放 */
    @PostMapping("/applications/{applicationId}/review")
    public R<Void> review(@PathVariable Long applicationId, @RequestParam boolean approved) {
        tutorJobService.review(applicationId, approved);
        return R.ok(null);
    }
}
