package com.example.englishtutor.controller;
import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.FileResource;
import com.example.englishtutor.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService service;

    @PostMapping("/upload")
    public R<FileResource> upload(@RequestParam MultipartFile file) {
        // 诊断日志：确认请求到达、文件大小、类型
        log.info("📤 收到上传请求 — 文件名: {}, 大小: {}KB, 类型: {}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() / 1024 : 0,
                file != null ? file.getContentType() : "null");
        if (file == null || file.isEmpty()) {
            log.warn("上传文件为空");
            return R.fail("文件不能为空");
        }
        return R.ok(service.upload(file));
    }

    /**
     * 生成浏览器直传 OSS 的预签名 PUT URL（大文件如 APK 用，避免服务端中转慢/超时）。
     * 前端拿到 uploadUrl 后直接 PUT 文件，再用返回的 fileUrl 作为最终地址。
     */
    @PostMapping("/presign")
    public R<Map<String, String>> presign(@RequestBody Map<String, String> body) {
        return R.ok(service.presign(body.get("filename"), body.get("contentType")));
    }
}
