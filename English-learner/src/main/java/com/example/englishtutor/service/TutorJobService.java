package com.example.englishtutor.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 家教信息发布与投递 — 教师端（浏览/投递）+ 管理端（发布/审核/关闭）。
 * 教师端方法校验 teacher 角色，管理端方法校验 admin 角色，
 * 与 StudentService/ParentService/AdminService 同一套约定（统一走 FeignAuthContext 适配层取当前登录人）。
 * <p>
 * V3 变更：
 * 1. 试课失败 → 单子重开 + **清空该单全部投递历史**（含附带的老师姓名/电话/附言），
 *    缴费记录一行不动；退费状态搬到 tutor_refund_task，见 {@link TutorPaymentService}；
 * 2. 缴费/退费各写一条 tutor_payment_log 流水，时间永久留痕；
 * 3. jobType 区分「家教单」与「同行合作单」，共用同一套发布/投递/审核流程；
 * 4. 发布可带 templateId 套用科目模板，见 {@link TutorJobTemplateService}；
 * 5. 教师端可取转发文案（附自己的邀请码）转发好单给同行。
 */
public interface TutorJobService {

    // ==================== 教师端 ====================

    /** 开放中的单子分页（jobType：1家教单 2同行合作单 null全部；keyword 搜科目/城市/单号） */
    Map<String, Object> openJobs(String keyword, String province, String city,
                                 Integer jobType, long page, long size);

    /**
     * 投递简历（同一单同一老师只能投一次；单子关闭后不可投；
     * 需缴费的单必须先由管理端标记已缴费；本单还有未办结退费工单时不可再投）
     */
    void apply(Long jobId, String message);

    /** 我投递过的简历列表（含家教单摘要 + 审核状态 + 试课状态） */
    Map<String, Object> myApplications(long page, long size);

    /** 教师确认试课结果：成功→订单完成；失败→单子重开 + 清空该单全部投递 + 登记退费工单 */
    void trialResult(Long applicationId, boolean success);

    /** 教师端：某单的转发文案（复制到微信/群里发给同行，末尾附自己的邀请码） */
    Map<String, Object> shareText(Long jobId);

    // ==================== 管理端 ====================

    /**
     * 单子分页（keyword/jobType 筛选，附每单投递数与是否有待退费工单）。
     * <p>
     * view 是管理端「状态下拉 + 概览条点击」共用的筛选维度，优先级高于 status：
     * open=开放中、closed=已关闭、trial=试课中、done=已完成订单、
     * pendingApply=有待审核投递、refund=挂着待退费工单；为空时退回按 status 过滤。
     */
    Map<String, Object> adminJobs(String keyword, Integer status, Integer jobType,
                                  String view, long page, long size);

    /** 发布单子（body 可带 jobType 与 templateId；返回完整单子，含生成的单号） */
    Map<String, Object> publish(Map<String, Object> body);

    /** 编辑单子（仅开放中的单可编辑） */
    void update(Long jobId, Map<String, Object> body);

    /** 手动下架（关闭） */
    void close(Long jobId);

    /** 重新上架（开放，清掉通过记录） */
    void reopen(Long jobId);

    /** 彻底删除单子（连带投递、缴费、退费工单与流水；关联的家长需求回退为待发布） */
    void adminDeleteJob(Long jobId);

    /** 某个单的投递列表（管理端审核用；附该单家长的联系方式） */
    Map<String, Object> applications(Long jobId, long page, long size);

    /** 某个单的详情（完整字段 + 投递数 + 试课状态 + 已通过老师 + 家长联系方式 + 是否待退费） */
    Map<String, Object> adminJobDetail(Long jobId);

    /** 家教业务待办统计（待发布需求/删除申请/待审核投递/试课中/待退费/缴费收入） */
    Map<String, Object> adminTodo();

    /** 审核投递：通过（true）自动关闭该单并通知老师 / 未通过（false）仅通知老师 */
    void review(Long applicationId, boolean approved);

    /** 标记教师已缴费（解锁该单投递权，记金额方便后续退费，并写一条缴费流水） */
    void markPaid(Long jobId, Long teacherId, BigDecimal amount, String note);

    /** 按“单+教师”退费（金额清零、写退费流水、办结该教师本单的待退费工单） */
    void refund(Long jobId, Long teacherId, String note);

    /** 某单的缴费列表（含教师姓名） */
    Map<String, Object> payments(Long jobId);
}
