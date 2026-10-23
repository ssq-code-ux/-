package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.AdminJobConfig;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.ParentDemand;
import com.example.englishtutor.entity.TutorJob;
import com.example.englishtutor.entity.TutorJobApplication;
import com.example.englishtutor.entity.TutorJobPayment;
import com.example.englishtutor.entity.TeacherProfile;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.AdminJobConfigMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.ParentDemandMapper;
import com.example.englishtutor.mapper.TeacherProfileMapper;
import com.example.englishtutor.mapper.TutorJobApplicationMapper;
import com.example.englishtutor.mapper.TutorJobMapper;
import com.example.englishtutor.mapper.TutorJobPaymentMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.ParentTutorService;
import com.example.englishtutor.service.TutorPaymentService;
import com.example.englishtutor.service.WechatNotifyPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 家长找家教实现。
 * 核心链路：家长提交需求 → 管理端发布为家教单（教师端只见单子不�见家长信息）
 * → 教师投递（简历自动附加）→ 家长/管理端均可见 → 家长通过 → 单子关闭 + 展示管理端联系方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentTutorServiceImpl implements ParentTutorService {

    private static final int CONFIG_ID = 1;

    private final ParentDemandMapper demandMapper;
    private final TutorJobMapper jobMapper;
    private final TutorJobApplicationMapper applicationMapper;
    private final TeacherProfileMapper profileMapper;
    private final AdminJobConfigMapper configMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final NotificationMapper notificationMapper;
    private final WechatNotifyPusher wechatPusher;
    private final TutorJobPaymentMapper paymentMapper;
    private final UserMapper userMapper;
    /** 删除需求时要连带清掉关联家教单的退费工单与缴费流水 */
    private final TutorPaymentService paymentService;

    // ==================== 家长端 ====================

    @Override
    public void submitDemand(Map<String, Object> body) {
        assertParent();
        if (body == null) throw new BizException("请求体不能为空");

        // 需求 3：只强制校验手机号；微信号及其余字段全部选填
        String phone = reqStr(body.get("phone"), "请填写手机号");
        if (!phone.matches("\\d{5,20}")) throw new BizException("手机号格式不正确");
        String wechat = optStr(body.get("wechat"));

        ParentDemand d = new ParentDemand();
        d.setParentId(FeignAuthContext.userId());
        d.setContactName(optStr(body.get("contactName")));
        d.setPhone(phone);
        d.setWechat(wechat);
        d.setStudentLevel(optStr(body.get("studentLevel")));
        d.setGrade(optStr(body.get("grade")));
        d.setStudentGender(optStr(body.get("studentGender")));
        d.setSubject(optStr(body.get("subject")));
        d.setExpectGender(optStr(body.get("expectGender")));
        d.setOtherRequire(optStr(body.get("otherRequire")));
        if (body.get("salary") != null && !String.valueOf(body.get("salary")).isBlank()) {
            try {
                d.setSalary(Integer.parseInt(String.valueOf(body.get("salary")).trim()));
            } catch (NumberFormatException e) {
                throw new BizException("时薪必须是数字");
            }
        }
        d.setStatus(ParentDemand.STATUS_PENDING);
        d.setDemandNo(genDemandNo());
        demandMapper.insert(d);
    }

    @Override
    public Map<String, Object> myDemands(int page, int size) {
        assertParent();
        Page<ParentDemand> p = demandMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<ParentDemand>()
                        .eq(ParentDemand::getParentId, FeignAuthContext.userId())
                        .orderByDesc(ParentDemand::getId));
        List<Map<String, Object>> records = p.getRecords().stream().map(d -> {
            Map<String, Object> m = demandBrief(d);
            // 统计关联家教单收到的投递数
            Long applyCount = null;
            String jobStatus = null;
            if (d.getJobId() != null) {
                TutorJob job = jobMapper.selectById(d.getJobId());
                if (job != null) {
                    jobStatus = job.getStatus() == 1 ? "开放中" : "已关闭";
                    applyCount = applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                            .eq(TutorJobApplication::getJobId, d.getJobId()));
                }
            }
            m.put("jobStatus", jobStatus);
            m.put("applyCount", applyCount == null ? 0 : applyCount.intValue());
            return m;
        }).collect(Collectors.toList());
        return Map.of("records", records, "total", p.getTotal());
    }

    @Override
    public Map<String, Object> myDemandApplications(Long demandId) {
        assertParent();
        ParentDemand d = demandMapper.selectById(demandId);
        if (d == null || !d.getParentId().equals(FeignAuthContext.userId())) {
            throw new BizException("需求单不存在");
        }
        if (d.getJobId() == null) throw new BizException("该需求还未发布，暂无教师投递");

        List<TutorJobApplication> apps = applicationMapper.selectList(
                new LambdaQueryWrapper<TutorJobApplication>()
                        .eq(TutorJobApplication::getJobId, d.getJobId())
                        .orderByDesc(TutorJobApplication::getId));
        return Map.of("records", apps.stream().map(this::appToMap).collect(Collectors.toList()), "total", apps.size());
    }

    @Override
    @Transactional
    public Map<String, Object> reviewApplication(Long applicationId, boolean approved) {
        assertParent();
        TutorJobApplication app = applicationMapper.selectById(applicationId);
        if (app == null) throw new BizException("投递记录不存在");
        TutorJob job = jobMapper.selectById(app.getJobId());
        if (job == null || job.getParentId() == null
                || !job.getParentId().equals(FeignAuthContext.userId())) {
            throw new BizException("无权操作该投递记录");
        }
        if (app.getStatus() != TutorJobApplication.STATUS_PENDING) {
            throw new BizException("该投递已处理过，不能重复操作");
        }

        if (approved) {
            app.setStatus(TutorJobApplication.STATUS_APPROVED);
            app.setTrialStatus(TutorJobApplication.TRIAL_IN_PROGRESS);
            job.setApprovedTeacherId(app.getTeacherId());
            job.setApprovedApplicationId(app.getId());
            job.setStatus(TutorJob.STATUS_CLOSED);
            job.setCloseTime(LocalDateTime.now());
            jobMapper.updateById(job);
        } else {
            app.setStatus(TutorJobApplication.STATUS_REJECTED);
        }
        app.setReviewTime(LocalDateTime.now());
        applicationMapper.updateById(app);

        // 站内通知老师审核结果（失败不影响主流程）；通过时直接带上家长联系方式
        TutorJob jobForNotify = job;
        String teacherTip = "管理员会尽快与您联系对接上课事宜。";
        if (approved && job.getParentDemandId() != null) {
            ParentDemand dForNotify = demandMapper.selectById(job.getParentDemandId());
            if (dForNotify != null) {
                // 需求 3 之后称呼可能为空，拼联系方式时不能出现 "null"
                teacherTip = "家长联系方式：" + orDefault(dForNotify.getContactName(), "家长") + " " + dForNotify.getPhone()
                        + (dForNotify.getWechat() != null ? "（微信：" + dForNotify.getWechat() + "）" : "")
                        + "，请尽快与家长沟通对接。";
            }
        }
        try {
            Notification n = new Notification();
            n.setUserId(app.getTeacherId());
            n.setType("system");
            n.setTitle(approved ? "简历已通过" : "简历未通过");
            n.setContent(approved
                    ? "家长已通过您对家教单（" + jobForNotify.getJobNo() + "·" + jobForNotify.getSubject()
                            + "）的投递，" + teacherTip
                    : "很遗憾，您投递的家教单（" + jobForNotify.getJobNo() + "·" + jobForNotify.getSubject()
                            + "）未被家长选中，欢迎继续投递其他家教单。");
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("[家长找家教] 站内通知发送失败 teacherId={}：{}", app.getTeacherId(), e.getMessage());
        }

        // 微信推送（旁路）：老师配置过推送令牌的话，简历通过直接推到微信
        if (approved) {
            wechatPusher.push(app.getTeacherId(), "tutor_apply_result", "简历已通过",
                    "家长已通过您对家教单（" + job.getJobNo() + "·" + job.getSubject() + "）的投递，" + teacherTip);
        }

        // 通过后返回管理端联系方式（家长弹窗展示）
        Map<String, Object> result = new LinkedHashMap<>();
        if (approved) {
            AdminJobConfig c = configMapper.selectById(CONFIG_ID);
            if (c != null) {
                result.put("adminPhone", c.getPhone());
                result.put("adminWechat", c.getWechat());
            }
        }
        return result;
    }

    @Override
    public void requestDeleteDemand(Long demandId) {
        assertParent();
        ParentDemand d = demandMapper.selectById(demandId);
        if (d == null || !d.getParentId().equals(FeignAuthContext.userId())) {
            throw new BizException("需求单不存在");
        }
        if (d.getDeleteRequested() != null && d.getDeleteRequested() == 1) {
            throw new BizException("已提交删除申请，请等待管理端确认");
        }
        d.setDeleteRequested(1);
        demandMapper.updateById(d);
        String jobDesc = d.getJobId() != null ? "（已发布，关联家教单将一并删除）" : "（尚未发布）";
        notifyAdmins("家长申请删除需求",
                "家长「" + orDefault(d.getContactName(), "家长") + "」（电话 " + d.getPhone() + "）申请删除找家教需求（"
                        + orDefault(d.getSubject(), "—") + "·" + orDefault(d.getGrade(), "—") + "）"
                        + jobDesc + "，请到【家长需求】确认处理。");
    }

    // ==================== �管理端 ====================

    @Override
    public Map<String, Object> adminDemands(String keyword, int status, Integer deleteRequested, int page, int size) {
        assertAdmin();
        LambdaQueryWrapper<ParentDemand> qw = new LambdaQueryWrapper<ParentDemand>()
                // 申请删除的排最前，管理员优先处理
                .orderByDesc(ParentDemand::getDeleteRequested)
                .orderByDesc(ParentDemand::getId);
        // 关键词：订单号（= 需求编号）/ 家长称呼 / 电话 / 微信 —— 搜到需求单就能拿到家长信息
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // 兼容存量数据：旧的家教单单号可能与需求编号不一致，额外按关联家教单的 job_no 反查
            List<Long> idsByJobNo = jobMapper.selectList(new LambdaQueryWrapper<TutorJob>()
                            .select(TutorJob::getParentDemandId)
                            .like(TutorJob::getJobNo, kw)
                            .isNotNull(TutorJob::getParentDemandId))
                    .stream().map(TutorJob::getParentDemandId).distinct().collect(Collectors.toList());
            qw.and(w -> {
                w.like(ParentDemand::getDemandNo, kw)
                        .or().like(ParentDemand::getContactName, kw)
                        .or().like(ParentDemand::getPhone, kw)
                        .or().like(ParentDemand::getWechat, kw);
                if (!idsByJobNo.isEmpty()) w.or().in(ParentDemand::getId, idsByJobNo);
            });
        }
        if (status >= 0) qw.eq(ParentDemand::getStatus, status);
        if (deleteRequested != null) qw.eq(ParentDemand::getDeleteRequested, deleteRequested);
        Page<ParentDemand> p = demandMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 50)), qw);
        List<Map<String, Object>> records = p.getRecords().stream().map(d -> {
            Map<String, Object> m = demandBrief(d);
            // 补关联家教单的单号/状态/投递数，管理端表格直接可见
            if (d.getJobId() != null) {
                TutorJob job = jobMapper.selectById(d.getJobId());
                if (job != null) {
                    m.put("jobNo", job.getJobNo());
                    m.put("jobStatus", job.getStatus() != null && job.getStatus() == TutorJob.STATUS_OPEN ? "开放中" : "已关闭");
                    // 试课失败会把投递历史整单清空，剩下的就是当前真实投递数
                    m.put("applyCount", applicationMapper.selectCount(new LambdaQueryWrapper<TutorJobApplication>()
                            .eq(TutorJobApplication::getJobId, d.getJobId())).intValue());
                }
            }
            return m;
        }).collect(Collectors.toList());
        return Map.of("records", records, "total", p.getTotal());
    }

    @Override
    public Map<String, Object> adminDemandDetail(Long demandId) {
        assertAdmin();
        ParentDemand d = demandMapper.selectById(demandId);
        if (d == null) throw new BizException("需求单不存在");
        Map<String, Object> m = demandFull(d);
        if (d.getJobId() != null) {
            TutorJob job = jobMapper.selectById(d.getJobId());
            if (job != null) {
                Map<String, Object> jm = new LinkedHashMap<>();
                jm.put("id", job.getId());
                jm.put("jobNo", job.getJobNo());
                jm.put("subject", job.getSubject());
                jm.put("status", job.getStatus());
                jm.put("province", job.getProvince());
                jm.put("city", job.getCity());
                jm.put("address", job.getAddress());
                jm.put("salary", job.getSalary());
                m.put("job", jm);
            }
        }
        return m;
    }

    @Override
    @Transactional
    public Map<String, Object> adminPublishDemand(Long demandId, Map<String, Object> body) {
        assertAdmin();
        ParentDemand d = demandMapper.selectById(demandId);
        if (d == null) throw new BizException("需求单不存在");
        if (d.getStatus() == ParentDemand.STATUS_PUBLISHED && d.getJobId() != null) {
            throw new BizException("该需求已发布过家教单（单号见列表），不能重复发布");
        }
        if (body == null) throw new BizException("请求体不能为空");

        // 生成家教单（教师端可见，不含家长任何信息）
        TutorJob job = new TutorJob();
        // 需求编号即订单号：家长需求与它发布出来的家教单共用同一个编号，管理端两边能直接对上
        String jobNo = demandNoOf(d);
        if (jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>().eq(TutorJob::getJobNo, jobNo)) > 0) {
            throw new BizException("编号 " + jobNo + " 的家教单已存在，请先删除旧单再重新发布");
        }
        job.setJobNo(jobNo);
        job.setSubject(reqStr(orDefault(body.get("subject"), d.getSubject()), "请填写科目"));
        job.setProvince(optStr(body.get("province")));
        job.setCity(optStr(body.get("city")));
        job.setAddress(optStr(body.get("address")));
        job.setGrade(optStr(orDefault(body.get("grade"), d.getGrade())));
        job.setStudentGender(optStr(orDefault(body.get("studentGender"), d.getStudentGender())));
        job.setExpectGender(optStr(orDefault(body.get("expectGender"), d.getExpectGender())));
        job.setFrequency(optStr(body.get("frequency")));
        if (body.get("salary") != null && !String.valueOf(body.get("salary")).isBlank()) {
            try {
                job.setSalary(Integer.parseInt(String.valueOf(body.get("salary")).trim()));
            } catch (NumberFormatException e) {
                throw new BizException("时薪必须是数字");
            }
        } else {
            job.setSalary(d.getSalary());
        }
        job.setSituation(optStr(orDefault(body.get("situation"), d.getStudentLevel())));
        job.setRequirement(optStr(orDefault(body.get("requirement"), d.getOtherRequire())));
        job.setStatus(TutorJob.STATUS_OPEN);
        job.setPublisherId(FeignAuthContext.userId());
        job.setPublisherName(FeignAuthContext.username());
        job.setParentId(d.getParentId());
        job.setParentDemandId(d.getId());
        // 默认收费：未显式关闭缴费解锁的需求单，发布后教师需缴费才能投递
        Object pta = body.get("payToApply");
        job.setPayToApply(pta == null ? 1 : (Integer.parseInt(String.valueOf(pta)) == 1 ? 1 : 0));
        // 需求 6：家长需求发布出来的单也支持指定为同行合作单，默认家教单
        job.setJobType(body.get("jobType") != null
                && Integer.parseInt(String.valueOf(body.get("jobType"))) == TutorJob.TYPE_PARTNER
                ? TutorJob.TYPE_PARTNER : TutorJob.TYPE_TUTOR);
        jobMapper.insert(job);

        // 回写需求单
        d.setStatus(ParentDemand.STATUS_PUBLISHED);
        d.setJobId(job.getId());
        d.setPublishTime(LocalDateTime.now());
        demandMapper.updateById(d);

        audit("PUBLISH_JOB", "发布家长需求为家教单，demandId=" + demandId + "，jobNo=" + job.getJobNo());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId());
        result.put("jobNo", job.getJobNo());
        return result;
    }

    @Override
    @Transactional
    public void adminConfirmDeleteDemand(Long demandId) {
        assertAdmin();
        ParentDemand d = demandMapper.selectById(demandId);
        if (d == null) throw new BizException("需求单不存在");
        // 连带删除：关联家教单 → 投递记录 → 缴费记录 → 退费工单/缴费流水 → 需求单
        if (d.getJobId() != null) {
            applicationMapper.delete(new LambdaQueryWrapper<TutorJobApplication>()
                    .eq(TutorJobApplication::getJobId, d.getJobId()));
            paymentMapper.delete(new LambdaQueryWrapper<TutorJobPayment>()
                    .eq(TutorJobPayment::getJobId, d.getJobId()));
            paymentService.deleteByJobId(d.getJobId());
            jobMapper.deleteById(d.getJobId());
        }
        demandMapper.deleteById(demandId);
        audit("DELETE_DEMAND", "确认删除家长需求 demandId=" + demandId + "（" + orDefault(d.getSubject(), "—") + "）"
                + (d.getJobId() != null ? "，连带删除家教单 jobId=" + d.getJobId() + " 及其投递/缴费/退费记录" : ""));
    }

    // ==================== 内部工具 ====================

    private void assertParent() {
        if (!"parent".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要家长账号");
        }
    }

    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    /** 投递记录 → 家长端视图（附教师简历） */
    private Map<String, Object> appToMap(TutorJobApplication app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", app.getId());
        m.put("jobId", app.getJobId());
        m.put("teacherId", app.getTeacherId());
        m.put("teacherName", app.getTeacherName());
        m.put("message", app.getMessage());
        m.put("status", app.getStatus());
        m.put("trialStatus", app.getTrialStatus());
        m.put("createTime", app.getCreateTime());
        TeacherProfile p = profileMapper.selectById(app.getTeacherId());
        m.put("profile", p);
        return m;
    }

    /** 需求单简况（不含联系方式，家长端"我的需求"列表 & 管理端列表共用） */
    private Map<String, Object> demandBrief(ParentDemand d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("demandNo", d.getDemandNo());
        m.put("parentId", d.getParentId());
        m.put("contactName", d.getContactName());
        m.put("phone", d.getPhone());
        m.put("wechat", d.getWechat());
        m.put("studentLevel", d.getStudentLevel());
        m.put("grade", d.getGrade());
        m.put("studentGender", d.getStudentGender());
        m.put("subject", d.getSubject());
        m.put("expectGender", d.getExpectGender());
        m.put("otherRequire", d.getOtherRequire());
        m.put("salary", d.getSalary());
        m.put("status", d.getStatus());
        m.put("deleteRequested", d.getDeleteRequested());
        m.put("jobId", d.getJobId());
        m.put("createTime", d.getCreateTime());
        m.put("publishTime", d.getPublishTime());
        return m;
    }

    /** 需求单全量（管理端详情） */
    private Map<String, Object> demandFull(ParentDemand d) {
        return demandBrief(d);
    }

    /** 单号：6位随机数字，查重重试5次（仅管理员手工发布的单用；家长需求发布时直接用需求编号当单号） */
    private String genJobNo() {
        for (int i = 0; i < 5; i++) {
            String no = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
            Long c = jobMapper.selectCount(new LambdaQueryWrapper<TutorJob>().eq(TutorJob::getJobNo, no));
            if (c == 0) return no;
        }
        return String.valueOf(System.currentTimeMillis()).substring(7);
    }

    /** 需求单编号：XQ + 6位随机数字，查重重试5次（兼容并发：数据库 uk_demand_no 兼底） */
    private String genDemandNo() {
        for (int i = 0; i < 5; i++) {
            String no = "XQ" + String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
            Long c = demandMapper.selectCount(new LambdaQueryWrapper<ParentDemand>()
                    .eq(ParentDemand::getDemandNo, no));
            if (c == 0) return no;
        }
        return "XQ" + (System.currentTimeMillis() % 1000000000L);
    }

    /** 取需求单编号（存量数据可能还没编号，补生成并落库）；发布家教单时直接拿它当单号，保证两边一致 */
    private String demandNoOf(ParentDemand d) {
        if (d.getDemandNo() != null && !d.getDemandNo().isBlank()) return d.getDemandNo();
        String no = genDemandNo();
        d.setDemandNo(no);
        demandMapper.updateById(d);
        return no;
    }

    private Object orDefault(Object v, Object fallback) {
        if (v == null || String.valueOf(v).isBlank()) return fallback;
        return v;
    }

    private String reqStr(Object v, String err) {
        if (v == null || String.valueOf(v).isBlank()) throw new BizException(err);
        return String.valueOf(v).trim();
    }

    private String optStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 给全体管理员发通知（站内铃铛 + 微信推送），失败不影响主流程 */
    private void notifyAdmins(String title, String content) {
        try {
            List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "admin"));
            for (User adm : admins) {
                Notification n = new Notification();
                n.setUserId(adm.getId());
                n.setType("system");
                n.setTitle(title);
                n.setContent(content);
                n.setIsRead(0);
                n.setCreateTime(LocalDateTime.now());
                notificationMapper.insert(n);
                wechatPusher.push(adm.getId(), "tutor_apply_result", title, content);
            }
        } catch (Exception e) {
            log.warn("[家长找家教] 管理端通知发送失败：{}", e.getMessage());
        }
    }

    private void audit(String action, String detail) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            l.setOperatorId(FeignAuthContext.userId());
            l.setOperatorName(FeignAuthContext.username());
            l.setAction(action);
            l.setDetail(detail);
            l.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(l);
        } catch (Exception e) {
            log.error("写审计日志失败: {}", e.getMessage());
        }
    }
}
