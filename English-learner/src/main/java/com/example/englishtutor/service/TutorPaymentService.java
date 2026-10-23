package com.example.englishtutor.service;

import com.example.englishtutor.entity.TutorJob;
import com.example.englishtutor.entity.TutorRefundTask;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 缴费流水 + 退费工单（需求 1、需求 2）。
 * <p>
 * 单独成一个服务的原因：试课失败后 {@code tutor_job_application} 会被整单清空，
 * 退费状态必须搬到一个不随投递记录消失的地方；同时缴费/退费要有永久时间留痕，
 * 而 {@code tutor_job_payment} 是“当前状态”表（退费后金额清零、时间被覆盖），当不了账本。
 * 这两件事都围绕 tutor_refund_task 与 tutor_payment_log，放在 TutorJobService 里会让它继续膨胀。
 */
public interface TutorPaymentService {

    // ==================== 供 TutorJobService 调用 ====================

    /** 写一条缴费/退费流水（只增不改；写失败只记日志，不阻断主流程） */
    void writeLog(Long jobId, String jobNo, Long teacherId, String teacherName,
                  int type, BigDecimal amount, String note);

    /** 登记一张退费工单（教师反馈试课失败时调用，投递记录随后会被清空） */
    TutorRefundTask createTask(TutorJob job, Long teacherId, String teacherName,
                               String teacherPhone, BigDecimal amount, String reason);

    /** 该单该教师是否还有未办结的退费工单（有则不允许再投同一单） */
    boolean hasPending(Long jobId, Long teacherId);

    /** 一批单子里哪些还挂着待退费工单（管理端列表打红标用） */
    Set<Long> pendingJobIds(Collection<Long> jobIds);

    /** 全库还挂着待退费工单的 jobId（管理端概览条“试课失败退费”点击筛选用） */
    Set<Long> allPendingJobIds();

    /** 待退费工单总数（管理端 Tab 红点） */
    long pendingCount();

    /**
     * 办结某单某教师的全部待退费工单（管理端按“单+教师”退费时调用）。
     *
     * @param refunded 实际退款金额，0 表示免费单只标记已处理
     * @return 办结条数
     */
    int settleTasksFor(Long jobId, Long teacherId, BigDecimal refunded, String note);

    /** 删除家教单时连带清理工单与流水，避免留下孤儿数据 */
    void deleteByJobId(Long jobId);

    // ==================== 管理端 ====================

    /** 退费工单分页（status：0待退费 1已办结 null全部；keyword 搜单号/教师姓名/电话） */
    Map<String, Object> refundTasks(Integer status, String keyword, long page, long size);

    /** 办结单张退费工单（有缴费记录则退款清零并写流水；免费单直接标记已处理） */
    void settleRefundTask(Long taskId, String note);

    /** 缴费/退费流水分页（jobId、type 可选筛选） */
    Map<String, Object> paymentLogs(Long jobId, Integer type, long page, long size);

    // ==================== 教师端 ====================

    /** 我的退费工单（投递记录已被清空，退费进度只能从这里看） */
    Map<String, Object> myRefundTasks();
}
