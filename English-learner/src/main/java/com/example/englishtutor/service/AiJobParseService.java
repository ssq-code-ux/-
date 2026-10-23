package com.example.englishtutor.service;

import java.util.List;
import java.util.Map;

/**
 * 家教信息批量解析 — 管理端把多条家教广告（微信群转发、截图 OCR 文本等）整段粘进来，
 * AI 负责“拆分 + 抽取字段”，返回可直接填入发布表单的草稿列表。
 * <p>
 * 解析结果一律先给管理端核对修改，确认无误才批量发布，AI 不直接落库。
 */
public interface AiJobParseService {

    /**
     * @param text    原始文本，可能包含多条家教信息
     * @param jobType 1家教单 2同行合作单（写进草稿，管理端可改）
     * @return 每条一个 Map，key 与发布接口字段一致：
     *         subject/grade/province/city/address/studentGender/expectGender/
     *         frequency/salary/situation/requirement/jobType/raw
     */
    List<Map<String, Object>> parseJobs(String text, Integer jobType);
}
