package com.example.englishtutor.service;

import java.util.List;
import java.util.Map;

/**
 * 科目订单模板 + AI 批量发布（需求 7、需求 8）。
 * <p>
 * 模板：每个科目一份用例，管理端发布时可选用模板预填再改；
 * 批量：管理端粘贴一大段多条家教信息 → AI 拆分成草稿 → 管理端逐条核对/修改 → 一次性批量发布。
 * AI 只负责“拆分填入”，落库前必须经过管理端确认，绝不自动发布。
 */
public interface TutorJobTemplateService {

    // ==================== 需求 8：科目模板 ====================

    /** 模板列表（jobType 可选筛选；含停用与否由 enabled 字段体现） */
    Map<String, Object> templates(Integer jobType);

    /** 新增/编辑模板（body 带 id 则更新；同一科目同一单类型只允许一份） */
    void saveTemplate(Map<String, Object> body);

    /** 删除模板 */
    void deleteTemplate(Long templateId);

    // ==================== 需求 7：AI 批量发布 ====================

    /**
     * 调用 AI 把一大段多条家教信息拆分成语义字段草稿（不落库）。
     *
     * @param text    原始文本
     * @param jobType 1家教单 2同行合作单
     */
    List<Map<String, Object>> parseJobs(String text, Integer jobType);

    /**
     * 批量发布（管理端核对修改后提交）。
     * 逐条独立成败：某一条科目为空不会影响其他条，失败原因原样返回给管理端继续修改。
     *
     * @return { success: n, fail: m, jobs: [已发布单], errors: [{index, subject, error}] }
     */
    Map<String, Object> batchPublish(List<Map<String, Object>> jobs);
}
