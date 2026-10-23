package com.example.englishtutor.service;

import com.example.englishtutor.dto.AiGradeDTO;
import com.example.englishtutor.dto.AiGradeResultDTO;
import com.example.englishtutor.dto.SectionDTO;

import java.util.List;

/** 答题卡 AI 服务：解析题目出卷 + 主观题评分 */
public interface AIAnswerSheetService {

    /**
     * 把教师粘贴的题目文本解析成「大题 + 小题」结构（含标准答案与解析），供教师核验
     *
     * @param sectionHint 教师指定的大题类型（cloze/reading/...），为空则由 AI 自动识别
     * @param subject     学科名称（英语/数学/语文…），为空按英语处理，决定出卷 prompt 的题型集与口径
     */
    List<SectionDTO> parse(String rawText, Integer defaultScore, String sectionHint, String subject);

    /**
     * 图片识别出卷：多模态模型先把题目图片转成文字，再走与 {@link #parse} 完全相同的解析流程，
     * 后续教师核验、发布等环节不变。
     *
     * @param image    题目图片字节（前端已压缩）
     * @param mimeType 图片 MIME 类型，如 image/jpeg
     * @param sectionHint 同 {@link #parse}
     * @param subject  同 {@link #parse}
     */
    List<SectionDTO> parseImage(byte[] image, String mimeType, Integer defaultScore, String sectionHint, String subject);

    /** 批量给主观题打分，一次请求处理全部题目 */
    List<AiGradeResultDTO> grade(AiGradeDTO dto);
}
