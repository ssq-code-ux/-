package com.example.englishtutor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.LearningResource;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.service.ResourceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService service;

    @PostMapping
    public R<Void> add(@RequestParam("title") String title,
                    @RequestParam(value = "type", defaultValue = "grammar") String type,
                    @RequestParam(value = "content", required = false) String content,
                    @RequestParam(value = "file", required = false) MultipartFile file,
                    @RequestParam(value = "fileUrl", required = false) String fileUrl,
                    @RequestParam(value = "subjectId", required = false) Long subjectId) {
        LearningResource r = new LearningResource();
        r.setTitle(title);
        r.setType(type);
        r.setContent(content);
        if (fileUrl != null && !fileUrl.isEmpty()) r.setFileUrl(fileUrl);
        r.setSubjectId(subjectId);
        service.add(r, file);
        return R.ok(null);
    }

    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(required = false) String type,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long subjectId,
                     @RequestParam(required = false) Long teacherId,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "6") int size) {
        Page<LearningResource> p = service.list(type, keyword, subjectId, teacherId, page, size);
        return R.ok(Map.of("records", p.getRecords(), "total", p.getTotal()));
    }

    /** 已存在的资料类型（去重），供新增资料时下拉建议；subjectId 非空则只看该学科 */
    @GetMapping("/types")
    public R<List<String>> types(@RequestParam(required = false) Long subjectId) {
        return R.ok(service.distinctTypes(subjectId));
    }

    /** 搜索教师账号（教师间共享资料用） */
    @GetMapping("/teachers/search")
    public R<List<User>> searchTeachers(@RequestParam(required = false) String keyword) {
        return R.ok(service.searchTeachers(keyword));
    }

    @GetMapping("/{id}")
    public R<LearningResource> detail(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                       @RequestParam("title") String title,
                       @RequestParam(value = "type", defaultValue = "grammar") String type,
                       @RequestParam(value = "content", required = false) String content,
                       @RequestParam(value = "file", required = false) MultipartFile file,
                       @RequestParam(value = "fileUrl", required = false) String fileUrl,
                       @RequestParam(value = "subjectId", required = false) Long subjectId) {
        service.update(id, title, type, content, file, fileUrl, subjectId);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { service.delete(id); return R.ok(null); }

    /** 复制其他教师的资料到自己的资料库（教师间共享） */
    @PostMapping("/{id}/copy")
    public R<LearningResource> copy(@PathVariable Long id) { return R.ok(service.copy(id)); }

    /**
     * 文件代理：后端从 OSS 拉取文件再转发给浏览器，
     * 规避浏览器直连 OSS 的跨域（CORS）限制，支持在线预览与下载。
     * @param index 文件在 fileUrl（逗号分隔）中的下标
     * @param mode  download=触发浏览器下载；其他=内联展示（预览）
     */
    @GetMapping("/{id}/file")
    public void proxyFile(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int index,
                          @RequestParam(defaultValue = "preview") String mode,
                          HttpServletResponse response) {
        service.streamFile(id, index, "download".equals(mode), response);
    }

    /** 一键复制指定教师的全部资料到自己的资料库（教师间共享） */
    @PostMapping("/copy-all")
    public R<Integer> copyAll(@RequestParam Long teacherId) { return R.ok(service.copyAllFromTeacher(teacherId)); }
}
