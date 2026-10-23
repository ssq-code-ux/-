package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.service.ParentTutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 家长端找家教接口 — 提交需求、查看收到的教师简历投递、通过简历（返回管理端联系方式）。
 * 角色校验在 Service 层 assertParent()。
 * 网关路由：/api/parent-tutor/** → user-service。
 */
@RestController
@RequestMapping("/api/parent-tutor")
@RequiredArgsConstructor
public class ParentTutorController {

    private final ParentTutorService parentTutorService;

    /** 提交找家教需求（表单：称呼/电话/微信/学生程度/年级/学生性别/学科/老师性别要求/其他要求/时薪） */
    @PostMapping("/demands")
    public R<Void> submitDemand(@RequestBody Map<String, Object> body) {
        parentTutorService.submitDemand(body);
        return R.ok(null);
    }

    /** 我的需求单列表（含关联家教单状态与已投递数） */
    @GetMapping("/demands")
    public R<Map<String, Object>> myDemands(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(parentTutorService.myDemands(page, size));
    }

    /** 某需求单收到的教师简历投递列表（含教师完整简历） */
    @GetMapping("/demands/{demandId}/applications")
    public R<Map<String, Object>> myDemandApplications(@PathVariable Long demandId) {
        return R.ok(parentTutorService.myDemandApplications(demandId));
    }

    /** 申请删除需求单（管理端确认后才会真正删除，连带删除关联家教单） */
    @PostMapping("/demands/{demandId}/delete-request")
    public R<Void> requestDeleteDemand(@PathVariable Long demandId) {
        parentTutorService.requestDeleteDemand(demandId);
        return R.ok(null);
    }

    /** 通过/拒绝某份投递简历（通过后单子关闭；返回管理端联系方式供弹窗展示） */
    @PostMapping("/applications/{applicationId}/review")
    public R<Map<String, Object>> reviewApplication(
            @PathVariable Long applicationId, @RequestParam boolean approved) {
        return R.ok(parentTutorService.reviewApplication(applicationId, approved));
    }
}
