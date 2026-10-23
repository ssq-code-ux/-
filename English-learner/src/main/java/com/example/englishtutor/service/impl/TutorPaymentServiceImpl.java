package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.TutorJob;
import com.example.englishtutor.entity.TutorJobPayment;
import com.example.englishtutor.entity.TutorPaymentLog;
import com.example.englishtutor.entity.TutorRefundTask;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.TutorJobMapper;
import com.example.englishtutor.mapper.TutorJobPaymentMapper;
import com.example.englishtutor.mapper.TutorPaymentLogMapper;
import com.example.englishtutor.mapper.TutorRefundTaskMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.TutorPaymentService;
import com.example.englishtutor.service.WechatNotifyPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缴费流水 + 退费工单实现。
 * <p>
 * 两条铁律：
 * 1. 流水只增不改 —— 任何一次缴费或退费都落一条 tutor_payment_log，时间永久留痕；
 *    tutor_job_payment 只表示“当前状态”，退费后金额清零、时间被覆盖，不能当账本用。
 * 2. 退费工单与投递记录解耦 —— 试课失败会把该单投递历史整单清空，
 *    工单必须在删除之前先落库，否则“待退费”计数会重新变成永远为 0 的老 bug。
 * <p>
 * 注意：MyBatis-Plus updateById 默认跳过 null 字段，清零 refund_time 这类操作必须用 LambdaUpdateWrapper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorPaymentServiceImpl implements TutorPaymentService {

    private final TutorRefundTaskMapper refundTaskMapper;
    private final TutorPaymentLogMapper paymentLogMapper;
    private final TutorJobPaymentMapper paymentMapper;
    private final TutorJobMapper jobMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final WechatNotifyPusher wechatPusher;

    // ==================== 供 TutorJobService 调用 ====================

    @Override
    public void writeLog(Long jobId, String jobNo, Long teacherId, String teacherName,
                         int type, BigDecimal amount, String note) {
        try {
            TutorPaymentLog l = new TutorPaymentLog();
            l.setJobId(jobId);
            l.setJobNo(jobNo);
            l.setTeacherId(teacherId);
            l.setTeacherName(teacherName);
            l.setType(type);
            l.setAmount(amount == null ? BigDecimal.ZERO : amount);
            l.setNote(note);
            l.setOperatorId(FeignAuthContext.userId());
            l.setOperatorName(FeignAuthContext.username());
            l.setCreateTime(LocalDateTime.now());
            paymentLogMapper.insert(l);
        } catch (Exception e) {
            // 流水是附加账本，写失败不能把已经成功的缴费/退费回滚掉
            log.error("[缴费流水] 写入失败 jobId={} teacherId={} type={}：{}", jobId, teacherId, type, e.getMessage());
        }
    }

    @Override
    public TutorRefundTask createTask(TutorJob job, Long teacherId, String teacherName,
                                      String teacherPhone, BigDecimal amount, String reason) {
        TutorRefundTask task = new TutorRefundTask();
        task.setJobId(job.getId());
        task.setJobNo(job.getJobNo());
        task.setTeacherId(teacherId);
        task.setTeacherName(teacherName);
        task.setTeacherPhone(teacherPhone);
        task.setAmount(amount == null ? BigDecimal.ZERO : amount);
        task.setReason(reason);
        task.setStatus(TutorRefundTask.STATUS_PENDING);
        task.setCreateTime(LocalDateTime.now());
        refundTaskMapper.insert(task);
        return task;
    }

    @Override
    public boolean hasPending(Long jobId, Long teacherId) {
        if (jobId == null || teacherId == null) return false;
        Long c = refundTaskMapper.selectCount(new LambdaQueryWrapper<TutorRefundTask>()
                .eq(TutorRefundTask::getJobId, jobId)
                .eq(TutorRefundTask::getTeacherId, teacherId)
                .eq(TutorRefundTask::getStatus, TutorRefundTask.STATUS_PENDING));
        return c != null && c > 0;
    }

    @Override
    public Set<Long> pendingJobIds(Collection<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) return Set.of();
        List<TutorRefundTask> tasks = refundTaskMapper.selectList(new LambdaQueryWrapper<TutorRefundTask>()
                .select(TutorRefundTask::getJobId)
                .in(TutorRefundTask::getJobId, jobIds)
                .eq(TutorRefundTask::getStatus, TutorRefundTask.STATUS_PENDING));
        Set<Long> ids = new HashSet<>();
        for (TutorRefundTask t : tasks) {
            ids.add(t.getJobId());
        }
        return ids;
    }

    /**
     * 不限定单号范围，直接拿全库待退费的 jobId。
     * 管理端概览条上点“试课失败退费”时靠它把列表筛成只剩欠着退费的单。
     */
    @Override
    public Set<Long> allPendingJobIds() {
        List<TutorRefundTask> tasks = refundTaskMapper.selectList(new LambdaQueryWrapper<TutorRefundTask>()
                .select(TutorRefundTask::getJobId)
                .eq(TutorRefundTask::getStatus, TutorRefundTask.STATUS_PENDING));
        Set<Long> ids = new HashSet<>();
        for (TutorRefundTask t : tasks) {
            if (t.getJobId() != null) {
                ids.add(t.getJobId());
            }
        }
        return ids;
    }

    @Override
    public long pendingCount() {
        Long c = refundTaskMapper.selectCount(new LambdaQueryWrapper<TutorRefundTask>()
                .eq(TutorRefundTask::getStatus, TutorRefundTask.STATUS_PENDING));
        return c == null ? 0L : c;
    }

    @Override
    public int settleTasksFor(Long jobId, Long teacherId, BigDecimal refunded, String note) {
        List<TutorRefundTask> tasks = refundTaskMapper.selectList(new LambdaQueryWrapper<TutorRefundTask>()
                .eq(TutorRefundTask::getJobId, jobId)
                .eq(TutorRefundTask::getTeacherId, teacherId)
                .eq(TutorRefundTask::getStatus, TutorRefundTask.STATUS_PENDING));
        LocalDateTime now = LocalDateTime.now();
        for (TutorRefundTask t : tasks) {
            t.setStatus(TutorRefundTask.STATUS_DONE);
            t.setRefundTime(now);
            t.setOperatorId(FeignAuthContext.userId());
            t.setNote(note);
            // 登记时算出的应退金额可能已过期（管理员后来改过缴费金额），以实际退款额为准
            if (refunded != null) {
                t.setAmount(refunded);
            }
            refundTaskMapper.updateById(t);
        }
        return tasks.size();
    }

    @Override
    public void deleteByJobId(Long jobId) {
        if (jobId == null) return;
        refundTaskMapper.delete(new LambdaQueryWrapper<TutorRefundTask>()
                .eq(TutorRefundTask::getJobId, jobId));
        paymentLogMapper.delete(new LambdaQueryWrapper<TutorPaymentLog>()
                .eq(TutorPaymentLog::getJobId, jobId));
    }

    // ==================== 管理端 ====================

    @Override
    public Map<String, Object> refundTasks(Integer status, String keyword, long page, long size) {
        assertAdmin();
        LambdaQueryWrapper<TutorRefundTask> q = new LambdaQueryWrapper<>();
        if (status != null) {
            q.eq(TutorRefundTask::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(TutorRefundTask::getJobNo, kw)
                    .or().like(TutorRefundTask::getTeacherName, kw)
                    .or().like(TutorRefundTask::getTeacherPhone, kw));
        }
        // 待退费的排前面，同一状态里新登记的优先处理
        q.orderByAsc(TutorRefundTask::getStatus).orderByDesc(TutorRefundTask::getId);

        Page<TutorRefundTask> p = refundTaskMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorRefundTask t : p.getRecords()) {
            list.add(toTaskMap(t));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        result.put("pending", pendingCount());
        return result;
    }

    /**
     * 办结单张退费工单。
     * <p>
     * 免费单（没有缴费记录或金额为 0）也允许走这里 —— 只把工单标记为已处理，
     * 否则“试课待退费”计数永远清不掉。缴费记录与流水一行都不删。
     */
    @Override
    @Transactional
    public void settleRefundTask(Long taskId, String note) {
        assertAdmin();
        if (taskId == null) throw new BizException("缺少工单ID");
        TutorRefundTask t = refundTaskMapper.selectById(taskId);
        if (t == null) throw new BizException("退费工单不存在");
        if (t.getStatus() != null && t.getStatus() == TutorRefundTask.STATUS_DONE) {
            throw new BizException("该退费工单已办结，不能重复处理");
        }

        TutorJob job = jobMapper.selectById(t.getJobId());
        String jobNo = job != null ? job.getJobNo() : t.getJobNo();
        String subject = job != null ? job.getSubject() : null;

        TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                .eq(TutorJobPayment::getJobId, t.getJobId())
                .eq(TutorJobPayment::getTeacherId, t.getTeacherId()));
        boolean hasMoney = pay != null && pay.getAmount() != null
                && pay.getAmount().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal refunded = hasMoney ? pay.getAmount() : BigDecimal.ZERO;

        if (hasMoney) {
            // 金额清零 + 记退费时间；refund_time 用 UpdateWrapper 显式写，避免 null 跳过问题
            paymentMapper.update(null, new LambdaUpdateWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getId, pay.getId())
                    .set(TutorJobPayment::getAmount, BigDecimal.ZERO)
                    .set(TutorJobPayment::getRefundTime, LocalDateTime.now())
                    .set(TutorJobPayment::getNote, note)
                    .set(TutorJobPayment::getOperatorId, FeignAuthContext.userId()));
            writeLog(t.getJobId(), jobNo, t.getTeacherId(), t.getTeacherName(),
                    TutorPaymentLog.TYPE_REFUND, refunded, note == null ? "试课失败退费" : note);
        }

        t.setStatus(TutorRefundTask.STATUS_DONE);
        t.setAmount(refunded);
        t.setRefundTime(LocalDateTime.now());
        t.setOperatorId(FeignAuthContext.userId());
        t.setNote(note);
        refundTaskMapper.updateById(t);

        audit("REFUND_TASK_DONE", t.getTeacherId(),
                "办结退费工单 #" + taskId + "（家教单 " + jobNo + "），实退 " + refunded + " 元"
                        + (hasMoney ? "" : "（该单无需缴费，仅标记已处理）"));

        String jobDesc = "家教单（" + jobNo + (subject == null ? "" : "·" + subject) + "）";
        String msg = hasMoney
                ? "您对" + jobDesc + "的缴费 " + refunded + " 元已退回，感谢您的参与。"
                : "您对" + jobDesc + "的试课失败已处理完成（该单无需缴费，无退款），感谢您的参与。";
        notifyUser(t.getTeacherId(), "试课退费已完成", msg);
        wechatPusher.push(t.getTeacherId(), "tutor_apply_result", "试课退费已完成", msg);
    }

    @Override
    public Map<String, Object> paymentLogs(Long jobId, Integer type, long page, long size) {
        assertAdmin();
        LambdaQueryWrapper<TutorPaymentLog> q = new LambdaQueryWrapper<>();
        if (jobId != null) q.eq(TutorPaymentLog::getJobId, jobId);
        if (type != null) q.eq(TutorPaymentLog::getType, type);
        q.orderByDesc(TutorPaymentLog::getId);

        Page<TutorPaymentLog> p = paymentLogMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal paidTotal = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        for (TutorPaymentLog l : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("jobId", l.getJobId());
            m.put("jobNo", l.getJobNo());
            m.put("teacherId", l.getTeacherId());
            m.put("teacherName", l.getTeacherName());
            m.put("type", l.getType());
            m.put("amount", l.getAmount());
            m.put("note", l.getNote());
            m.put("operatorName", l.getOperatorName());
            m.put("createTime", l.getCreateTime());
            list.add(m);
            if (l.getAmount() != null) {
                if (l.getType() != null && l.getType() == TutorPaymentLog.TYPE_REFUND) {
                    refundTotal = refundTotal.add(l.getAmount());
                } else {
                    paidTotal = paidTotal.add(l.getAmount());
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        result.put("paidTotal", paidTotal);
        result.put("refundTotal", refundTotal);
        return result;
    }

    // ==================== 教师端 ====================

    @Override
    public Map<String, Object> myRefundTasks() {
        assertTeacher();
        Long teacherId = FeignAuthContext.userId();
        List<TutorRefundTask> tasks = refundTaskMapper.selectList(new LambdaQueryWrapper<TutorRefundTask>()
                .eq(TutorRefundTask::getTeacherId, teacherId)
                .orderByDesc(TutorRefundTask::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        long pending = 0;
        for (TutorRefundTask t : tasks) {
            Map<String, Object> m = toTaskMap(t);
            // 教师端不需要看到自己的电话与办理人，去掉冗余字段
            m.remove("teacherPhone");
            m.remove("operatorName");
            list.add(m);
            if (t.getStatus() != null && t.getStatus() == TutorRefundTask.STATUS_PENDING) pending++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        result.put("pending", pending);
        return result;
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> toTaskMap(TutorRefundTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("jobId", t.getJobId());
        m.put("jobNo", t.getJobNo());
        m.put("teacherId", t.getTeacherId());
        m.put("teacherName", t.getTeacherName());
        m.put("teacherPhone", t.getTeacherPhone());
        m.put("amount", t.getAmount());
        m.put("reason", t.getReason());
        m.put("status", t.getStatus());
        m.put("createTime", t.getCreateTime());
        m.put("refundTime", t.getRefundTime());
        m.put("note", t.getNote());
        // 教师姓名可能在登记时没拿到，这里按用户表补一次，管理端好联系
        if (m.get("teacherName") == null && t.getTeacherId() != null) {
            User u = userMapper.selectById(t.getTeacherId());
            if (u != null) {
                m.put("teacherName", u.getRealName() != null && !u.getRealName().isBlank()
                        ? u.getRealName() : u.getUsername());
            }
        }
        return m;
    }

    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    private void assertTeacher() {
        if (!"teacher".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要教师账号");
        }
    }

    /** 敏感操作留痕（与 TutorJobServiceImpl 同款约定：失败不阻断主流程） */
    private void audit(String action, Long targetUserId, String detail) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            l.setOperatorId(FeignAuthContext.userId());
            l.setOperatorName(FeignAuthContext.username());
            l.setAction(action);
            l.setTargetUserId(targetUserId);
            l.setDetail(detail);
            l.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(l);
        } catch (Exception e) {
            log.error("[审计] 写入失败 action={}：{}", action, e.getMessage());
        }
    }

    /** 给用户发一条站内通知（铃铛可见），失败不影响主流程 */
    private void notifyUser(Long userId, String title, String content) {
        if (userId == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType("system");
            n.setTitle(title);
            n.setContent(content);
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("[退费工单] 站内通知发送失败 userId={}：{}", userId, e.getMessage());
        }
    }
}
