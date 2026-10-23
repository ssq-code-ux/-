package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.ParentTutorService;
import com.example.englishtutor.service.TutorJobService;
import com.example.englishtutor.service.TutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 管理端家教 V2 接口 — 家长需求单（查看/发布为家教单）、
 * 接单配置（须知/电话/微信/收款码）、教师缴费管理（标记缴费/退费）。
 * 角色校验在 Service 层 assertAdmin()。
 * 网关路由：/api/admin/tutor/** → user-service（已含在 /api/admin/** 内）。
 */
@RestController
@RequestMapping("/api/admin/tutor")
@RequiredArgsConstructor
public class AdminTutorV2Controller {

    private final ParentTutorService parentTutorService;
    private final TutorProfileService tutorProfileService;
    private final TutorJobService tutorJobService;

    // ==================== 家长需求 ====================

    /** 家长需求列表（keyword 搜订单号/需求编号/家长称呼/电话/微信；status：-1全部 0待发布 1已发布；
     *  deleteRequested：1只看删除申请） */
    @GetMapping("/demands")
    public R<Map<String, Object>> demands(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "-1") int status,
            @RequestParam(required = false) Integer deleteRequested,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(parentTutorService.adminDemands(keyword, status, deleteRequested, page, size));
    }

    /** 家教业务待办统计（待发布需求/删除申请/待审核投递/试课中/缴费收入） */
    @GetMapping("/todo")
    public R<Map<String, Object>> todo() {
        return R.ok(tutorJobService.adminTodo());
    }

    /** 家长需求详情（含家长联系方式 + 关联家教单信息） */
    @GetMapping("/demands/{demandId}")
    public R<Map<String, Object>> demandDetail(@PathVariable Long demandId) {
        return R.ok(parentTutorService.adminDemandDetail(demandId));
    }

    /** 把家长需求发布为家教单（教师端可见但不含家长信息） */
    @PostMapping("/demands/{demandId}/publish")
    public R<Map<String, Object>> publishDemand(
            @PathVariable Long demandId, @RequestBody Map<String, Object> body) {
        return R.ok(parentTutorService.adminPublishDemand(demandId, body));
    }

    /** 确认删除家长需求单（连带删除关联家教单、投递、缴费记录） */
    @PostMapping("/demands/{demandId}/delete")
    public R<Void> confirmDeleteDemand(@PathVariable Long demandId) {
        parentTutorService.adminConfirmDeleteDemand(demandId);
        return R.ok(null);
    }

    // ==================== 接单配置 ====================

    /** 读取接单配置（须知文字/图片、电话、微信、收款码） */
    @GetMapping("/config")
    public R<Map<String, Object>> getConfig() {
        return R.ok(tutorProfileService.adminGetConfig());
    }

    /** 保存接单配置 */
    @PostMapping("/config")
    public R<Void> saveConfig(@RequestBody Map<String, Object> body) {
        tutorProfileService.adminSaveConfig(body);
        return R.ok(null);
    }

    // ==================== 缴费管理 ====================

    /** 某家教单的缴费列表 */
    @GetMapping("/jobs/{jobId}/payments")
    public R<Map<String, Object>> payments(@PathVariable Long jobId) {
        return R.ok(tutorJobService.payments(jobId));
    }

    /** 标记教师已缴费（解锁该单投递权，金额>0 即解锁） */
    @PostMapping("/jobs/{jobId}/payments/{teacherId}")
    public R<Void> markPaid(
            @PathVariable Long jobId,
            @PathVariable Long teacherId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = null;
        if (body != null && body.get("amount") != null && !String.valueOf(body.get("amount")).isBlank()) {
            try {
                amount = new BigDecimal(String.valueOf(body.get("amount")).trim());
            } catch (NumberFormatException e) {
                throw new com.example.englishtutor.common.BizException("金额格式不正确");
            }
        }
        String note = body == null ? null : (String) body.get("note");
        tutorJobService.markPaid(jobId, teacherId, amount, note);
        return R.ok(null);
    }

    /** 退费（金额清零并记录退费时间） */
    @PostMapping("/jobs/{jobId}/payments/{teacherId}/refund")
    public R<Void> refund(
            @PathVariable Long jobId,
            @PathVariable Long teacherId,
            @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null ? null : (String) body.get("note");
        tutorJobService.refund(jobId, teacherId, note);
        return R.ok(null);
    }
}
