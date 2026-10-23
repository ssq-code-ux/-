package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.AdminJobConfig;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.ParentDemand;
import com.example.englishtutor.entity.TeacherProfile;
import com.example.englishtutor.entity.TutorJob;
import com.example.englishtutor.entity.TutorJobApplication;
import com.example.englishtutor.entity.TutorJobPayment;
import com.example.englishtutor.entity.TutorJobTemplate;
import com.example.englishtutor.entity.TutorPaymentLog;
import com.example.englishtutor.entity.TutorRefundTask;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.AdminJobConfigMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.ParentDemandMapper;
import com.example.englishtutor.mapper.TeacherProfileMapper;
import com.example.englishtutor.mapper.TutorJobMapper;
import com.example.englishtutor.mapper.TutorJobApplicationMapper;
import com.example.englishtutor.mapper.TutorJobPaymentMapper;
import com.example.englishtutor.mapper.TutorJobTemplateMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.InviteCodeService;
import com.example.englishtutor.service.TutorJobService;
import com.example.englishtutor.service.TutorPaymentService;
import com.example.englishtutor.service.WechatNotifyPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 家教信息发布与投递实现。
 * 关键规则：
 * 1. 一个单子可以多人投递（uk_job_teacher 只拦"同一老师重复投同一单"）；
 * 2. 任一投递审核通过 → 该单立即关闭（status=0），教师端不再展示、不再接受投递；
 * 3. 关闭后的单可重新上架，重开时清掉旧的"通过"标记（旧投递状态保留作历史）；
 * 4. 试课失败 → 单子重新开放 + **清空该单全部投递历史**（含附带的老师姓名/电话/附言），
 *    缴费记录一行不动；退费状态搬到 tutor_refund_task 工单表——投递记录都没了，
 *    再靠 trial_status 统计"待退费"就永远是 0，这正是之前那个 bug 的根因；
 * 5. 缴费与退费各写一条 tutor_payment_log 流水（只增不改），时间永久留痕；
 *    tutor_job_payment 只表示"当前状态"，退费后金额清零、时间被覆盖，不能当账本用；
 * 6. jobType：1 家教单、2 同行合作单，共用同一套发布/投递/审核流程，只是分开管理展示。
 * 注意：MyBatis-Plus updateById 默认跳过 null 字段，需要把字段清空时必须用 LambdaUpdateWrapper 显式 set null。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorJobServiceImpl implements TutorJobService {

    private final TutorJobMapper jobMapper;
    private final TutorJobApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final TutorJobPaymentMapper paymentMapper;
    private final TeacherProfileMapper profileMapper;
    private final ParentDemandMapper demandMapper;
    private final WechatNotifyPusher wechatPusher;
    private final TutorJobTemplateMapper templateMapper;
    private final AdminJobConfigMapper jobConfigMapper;
    private final TutorPaymentService paymentService;
    private final InviteCodeService inviteCodeService;

    /** 接单配置是单行表（id=1），教师转发文案里要带上管理端联系方式 */
    private static final int CONFIG_ID = 1;

    // ==================== 教师端 ====================

    @Override
    public Map<String, Object> openJobs(String keyword, String province, String city,
                                       Integer jobType, long page, long size) {
        assertTeacher();
        LambdaQueryWrapper<TutorJob> q = new LambdaQueryWrapper<>();
        q.eq(TutorJob::getStatus, TutorJob.STATUS_OPEN);
        // 接单广场可分开看“家教单”与“同行合作单”；jobType 为空则全部展示
        if (jobType != null) {
            q.eq(TutorJob::getJobType, jobType);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(TutorJob::getSubject, keyword)
                    .or().like(TutorJob::getCity, keyword)
                    .or().like(TutorJob::getProvince, keyword)
                    .or().like(TutorJob::getJobNo, keyword));
        }
        if (province != null && !province.isBlank()) {
            q.eq(TutorJob::getProvince, province);
        }
        if (city != null && !city.isBlank()) {
            q.eq(TutorJob::getCity, city);
        }
        q.orderByDesc(TutorJob::getCreateTime);

        Page<TutorJob> p = jobMapper.selectPage(new Page<>(page, Math.min(size, 50)), q);
        List<Map<String, Object>> list = decorateJobs(p.getRecords(), true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return result;
    }

    @Override
    public void apply(Long jobId, String message) {
        assertTeacher();
        TutorJob job = mustGetJob(jobId);
        if (job.getStatus() == null || job.getStatus() != TutorJob.STATUS_OPEN) {
            throw new BizException("该家教单已关闭，无法投递");
        }
        Long teacherId = FeignAuthContext.userId();

        TutorJobApplication mine = applicationMapper.selectOne(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getJobId, jobId)
                .eq(TutorJobApplication::getTeacherId, teacherId));
        if (mine != null) {
            throw new BizException("您已投递过该家教单，请耐心等待审核");
        }
        // 试课失败后投递记录已被整单清空，这位老师理论上可以重新投；
        // 但他的退费还没办结，先拦住，避免“一边等退钱一边又抢这单”
        if (paymentService.hasPending(jobId, teacherId)) {
            throw new BizException("您在该单的试课退费还在处理中，办结后才能再次投递");
        }

        // 需缴费解锁的单：必须先由管理端标记已缴费（金额>0）才能投递
        if (job.getPayToApply() != null && job.getPayToApply() == 1) {
            TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getJobId, jobId)
                    .eq(TutorJobPayment::getTeacherId, teacherId));
            if (pay == null || pay.getAmount() == null
                    || pay.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new BizException("该家教单需缴费后才能投递，请联系管理员完成缴费解锁");
            }
        }

        User teacher = userMapper.selectById(teacherId);
        TutorJobApplication a = new TutorJobApplication();
        a.setJobId(jobId);
        a.setTeacherId(teacherId);
        a.setTeacherName(teacher != null ? safeName(teacher) : null);
        a.setTeacherPhone(teacher != null ? teacher.getPhone() : null);
        a.setMessage(message);
        a.setStatus(TutorJobApplication.STATUS_PENDING);
        a.setCreateTime(LocalDateTime.now());
        try {
            applicationMapper.insert(a);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_job_teacher 数据库层兜底：并发下同一老师对同一单只可能插入一条
            throw new BizException("您已投递过该家教单，请耐心等待审核");
        }
    }

    @Override
    public Map<String, Object> myApplications(long page, long size) {
        assertTeacher();
        Long teacherId = FeignAuthContext.userId();
        Page<TutorJobApplication> p = applicationMapper.selectPage(
                new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<TutorJobApplication>()
                        .eq(TutorJobApplication::getTeacherId, teacherId)
                        .orderByDesc(TutorJobApplication::getCreateTime));
        List<TutorJobApplication> records = p.getRecords();

        // 批量补齐所属家教单摘要（科目/城市/时薪等）
        Map<Long, TutorJob> jobMap = records.isEmpty() ? Map.of()
                : jobMapper.selectBatchIds(records.stream()
                        .map(TutorJobApplication::getJobId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(TutorJob::getId, j -> j));

        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorJobApplication a : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("jobId", a.getJobId());
            m.put("message", a.getMessage());
            m.put("status", a.getStatus());
            m.put("trialStatus", a.getTrialStatus());
            m.put("createTime", a.getCreateTime());
            m.put("reviewTime", a.getReviewTime());
            TutorJob j = jobMap.get(a.getJobId());
            if (j != null) {
                m.put("jobNo", j.getJobNo());
                m.put("jobType", j.getJobType() == null ? TutorJob.TYPE_TUTOR : j.getJobType());
                m.put("subject", j.getSubject());
                m.put("province", j.getProvince());
                m.put("city", j.getCity());
                m.put("salary", j.getSalary());
                m.put("grade", j.getGrade());
                m.put("jobStatus", j.getStatus());
                // 简历通过后：返回该单对应家长的联系方式（仅通过者可见；待审核/未通过不返回）。
                // 试课失败的记录已被整单删除，不会出现在这个列表里，无需再做额外拦截
                if (a.getStatus() != null && a.getStatus() == TutorJobApplication.STATUS_APPROVED
                        && j.getParentId() != null) {
                    m.put("parentContact", findParentContact(j));
                }
            }
            list.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return result;
    }

    /** 教师确认试课结果：成功→订单完成；失败→单子重开 + 清空该单全部投递历史 + 登记退费工单 */
    @Override
    @Transactional
    public void trialResult(Long applicationId, boolean success) {
        assertTeacher();
        TutorJobApplication a = applicationMapper.selectById(applicationId);
        if (a == null) {
            throw new BizException("投递记录不存在");
        }
        if (!a.getTeacherId().equals(FeignAuthContext.userId())) {
            throw new BizException("无权操作该投递记录");
        }
        if (a.getStatus() == null || a.getStatus() != TutorJobApplication.STATUS_APPROVED) {
            throw new BizException("简历还未通过，暂无试课环节");
        }
        if (a.getTrialStatus() == null || a.getTrialStatus() != TutorJobApplication.TRIAL_IN_PROGRESS) {
            throw new BizException("该订单不在试课中，无法重复确认");
        }
        TutorJob job = mustGetJob(a.getJobId());

        User teacher = userMapper.selectById(a.getTeacherId());
        String teacherName = teacher != null ? safeName(teacher) : "老师#" + a.getTeacherId();
        String jobDesc = "家教单（" + job.getJobNo() + "·" + job.getSubject() + "）";
        if (success) {
            a.setTrialStatus(TutorJobApplication.TRIAL_SUCCESS);
            applicationMapper.updateById(a);
            notifyAdmins("订单已完成",
                    "教师「" + teacherName + "」确认" + jobDesc + "试课成功，该订单已完成。");
            return;
        }

        // 试课失败：**先登记退费工单，再清空投递历史**，顺序不能反 ——
        // 投递记录里冗余着老师姓名/电话，删光之后管理员只能靠工单联系到该退费的老师。
        TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                .eq(TutorJobPayment::getJobId, job.getId())
                .eq(TutorJobPayment::getTeacherId, a.getTeacherId()));
        BigDecimal refundable = (pay == null || pay.getAmount() == null) ? BigDecimal.ZERO : pay.getAmount();
        String teacherPhone = a.getTeacherPhone() != null ? a.getTeacherPhone()
                : (teacher != null ? teacher.getPhone() : null);
        paymentService.createTask(job, a.getTeacherId(), teacherName, teacherPhone, refundable, "教师反馈试课失败");

        // 清空该单**全部**投递历史（含附带的老师姓名/电话/附言）；缴费记录与流水一行都不动
        applicationMapper.delete(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getJobId, job.getId()));
        // 单子重新开放，招聘从头开始
        clearApprovalAndReopen(job.getId());

        boolean free = refundable.compareTo(BigDecimal.ZERO) <= 0;
        audit("TRIAL_FAILED", a.getTeacherId(),
                "教师试课失败，" + jobDesc + " 已重新开放并清空全部投递历史，退费工单待管理端办理");
        notifyAdmins("试课失败，待退费",
                "教师「" + teacherName + "」（电话 " + nvl(teacherPhone) + "）反馈" + jobDesc
                        + "试课失败；该单已重新开放、投递历史已清空，请到「退费工单」为其办理退费"
                        + (free ? "（免费单，仅需标记已处理）" : "（应退 " + refundable + " 元）") + "。");
        notifyUser(a.getTeacherId(), "试课失败已受理，等待退费",
                "您反馈的" + jobDesc + "试课失败已受理，该单投递记录已清空。"
                        + (free ? "该单无需缴费，管理员标记处理完成后会通知您。"
                                : "应退金额 " + refundable + " 元，管理员办结后会再通知您。")
                        + "退费进度可在「我的退费」中查看。");
    }

    /**
     * 教师端转发文案（需求 5）：把单子关键信息拼成一段可直接粘到微信/群里的文字，
     * 末尾附**自己的邀请码**，同行用它注册后邀请关系就落到这位老师名下。
     */
    @Override
    public Map<String, Object> shareText(Long jobId) {
        assertTeacher();
        TutorJob j = mustGetJob(jobId);
        User me = userMapper.selectById(FeignAuthContext.userId());
        String inviteCode = inviteCodeService.ensure(me);

        AdminJobConfig c = jobConfigMapper.selectById(CONFIG_ID);
        boolean partner = j.getJobType() != null && j.getJobType() == TutorJob.TYPE_PARTNER;
        StringBuilder sb = new StringBuilder();
        sb.append(partner ? "【同行合作单】" : "【家教单转发】").append("编号 ").append(j.getJobNo()).append("\n");
        sb.append("科目：").append(nvl(j.getSubject())).append("\n");
        if (j.getGrade() != null) sb.append("年级：").append(j.getGrade()).append("\n");
        StringBuilder area = new StringBuilder();
        if (j.getProvince() != null) area.append(j.getProvince());
        if (j.getCity() != null) area.append(area.length() == 0 ? j.getCity() : " " + j.getCity());
        if (area.length() > 0) sb.append("地区：").append(area).append("\n");
        if (j.getAddress() != null) sb.append("地址：").append(j.getAddress()).append("\n");
        if (j.getFrequency() != null) sb.append("频次：").append(j.getFrequency()).append("\n");
        if (j.getSalary() != null) sb.append("时薪：").append(j.getSalary()).append(" 元/小时\n");
        if (j.getStudentGender() != null) sb.append("学生性别：").append(j.getStudentGender()).append("\n");
        if (j.getExpectGender() != null) sb.append("老师要求：").append(j.getExpectGender()).append("\n");
        if (j.getSituation() != null) sb.append("学生情况：").append(j.getSituation()).append("\n");
        if (j.getRequirement() != null) sb.append("任职要求：").append(j.getRequirement()).append("\n");
        sb.append("——————\n");
        sb.append("有意向的老师请到平台「接单广场」搜索编号 ").append(j.getJobNo()).append(" 投递简历。\n");
        if (c != null && (c.getPhone() != null || c.getWechat() != null)) {
            sb.append("咨询管理员：").append(nvl(c.getPhone()));
            if (c.getWechat() != null) sb.append("（微信：").append(c.getWechat()).append("）");
            sb.append("\n");
        }
        if (inviteCode != null) {
            sb.append("还没注册平台？用我的邀请码 ").append(inviteCode).append(" 注册即可投递。\n");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", sb.toString());
        result.put("inviteCode", inviteCode);
        result.put("jobNo", j.getJobNo());
        return result;
    }

    // ==================== 管理端 ====================

    /** 家教业务待办统计：管理端 Tab 红点与概览条用 */
    @Override
    public Map<String, Object> adminTodo() {
        assertAdmin();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingDemands", demandMapper.selectCount(new LambdaQueryWrapper<ParentDemand>()
                .eq(ParentDemand::getStatus, ParentDemand.STATUS_PENDING)));
        m.put("deleteRequests", demandMapper.selectCount(new LambdaQueryWrapper<ParentDemand>()
                .eq(ParentDemand::getDeleteRequested, 1)));
        m.put("publishedDemands", demandMapper.selectCount(new LambdaQueryWrapper<ParentDemand>()
                .eq(ParentDemand::getStatus, ParentDemand.STATUS_PUBLISHED)));
        m.put("pendingApplications", applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getStatus, TutorJobApplication.STATUS_PENDING)));
        m.put("trialInProgress", applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getTrialStatus, TutorJobApplication.TRIAL_IN_PROGRESS)));
        // 待退费：投递记录已被整单清空，只能从退费工单表统计（这也是之前计数恒为 0 的根因）
        m.put("trialFailed", paymentService.pendingCount());
        m.put("trialSuccess", applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getTrialStatus, TutorJobApplication.TRIAL_SUCCESS)));
        m.put("openJobs", jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>()
                .eq(TutorJob::getStatus, TutorJob.STATUS_OPEN)));
        m.put("closedJobs", jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>()
                .eq(TutorJob::getStatus, TutorJob.STATUS_CLOSED)));
        m.put("payToApplyJobs", jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>()
                .eq(TutorJob::getPayToApply, 1)));
        // 缴费收入：只统计未退费且金额>0 的记录
        List<TutorJobPayment> pays = paymentMapper.selectList(new LambdaQueryWrapper<TutorJobPayment>()
                .isNull(TutorJobPayment::getRefundTime));
        java.math.BigDecimal income = pays.stream()
                .map(TutorJobPayment::getAmount)
                .filter(a -> a != null && a.compareTo(java.math.BigDecimal.ZERO) > 0)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        m.put("paidTeachers", pays.stream()
                .filter(p -> p.getAmount() != null && p.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
                .count());
        m.put("totalIncome", income);
        return m;
    }

    /** 这几个视图得先从投递/退费工单里捞 jobId 才能筛，其余的直接落在 tutor_job.status 上 */
    private static final Set<String> JOB_ID_VIEWS = Set.of("trial", "done", "pendingApply", "refund");

    @Override
    public Map<String, Object> adminJobs(String keyword, Integer status, Integer jobType,
                                         String view, long page, long size) {
        assertAdmin();
        LambdaQueryWrapper<TutorJob> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(TutorJob::getSubject, keyword)
                    .or().like(TutorJob::getCity, keyword)
                    .or().like(TutorJob::getJobNo, keyword));
        }
        // 需求 6：「家教单」与「同行合作单」分两个入口维护，jobType 为空则全部
        if (jobType != null) {
            q.eq(TutorJob::getJobType, jobType);
        }
        // 状态筛选：view 优先于 status。开放中/已关闭就是 tutor_job.status 本身，
        // 而试课中/已完成/待审核投递/待退费取决于投递记录或退费工单，只能先查出 jobId 再 IN
        if (view != null && !view.isBlank()) {
            if ("open".equals(view)) {
                q.eq(TutorJob::getStatus, TutorJob.STATUS_OPEN);
            } else if ("closed".equals(view)) {
                q.eq(TutorJob::getStatus, TutorJob.STATUS_CLOSED);
            } else if (JOB_ID_VIEWS.contains(view)) {
                Set<Long> ids = viewJobIds(view);
                if (ids.isEmpty()) {
                    return emptyJobPage();
                }
                q.in(TutorJob::getId, ids);
            }
            // 认不出的 view 一律忽略，等价于不加状态条件
        } else if (status != null) {
            q.eq(TutorJob::getStatus, status);
        }
        q.orderByDesc(TutorJob::getCreateTime);

        Page<TutorJob> p = jobMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
        List<Map<String, Object>> list = decorateJobs(p.getRecords(), false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return result;
    }

    /** 概览条筛选项 → jobId 集合（试课阶段看已通过的投递，待审核看投递状态，待退费看退费工单） */
    private Set<Long> viewJobIds(String view) {
        if ("refund".equals(view)) {
            return paymentService.allPendingJobIds();
        }
        LambdaQueryWrapper<TutorJobApplication> aq = new LambdaQueryWrapper<>();
        aq.select(TutorJobApplication::getJobId);
        if ("trial".equals(view)) {
            aq.eq(TutorJobApplication::getTrialStatus, TutorJobApplication.TRIAL_IN_PROGRESS);
        } else if ("done".equals(view)) {
            aq.eq(TutorJobApplication::getTrialStatus, TutorJobApplication.TRIAL_SUCCESS);
        } else {
            aq.eq(TutorJobApplication::getStatus, TutorJobApplication.STATUS_PENDING);
        }
        return applicationMapper.selectList(aq).stream()
                .map(TutorJobApplication::getJobId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 空结果页：筛出的 jobId 集合为空时直接返回，免得拼出 IN () 让数据库报语法错 */
    private Map<String, Object> emptyJobPage() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", new ArrayList<>());
        result.put("total", 0L);
        return result;
    }

    @Override
    public Map<String, Object> publish(Map<String, Object> body) {
        assertAdmin();
        TutorJob job = new TutorJob();
        job.setJobNo(genJobNo());
        // 需求 8：body 带 templateId 则先用科目模板预填，再用 body 里的字段覆盖（管理端改过的优先）
        Long templateId = body == null ? null : longOrNull(body.get("templateId"));
        if (templateId != null) {
            applyTemplate(job, templateId);
        }
        fillFromBody(job, body);
        job.setStatus(TutorJob.STATUS_OPEN);
        // 默认收费：未显式关闭缴费解锁的单，教师需缴费后才能投递
        if (job.getPayToApply() == null) job.setPayToApply(1);
        // 默认家教单；同行合作单由管理端显式传 jobType=2
        if (job.getJobType() == null) job.setJobType(TutorJob.TYPE_TUTOR);
        job.setPublisherId(FeignAuthContext.userId());
        job.setPublisherName(FeignAuthContext.username());
        job.setCreateTime(LocalDateTime.now());
        jobMapper.insert(job);

        audit(AdminAuditLog.ACTION_PUBLISH_JOB, null,
                "发布" + jobTypeText(job.getJobType()) + " " + job.getJobNo() + "（" + job.getSubject() + "·" + str(job.getCity()) + "）"
                        + (templateId != null ? "，套用模板 #" + templateId : ""));
        return toJobMap(job, 0L, null);
    }

    /** 把科目模板的字段预填到单子上（随后 fillFromBody 会用 body 里的值覆盖，因此模板只是初值） */
    private void applyTemplate(TutorJob job, Long templateId) {
        TutorJobTemplate t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BizException("所选模板不存在或已被删除");
        }
        job.setSubject(t.getSubject());
        job.setGrade(t.getGrade());
        job.setProvince(t.getProvince());
        job.setCity(t.getCity());
        job.setAddress(t.getAddress());
        job.setStudentGender(t.getStudentGender());
        job.setExpectGender(t.getExpectGender());
        job.setFrequency(t.getFrequency());
        job.setSalary(t.getSalary());
        job.setSituation(t.getSituation());
        job.setRequirement(t.getRequirement());
        job.setPayToApply(t.getPayToApply());
        job.setJobType(t.getJobType() == null ? TutorJob.TYPE_TUTOR : t.getJobType());
    }

    @Override
    public void update(Long jobId, Map<String, Object> body) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        if (job.getStatus() == null || job.getStatus() != TutorJob.STATUS_OPEN) {
            throw new BizException("已关闭的家教单不能编辑，可先重新上架");
        }
        fillFromBody(job, body);
        jobMapper.updateById(job);
        audit(AdminAuditLog.ACTION_UPDATE_JOB, null, "编辑家教单 " + job.getJobNo());
    }

    @Override
    public void close(Long jobId) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        if (job.getStatus() != null && job.getStatus() == TutorJob.STATUS_CLOSED) {
            throw new BizException("该家教单已是关闭状态");
        }
        doClose(job);
        audit(AdminAuditLog.ACTION_CLOSE_JOB, null, "手动下架家教单 " + job.getJobNo());
    }

    @Override
    public void reopen(Long jobId) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        if (job.getStatus() != null && job.getStatus() == TutorJob.STATUS_OPEN) {
            throw new BizException("该家教单已是开放状态");
        }
        // 重开时清掉旧的"通过"标记（单子重新可投递；历史投递记录保留）
        clearApprovalAndReopen(jobId);
        audit(AdminAuditLog.ACTION_REOPEN_JOB, null, "重新上架家教单 " + job.getJobNo());
    }

    /** 把单子重置为"开放中"并清空通过标记（updateById 会跳过 null，清空必须用 UpdateWrapper 显式置空） */
    private void clearApprovalAndReopen(Long jobId) {
        jobMapper.update(null, new LambdaUpdateWrapper<TutorJob>()
                .eq(TutorJob::getId, jobId)
                .set(TutorJob::getStatus, TutorJob.STATUS_OPEN)
                .set(TutorJob::getApprovedTeacherId, null)
                .set(TutorJob::getApprovedApplicationId, null)
                .set(TutorJob::getCloseTime, null));
    }

    @Override
    public Map<String, Object> applications(Long jobId, long page, long size) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId); // 单子必须存在
        Page<TutorJobApplication> p = applicationMapper.selectPage(
                new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<TutorJobApplication>()
                        .eq(TutorJobApplication::getJobId, jobId)
                        .orderByAsc(TutorJobApplication::getCreateTime));

        // 附上教师简历（投递时自动携带）与缴费情况
        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorJobApplication a : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("jobId", a.getJobId());
            m.put("teacherId", a.getTeacherId());
            m.put("teacherName", a.getTeacherName());
            m.put("teacherPhone", a.getTeacherPhone());
            m.put("message", a.getMessage());
            m.put("status", a.getStatus());
            m.put("trialStatus", a.getTrialStatus());
            m.put("createTime", a.getCreateTime());
            m.put("reviewTime", a.getReviewTime());
            m.put("profile", profileMapper.selectById(a.getTeacherId()));
            TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getJobId, jobId)
                    .eq(TutorJobPayment::getTeacherId, a.getTeacherId()));
            m.put("paidAmount", pay == null ? null : pay.getAmount());
            list.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        // 管理端可见该单对应家长的联系方式（教师端看不到）
        result.put("parentContact", job.getParentId() == null ? null : findParentContact(job));
        return result;
    }

    @Override
    public Map<String, Object> adminJobDetail(Long jobId) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        // 投递历史会在试课失败时被整单清空，剩下的就是当前真实投递数，无需再排除任何试课状态
        Long applyCount = applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getJobId, jobId));
        Map<String, Object> m = toJobMap(job, applyCount, null);
        m.put("parentContact", job.getParentId() == null ? null : findParentContact(job));
        // 该单是否还欠着退费工单（单子已重开、投递已清空，光看单子状态看不出来）
        m.put("refundPending", !paymentService.pendingJobIds(Set.of(jobId)).isEmpty());
        // 已成交的单：附上试课状态与通过老师信息
        if (job.getApprovedApplicationId() != null) {
            TutorJobApplication ap = applicationMapper.selectById(job.getApprovedApplicationId());
            if (ap != null) {
                m.put("trialStatus", ap.getTrialStatus());
                m.put("approvedTeacherName", ap.getTeacherName());
                m.put("approvedTeacherPhone", ap.getTeacherPhone());
            }
        }
        return m;
    }

    @Override
    public void review(Long applicationId, boolean approved) {
        assertAdmin();
        TutorJobApplication a = applicationMapper.selectById(applicationId);
        if (a == null) {
            throw new BizException("投递记录不存在");
        }
        if (a.getStatus() != null && a.getStatus() != TutorJobApplication.STATUS_PENDING) {
            throw new BizException("该投递已审核过，不能重复审核");
        }
        TutorJob job = mustGetJob(a.getJobId());

        if (approved) {
            // 通过 → 该单立即关闭，不再开放
            if (job.getStatus() != null && job.getStatus() == TutorJob.STATUS_OPEN) {
                job.setApprovedTeacherId(a.getTeacherId());
                job.setApprovedApplicationId(a.getId());
                doClose(job);
            }
            a.setStatus(TutorJobApplication.STATUS_APPROVED);
            a.setTrialStatus(TutorJobApplication.TRIAL_IN_PROGRESS);
            a.setReviewTime(LocalDateTime.now());
            applicationMapper.updateById(a);

            // 通知老师：简历通过后直接给出家长联系方式（有家长关联的单）
            Map<String, Object> pc = findParentContact(job);
            String contactTip = pc == null
                    ? "请留意管理员与您联系对接后续事宜。"
                    : "家长联系方式：" + pc.get("contactName") + " " + pc.get("phone")
                            + (pc.get("wechat") != null ? "（微信：" + pc.get("wechat") + "）" : "")
                            + "，请尽快与家长沟通对接。";
            notifyUser(a.getTeacherId(), "投递已通过",
                    "您投递的家教单（" + job.getJobNo() + "·" + job.getSubject() + "）已通过审核，"
                            + contactTip);
            // 微信推送（旁路）：老师配置过推送令牌的话，简历通过直接推到微信
            wechatPusher.push(a.getTeacherId(), "tutor_apply_result", "投递已通过",
                    "您投递的家教单（" + job.getJobNo() + "·" + job.getSubject() + "）已通过审核，" + contactTip);
            audit(AdminAuditLog.ACTION_REVIEW_APPLICATION, a.getTeacherId(),
                    "通过投递 #" + applicationId + "（家教单 " + job.getJobNo() + " 已关闭）");
        } else {
            a.setStatus(TutorJobApplication.STATUS_REJECTED);
            a.setReviewTime(LocalDateTime.now());
            applicationMapper.updateById(a);
            notifyUser(a.getTeacherId(), "投递未通过",
                    "很遗憾，您投递的家教单（" + job.getJobNo() + "·" + job.getSubject() + "）未能通过审核，"
                            + "欢迎继续投递其他家教单。");
            audit(AdminAuditLog.ACTION_REVIEW_APPLICATION, a.getTeacherId(),
                    "拒绝投递 #" + applicationId + "（家教单 " + job.getJobNo() + "）");
        }
    }

    // ==================== 缴费管理（管理端） ====================

    @Override
    public void markPaid(Long jobId, Long teacherId, BigDecimal amount, String note) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        if (teacherId == null) throw new BizException("缺少教师ID");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("缴费金额必须大于 0");
        }
        User t = userMapper.selectById(teacherId);
        String teacherName = t != null ? safeName(t) : null;
        TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                .eq(TutorJobPayment::getJobId, jobId)
                .eq(TutorJobPayment::getTeacherId, teacherId));
        LocalDateTime now = LocalDateTime.now();
        if (pay == null) {
            pay = new TutorJobPayment();
            pay.setJobId(jobId);
            pay.setTeacherId(teacherId);
            pay.setAmount(amount);
            pay.setPaidTime(now);
            pay.setNote(note);
            pay.setOperatorId(FeignAuthContext.userId());
            paymentMapper.insert(pay);
        } else {
            // 重新缴费要把上一次的退费时间清掉；updateById 默认跳过 null，必须用 UpdateWrapper 显式 set null
            paymentMapper.update(null, new LambdaUpdateWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getId, pay.getId())
                    .set(TutorJobPayment::getAmount, amount)
                    .set(TutorJobPayment::getPaidTime, now)
                    .set(TutorJobPayment::getRefundTime, null)
                    .set(TutorJobPayment::getNote, note)
                    .set(TutorJobPayment::getOperatorId, FeignAuthContext.userId()));
        }
        // 需求 2：缴费时间永久留痕（payment 表的 paid_time 会被下一次缴费覆盖，当不了账本）
        paymentService.writeLog(jobId, job.getJobNo(), teacherId, teacherName,
                TutorPaymentLog.TYPE_PAY, amount, note);
        audit("MARK_PAID", teacherId,
                "标记教师缴费 " + amount + " 元（家教单 " + job.getJobNo() + "，解锁投递）");
        notifyUser(teacherId, "缴费确认，已解锁投递",
                "管理员已确认您对家教单（" + job.getJobNo() + "·" + job.getSubject()
                        + "）的缴费 " + amount + " 元，现在可以投递简历了。");
    }

    /** 管理端退费（按“单+教师”）：金额清零、写退费流水，并办结该教师本单的待退费工单。
     *  免费单（没有缴费记录）也允许走这里 —— 只把工单标记已处理，避免“待退费”计数永远清不掉。 */
    @Override
    @Transactional
    public void refund(Long jobId, Long teacherId, String note) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        if (teacherId == null) throw new BizException("缺少教师ID");
        TutorJobPayment pay = paymentMapper.selectOne(new LambdaQueryWrapper<TutorJobPayment>()
                .eq(TutorJobPayment::getJobId, jobId)
                .eq(TutorJobPayment::getTeacherId, teacherId));
        boolean hasMoney = pay != null && pay.getAmount() != null
                && pay.getAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean hasTask = paymentService.hasPending(jobId, teacherId);
        if (!hasMoney && !hasTask) {
            throw new BizException("该教师此单没有待退的缴费记录");
        }
        BigDecimal refunded = hasMoney ? pay.getAmount() : BigDecimal.ZERO;
        if (hasMoney) {
            paymentMapper.update(null, new LambdaUpdateWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getId, pay.getId())
                    .set(TutorJobPayment::getAmount, BigDecimal.ZERO)
                    .set(TutorJobPayment::getRefundTime, LocalDateTime.now())
                    .set(TutorJobPayment::getNote, note)
                    .set(TutorJobPayment::getOperatorId, FeignAuthContext.userId()));
            User t = userMapper.selectById(teacherId);
            paymentService.writeLog(jobId, job.getJobNo(), teacherId, t != null ? safeName(t) : null,
                    TutorPaymentLog.TYPE_REFUND, refunded, note == null ? "管理端退费" : note);
        }
        // 办结该教师本单的退费工单（投递记录已被清空，工单是退费状态的唯一载体）
        int settled = paymentService.settleTasksFor(jobId, teacherId, refunded, note);

        audit("REFUND_PAID", teacherId,
                "退费 " + refunded + " 元并清零（家教单 " + job.getJobNo() + "）"
                        + (settled > 0 ? "，办结退费工单 " + settled + " 张" : ""));
        String jobDesc = "家教单（" + job.getJobNo() + "·" + job.getSubject() + "）";
        if (settled > 0) {
            // 试课失败退费：站内 + 微信各一条
            String msg = hasMoney
                    ? "您对" + jobDesc + "的缴费 " + refunded + " 元已退回，感谢您的参与。"
                    : "您对" + jobDesc + "的试课失败已处理完成（该单无需缴费，无退款），感谢您的参与。";
            notifyUser(teacherId, "试课退费已完成", msg);
            wechatPusher.push(teacherId, "tutor_apply_result", "试课退费已完成", msg);
        } else {
            notifyUser(teacherId, "缴费已退回",
                    "您对" + jobDesc + "的缴费 " + refunded + " 元已退回，已缴金额已清零。");
        }
    }

    @Override
    public Map<String, Object> payments(Long jobId) {
        assertAdmin();
        mustGetJob(jobId);
        List<TutorJobPayment> pays = paymentMapper.selectList(
                new LambdaQueryWrapper<TutorJobPayment>()
                        .eq(TutorJobPayment::getJobId, jobId)
                        .orderByDesc(TutorJobPayment::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorJobPayment pay : pays) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pay.getId());
            m.put("jobId", pay.getJobId());
            m.put("teacherId", pay.getTeacherId());
            User u = userMapper.selectById(pay.getTeacherId());
            m.put("teacherName", u != null ? safeName(u) : null);
            m.put("amount", pay.getAmount());
            m.put("paidTime", pay.getPaidTime());
            m.put("refundTime", pay.getRefundTime());
            m.put("note", pay.getNote());
            m.put("createTime", pay.getCreateTime());
            list.add(m);
        }
        return Map.of("list", list, "total", list.size());
    }

    /** 管理端：彻底删除家教单（连带投递记录与缴费记录；关联的家长需求回退为"待发布"，需求本身保留） */
    @Override
    @Transactional
    public void adminDeleteJob(Long jobId) {
        assertAdmin();
        TutorJob job = mustGetJob(jobId);
        long apps = applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getJobId, jobId));
        applicationMapper.delete(new LambdaQueryWrapper<TutorJobApplication>()
                .eq(TutorJobApplication::getJobId, jobId));
        paymentMapper.delete(new LambdaQueryWrapper<TutorJobPayment>()
                .eq(TutorJobPayment::getJobId, jobId));
        // 退费工单与缴费/退费流水一并清理，不留孤儿数据
        paymentService.deleteByJobId(jobId);
        jobMapper.deleteById(jobId);
        // 关联的家长需求回退为待发布（job_id / publish_time 要置空，updateById 会跳过 null，须用 UpdateWrapper）
        if (job.getParentDemandId() != null) {
            demandMapper.update(null, new LambdaUpdateWrapper<ParentDemand>()
                    .eq(ParentDemand::getId, job.getParentDemandId())
                    .eq(ParentDemand::getJobId, jobId)
                    .set(ParentDemand::getJobId, null)
                    .set(ParentDemand::getPublishTime, null)
                    .set(ParentDemand::getStatus, ParentDemand.STATUS_PENDING));
        }
        audit("DELETE_JOB", null, "删除家教单 " + job.getJobNo() + "（连带 " + apps + " 条投递与缴费记录）");
    }

    // ==================== 内部工具 ====================

    private void assertTeacher() {
        if (!"teacher".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要教师账号");
        }
    }

    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    private TutorJob mustGetJob(Long id) {
        TutorJob j = jobMapper.selectById(id);
        if (j == null) {
            throw new BizException("家教单不存在");
        }
        return j;
    }

    /** 统一的关闭动作：置状态 + 记关闭时间 */
    private void doClose(TutorJob job) {
        job.setStatus(TutorJob.STATUS_CLOSED);
        job.setCloseTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    /** 生成 6 位数字单号（日期前缀做种 + 随机，冲突时重试） */
    private String genJobNo() {
        Random rnd = new Random();
        for (int i = 0; i < 5; i++) {
            String no = String.format("%06d", rnd.nextInt(1000000));
            Long dup = jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>()
                    .eq(TutorJob::getJobNo, no));
            if (dup == 0) {
                return no;
            }
        }
        // 极小概率连续冲突：退化为毫秒时间戳后 6 位
        return String.format("%06d", System.currentTimeMillis() % 1000000);
    }

    /** 从请求体填充家教单字段（发布和编辑共用；null 字段保留原值） */
    private void fillFromBody(TutorJob job, Map<String, Object> body) {
        if (body == null) return;
        if (body.containsKey("subject")) {
            String v = str(body.get("subject"));
            if (v == null || v.isBlank()) throw new BizException("科目不能为空");
            job.setSubject(v.trim());
        }
        if (body.containsKey("province")) job.setProvince(str(body.get("province")));
        if (body.containsKey("city")) job.setCity(str(body.get("city")));
        if (body.containsKey("address")) job.setAddress(str(body.get("address")));
        if (body.containsKey("grade")) job.setGrade(str(body.get("grade")));
        if (body.containsKey("studentGender")) job.setStudentGender(str(body.get("studentGender")));
        if (body.containsKey("expectGender")) job.setExpectGender(str(body.get("expectGender")));
        if (body.containsKey("frequency")) job.setFrequency(str(body.get("frequency")));
        if (body.containsKey("situation")) job.setSituation(str(body.get("situation")));
        if (body.containsKey("requirement")) job.setRequirement(str(body.get("requirement")));
        if (body.containsKey("salary") && body.get("salary") != null) {
            try {
                job.setSalary(Integer.parseInt(String.valueOf(body.get("salary"))));
            } catch (NumberFormatException e) {
                throw new BizException("时薪必须是数字（元/小时）");
            }
        }
        if (body.containsKey("payToApply") && body.get("payToApply") != null) {
            job.setPayToApply(Integer.parseInt(String.valueOf(body.get("payToApply"))) == 1 ? 1 : 0);
        }
        // 需求 6：只认 2（同行合作单），其余一律归为家教单，避免前端传错值把单子搞成未知类型
        if (body.containsKey("jobType") && body.get("jobType") != null) {
            job.setJobType(intOrNull(body.get("jobType")) != null
                    && intOrNull(body.get("jobType")) == TutorJob.TYPE_PARTNER
                    ? TutorJob.TYPE_PARTNER : TutorJob.TYPE_TUTOR);
        }
    }

    /** 列表装饰：批量补每单投递数；教师端再补“当前老师是否已投”与“是否已缴费解锁” */
    private List<Map<String, Object>> decorateJobs(List<TutorJob> records, boolean forTeacher) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> jobIds = records.stream().map(TutorJob::getId).collect(Collectors.toSet());
        List<TutorJobApplication> all = applicationMapper.selectList(
                new LambdaQueryWrapper<TutorJobApplication>().in(TutorJobApplication::getJobId, jobIds));
        Map<Long, Long> countByJob = all.stream()
                .collect(Collectors.groupingBy(TutorJobApplication::getJobId, Collectors.counting()));
        Long me = forTeacher ? FeignAuthContext.userId() : null;
        Set<Long> myJobIds = (forTeacher && !all.isEmpty())
                ? all.stream().filter(a -> Objects.equals(a.getTeacherId(), me))
                        .map(TutorJobApplication::getJobId).collect(Collectors.toSet())
                : Set.of();
        // 管理端：批量取“已通过投递”的试课状态，列表可直接显示试课中/已完成/待退费
        Map<Long, Integer> trialByApp = Map.of();
        if (!forTeacher) {
            Set<Long> appIds = records.stream().map(TutorJob::getApprovedApplicationId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!appIds.isEmpty()) {
                trialByApp = applicationMapper.selectBatchIds(appIds).stream()
                        .filter(a -> a.getTrialStatus() != null)
                        .collect(Collectors.toMap(TutorJobApplication::getId, TutorJobApplication::getTrialStatus));
            }
        }
        // 管理端：标出“挂着待退费工单”的单 —— 试课失败后单子会重开、投递被清空，
        // 光看单子状态是看不出它还欠着一笔退费的，列表上要能一眼认出来
        Set<Long> refundPendingJobIds = forTeacher ? Set.of() : paymentService.pendingJobIds(jobIds);
        // 教师端：批量查我已缴费解锁的单（金额>0 视为已缴）
        Set<Long> paidJobIds = Set.of();
        if (forTeacher) {
            List<TutorJobPayment> pays = paymentMapper.selectList(new LambdaQueryWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getTeacherId, me)
                    .in(TutorJobPayment::getJobId, jobIds));
            paidJobIds = pays.stream()
                    .filter(p -> p.getAmount() != null && p.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
                    .map(TutorJobPayment::getJobId).collect(Collectors.toSet());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorJob j : records) {
            boolean applied = myJobIds.contains(j.getId());
            Map<String, Object> m = toJobMap(j, countByJob.getOrDefault(j.getId(), 0L), applied);
            boolean needPay = j.getPayToApply() != null && j.getPayToApply() == 1;
            m.put("needPay", needPay);
            if (!forTeacher) {
                if (j.getApprovedApplicationId() != null) {
                    m.put("trialStatus", trialByApp.get(j.getApprovedApplicationId()));
                }
                m.put("refundPending", refundPendingJobIds.contains(j.getId()));
            }
            if (forTeacher) {
                m.put("paid", paidJobIds.contains(j.getId()));
                // 教师端不返回任何家长信息
                m.remove("parentId");
                m.remove("parentDemandId");
            }
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> toJobMap(TutorJob j, Long applyCount, Boolean applied) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("jobNo", j.getJobNo());
        m.put("jobType", j.getJobType() == null ? TutorJob.TYPE_TUTOR : j.getJobType());
        m.put("subject", j.getSubject());
        m.put("province", j.getProvince());
        m.put("city", j.getCity());
        m.put("address", j.getAddress());
        m.put("grade", j.getGrade());
        m.put("studentGender", j.getStudentGender());
        m.put("expectGender", j.getExpectGender());
        m.put("frequency", j.getFrequency());
        m.put("salary", j.getSalary());
        m.put("situation", j.getSituation());
        m.put("requirement", j.getRequirement());
        m.put("status", j.getStatus());
        m.put("publisherName", j.getPublisherName());
        m.put("approvedTeacherId", j.getApprovedTeacherId());
        m.put("createTime", j.getCreateTime());
        m.put("closeTime", j.getCloseTime());
        m.put("applyCount", applyCount);
        if (applied != null) {
            m.put("applied", applied);
        }
        m.put("payToApply", j.getPayToApply() == null ? 0 : j.getPayToApply());
        m.put("parentId", j.getParentId());
        m.put("parentDemandId", j.getParentDemandId());
        return m;
    }

    private String safeName(User u) {
        return u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername();
    }

    /**
     * 查该单对应家长的联系方式（仅家长需求生成的单才有）：
     * 优先取 parent_demand 里家长填写的称呼/电话/微信；查不到时回退取家长账号的手机号。
     * 返回 null 表示该单没有关联家长（管理员手工发布的单）。
     */
    private Map<String, Object> findParentContact(TutorJob job) {
        ParentDemand d = null;
        if (job.getParentDemandId() != null) {
            d = demandMapper.selectById(job.getParentDemandId());
        }
        if (d == null && job.getParentId() != null) {
            d = demandMapper.selectOne(new LambdaQueryWrapper<ParentDemand>()
                    .eq(ParentDemand::getParentId, job.getParentId())
                    .orderByDesc(ParentDemand::getId)
                    .last("LIMIT 1"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (d != null) {
            m.put("contactName", d.getContactName());
            m.put("phone", d.getPhone());
            m.put("wechat", d.getWechat());
            m.put("grade", d.getGrade());
            m.put("subject", d.getSubject());
            m.put("studentLevel", d.getStudentLevel());
        } else if (job.getParentId() != null) {
            User parent = userMapper.selectById(job.getParentId());
            if (parent == null) return null;
            m.put("contactName", safeName(parent));
            m.put("phone", parent.getPhone());
            m.put("grade", job.getGrade());
            m.put("subject", job.getSubject());
        } else {
            return null;
        }
        return m;
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 前端可能传数字也可能传字符串，统一转 Long；空/非数字返回 null */
    private Long longOrNull(Object v) {
        String s = str(v);
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrNull(Object v) {
        String s = str(v);
        if (s == null) return null;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 空值占位（转发文案里不能出现 "null"） */
    private String nvl(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private String jobTypeText(Integer jobType) {
        return jobType != null && jobType == TutorJob.TYPE_PARTNER ? "同行合作单" : "家教单";
    }

    /** 敏感操作留痕（与 AdminServiceImpl 同款约定：失败不阻断主流程） */
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
            log.warn("[家教单] 站内通知发送失败 userId={}：{}", userId, e.getMessage());
        }
    }

    /** 给全体管理员发通知（站内铃铛 + 微信推送），失败不影响主流程 */
    private void notifyAdmins(String title, String content) {
        try {
            List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
            for (User adm : admins) {
                notifyUser(adm.getId(), title, content);
                wechatPusher.push(adm.getId(), "tutor_apply_result", title, content);
            }
        } catch (Exception e) {
            log.warn("[家教单] 管理端通知发送失败：{}", e.getMessage());
        }
    }
}
