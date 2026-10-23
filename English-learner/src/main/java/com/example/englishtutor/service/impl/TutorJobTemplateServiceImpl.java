package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.TutorJob;
import com.example.englishtutor.entity.TutorJobTemplate;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.TutorJobTemplateMapper;
import com.example.englishtutor.service.AiJobParseService;
import com.example.englishtutor.service.TutorJobService;
import com.example.englishtutor.service.TutorJobTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科目模板 + AI 批量发布实现。
 * <p>
 * 单体版直接注入本地 {@link AiJobParseService}（复用 AIConfig 里的 deepseekRestClient），
 * 不像微服务版那样绕 Feign 远程调 ai-service；解析失败时统一抛 BizException
 * 给管理端一句能看懂的提示，不影响手工发布链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorJobTemplateServiceImpl implements TutorJobTemplateService {

    /** 单次批量发布上限：太多条会让管理端核对不过来，也容易被 AI 幻觉污染 */
    private static final int MAX_BATCH = 50;

    private final TutorJobTemplateMapper templateMapper;
    private final TutorJobService tutorJobService;
    private final AdminAuditLogMapper auditLogMapper;
    private final AiJobParseService aiJobParseService;

    // ==================== 需求 8：科目模板 ====================

    @Override
    public Map<String, Object> templates(Integer jobType) {
        assertAdmin();
        LambdaQueryWrapper<TutorJobTemplate> q = new LambdaQueryWrapper<>();
        if (jobType != null) {
            q.eq(TutorJobTemplate::getJobType, jobType);
        }
        q.orderByAsc(TutorJobTemplate::getSubject).orderByAsc(TutorJobTemplate::getId);
        List<TutorJobTemplate> all = templateMapper.selectList(q);

        List<Map<String, Object>> list = new ArrayList<>();
        List<String> subjects = new ArrayList<>();
        for (TutorJobTemplate t : all) {
            list.add(toMap(t));
            if (t.getSubject() != null && !subjects.contains(t.getSubject())) {
                subjects.add(t.getSubject());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        // 前端做“按科目选模板”的下拉时直接用，省一次遍历
        result.put("subjects", subjects);
        return result;
    }

    @Override
    public void saveTemplate(Map<String, Object> body) {
        assertAdmin();
        if (body == null) throw new BizException("请求体不能为空");
        String subject = str(body.get("subject"));
        if (subject == null) throw new BizException("科目不能为空");

        Long id = body.get("id") == null || String.valueOf(body.get("id")).isBlank()
                ? null : Long.parseLong(String.valueOf(body.get("id")));
        int jobType = body.get("jobType") == null
                ? TutorJob.TYPE_TUTOR : Integer.parseInt(String.valueOf(body.get("jobType")));

        TutorJobTemplate t = id == null ? null : templateMapper.selectById(id);
        boolean isNew = (t == null);
        if (isNew) {
            t = new TutorJobTemplate();
            t.setCreateTime(LocalDateTime.now());
        }
        // uk_tpl_subject_type 是硬约束：同科目同类型只能有一份，提前查出来说清楚，别等数据库报错
        Long dup = templateMapper.selectCount(new LambdaQueryWrapper<TutorJobTemplate>()
                .eq(TutorJobTemplate::getSubject, subject)
                .eq(TutorJobTemplate::getJobType, jobType)
                .ne(!isNew, TutorJobTemplate::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException("科目「" + subject + "」在该单类型下已有模板，请直接编辑那一份");
        }

        t.setSubject(subject);
        t.setJobType(jobType);
        String name = str(body.get("name"));
        t.setName(name == null ? subject + "·订单用例模板" : name);
        t.setGrade(str(body.get("grade")));
        t.setProvince(str(body.get("province")));
        t.setCity(str(body.get("city")));
        t.setAddress(str(body.get("address")));
        t.setStudentGender(str(body.get("studentGender")));
        t.setExpectGender(str(body.get("expectGender")));
        t.setFrequency(str(body.get("frequency")));
        t.setSalary(intOrNull(body.get("salary")));
        t.setSituation(str(body.get("situation")));
        t.setRequirement(str(body.get("requirement")));
        t.setPayToApply(body.get("payToApply") == null ? 1
                : (Integer.parseInt(String.valueOf(body.get("payToApply"))) == 1 ? 1 : 0));
        t.setEnabled(body.get("enabled") == null ? 1
                : (Integer.parseInt(String.valueOf(body.get("enabled"))) == 1 ? 1 : 0));
        t.setUpdateTime(LocalDateTime.now());

        if (isNew) {
            templateMapper.insert(t);
        } else {
            templateMapper.updateById(t);
        }
        audit(isNew ? "CREATE_JOB_TEMPLATE" : "UPDATE_JOB_TEMPLATE",
                (isNew ? "新增" : "编辑") + "科目模板「" + t.getName() + "」（" + t.getSubject() + "）");
    }

    @Override
    public void deleteTemplate(Long templateId) {
        assertAdmin();
        TutorJobTemplate t = templateMapper.selectById(templateId);
        if (t == null) throw new BizException("模板不存在");
        templateMapper.deleteById(templateId);
        audit("DELETE_JOB_TEMPLATE", "删除科目模板「" + t.getName() + "」（" + t.getSubject() + "）");
    }

    // ==================== 需求 7：AI 批量发布 ====================

    @Override
    public List<Map<String, Object>> parseJobs(String text, Integer jobType) {
        assertAdmin();
        if (text == null || text.isBlank()) {
            throw new BizException("请粘贴需要解析的家教信息");
        }
        List<Map<String, Object>> drafts;
        try {
            drafts = aiJobParseService.parseJobs(text, jobType == null ? TutorJob.TYPE_TUTOR : jobType);
        } catch (Exception e) {
            log.error("[批量发布] AI 解析调用失败", e);
            throw new BizException("AI 解析失败，请重试或手动填写家教信息：" + e.getMessage());
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new BizException("AI 没能从这段文本里识别出家教信息，请换一种粘贴格式或手动填写");
        }
        audit("PARSE_JOBS", "AI 批量解析家教信息，原文 " + text.length() + " 字 → " + drafts.size() + " 条草稿");
        return drafts;
    }

    @Override
    public Map<String, Object> batchPublish(List<Map<String, Object>> jobs) {
        assertAdmin();
        if (jobs == null || jobs.isEmpty()) {
            throw new BizException("没有需要发布的家教单");
        }
        if (jobs.size() > MAX_BATCH) {
            throw new BizException("单次最多批量发布 " + MAX_BATCH + " 条，请分批处理");
        }

        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            Map<String, Object> body = jobs.get(i);
            try {
                // 逐条复用单条发布逻辑：单号生成、默认缴费解锁、审计留痕都保持一致
                published.add(tutorJobService.publish(body));
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i);
                err.put("subject", body == null ? null : body.get("subject"));
                err.put("city", body == null ? null : body.get("city"));
                err.put("error", e.getMessage() == null ? "未知错误" : e.getMessage());
                errors.add(err);
                log.warn("[批量发布] 第 {} 条发布失败：{}", i + 1, e.getMessage());
            }
        }
        audit("BATCH_PUBLISH_JOB",
                "批量发布家教单：成功 " + published.size() + " 条，失败 " + errors.size() + " 条");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", published.size());
        result.put("fail", errors.size());
        result.put("jobs", published);
        result.put("errors", errors);
        return result;
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> toMap(TutorJobTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("subject", t.getSubject());
        m.put("name", t.getName());
        m.put("jobType", t.getJobType() == null ? TutorJob.TYPE_TUTOR : t.getJobType());
        m.put("grade", t.getGrade());
        m.put("province", t.getProvince());
        m.put("city", t.getCity());
        m.put("address", t.getAddress());
        m.put("studentGender", t.getStudentGender());
        m.put("expectGender", t.getExpectGender());
        m.put("frequency", t.getFrequency());
        m.put("salary", t.getSalary());
        m.put("situation", t.getSituation());
        m.put("requirement", t.getRequirement());
        m.put("payToApply", t.getPayToApply() == null ? 1 : t.getPayToApply());
        m.put("enabled", t.getEnabled() == null ? 1 : t.getEnabled());
        m.put("updateTime", t.getUpdateTime());
        return m;
    }

    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOrNull(Object v) {
        String s = str(v);
        if (s == null) return null;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            throw new BizException("时薪必须是数字（元/小时）");
        }
    }

    /** 敏感操作留痕（失败不阻断主流程） */
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
            log.error("[审计] 写入失败 action={}：{}", action, e.getMessage());
        }
    }
}
