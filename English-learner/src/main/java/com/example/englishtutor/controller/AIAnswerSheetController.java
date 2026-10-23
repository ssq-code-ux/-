package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.AiGradeDTO;
import com.example.englishtutor.dto.AiGradeResultDTO;
import com.example.englishtutor.dto.AiParseDTO;
import com.example.englishtutor.dto.SectionDTO;
import com.example.englishtutor.service.AIAnswerSheetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 答题卡 AI 接口
 * <p>
 * /parse 由教师端前端直接调用（出卷向导第一步）<br>
 * /grade 由答题卡服务在学生提交后调用，只处理主观题
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/answer-sheet")
@RequiredArgsConstructor
public class AIAnswerSheetController {

    private final AIAnswerSheetService service;

    /**
     * 🤖 解析题目文本，生成「大题 + 小题」结构（含标准答案）
     * <p>
     * POST /api/ai/answer-sheet/parse
     * Body: { "rawText": "...完形填空原文...", "defaultScore": 1, "sectionHint": "cloze", "subject": "数学" }
     * <p>
     * sectionHint 可不传（或传 auto），交由 AI 自动识别题型；subject 不传按英语处理。
     */
    @PostMapping("/parse")
    public R<List<SectionDTO>> parse(@RequestBody AiParseDTO dto) {
        if (dto.getRawText() == null || dto.getRawText().trim().isEmpty()) {
            return R.fail("请先粘贴题目内容");
        }
        List<SectionDTO> list = service.parse(dto.getRawText().trim(), dto.getDefaultScore(),
                dto.getSectionHint(), dto.getSubject());
        int questionCount = list.stream()
                .mapToInt(s -> s.getQuestions() == null ? 0 : s.getQuestions().size())
                .sum();
        log.info("AI 出卷完成 — 共 {} 个大题、{} 道小题", list.size(), questionCount);
        return R.ok(list);
    }

    /**
     * 🤖 识别题目图片出卷：图片 → 多模态模型转录成文字 → 走与 /parse 相同的解析流程，
     * 后续教师核验、发布环节不变。
     * <p>
     * POST /api/ai/answer-sheet/parse-image（multipart/form-data）<br>
     * 参数：file=题目图片；defaultScore / sectionHint / subject 同 /parse，均可选。
     */
    @PostMapping("/parse-image")
    public R<List<SectionDTO>> parseImage(@RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) Integer defaultScore,
                                          @RequestParam(required = false, defaultValue = "auto") String sectionHint,
                                          @RequestParam(required = false) String subject) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.fail("请先选择题目图片");
        }
        // 移动端 uni.uploadFile 常把图片的 content-type 发成 application/octet-stream（web 端 el-upload 才是 image/jpeg），
        // 若在这里硬拒绝就会导致「App 图片识别出卷」永远失败。改为：content-type 不是 image/* 时按文件名后缀推断，
        // 仍判断不出就默认按 jpeg 交给多模态模型（data URL 前缀只是声明，模型按实际字节识别）。
        String mimeType = resolveImageMime(file.getContentType(), file.getOriginalFilename());
        List<SectionDTO> list = service.parseImage(file.getBytes(), mimeType, defaultScore, sectionHint, subject);

        int questionCount = list.stream()
                .mapToInt(s -> s.getQuestions() == null ? 0 : s.getQuestions().size())
                .sum();
        log.info("AI 图片识别出卷完成 — 共 {} 个大题、{} 道小题", list.size(), questionCount);
        return R.ok(list);
    }

    /**
     * 🤖 主观题批量评分
     * <p>
     * POST /api/ai/answer-sheet/grade
     */
    @PostMapping("/grade")
    public R<List<AiGradeResultDTO>> grade(@RequestBody AiGradeDTO dto) {
        List<AiGradeResultDTO> list = service.grade(dto);
        log.info("AI 评分完成 — 共 {} 道主观题", list.size());
        return R.ok(list);
    }

    /**
     * 归一化图片 MIME：优先用请求带的 content-type；移动端发 octet-stream 或缺失时按文件名后缀推断；
     * 都判断不出则默认 image/jpeg，避免因客户端 content-type 不规范而误杀正常的图片识别请求。
     */
    private String resolveImageMime(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return contentType;
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
