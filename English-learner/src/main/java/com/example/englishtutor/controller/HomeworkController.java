package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.entity.Homework;
import com.example.englishtutor.entity.HomeworkReview;
import com.example.englishtutor.entity.HomeworkSubmit;
import com.example.englishtutor.service.HomeworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkService service;

    @PostMapping
    public R<Void> assign(@RequestBody HomeworkDTO d) {
        service.assign(d);
        return R.ok(null);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody HomeworkDTO d) {
        service.update(id, d);
        return R.ok(null);
    }

    @GetMapping
    public R<List<Homework>> list(@RequestParam(required = false) Long studentId) {
        return R.ok(service.list(studentId));
    }

    @GetMapping("/grouped")
    public R<Map<String, Object>> grouped() {
        return R.ok(service.listGrouped());
    }

    @GetMapping("/{id}")
    public R<Homework> detail(@PathVariable("id") Long id) {
        return R.ok(service.getById(id));
    }

    @PostMapping("/submit")
    public R<Void> submit(@RequestBody SubmitDTO d) {
        service.submit(d);
        return R.ok(null);
    }

    @GetMapping("/{id}/submits")
    public R<List<HomeworkSubmit>> submits(@PathVariable("id") Long id) {
        return R.ok(service.submits(id));
    }

    @PostMapping("/review")
    public R<Void> review(@RequestBody ReviewDTO d) {
        service.review(d);
        return R.ok(null);
    }

    @GetMapping("/{id}/review")
    public R<Map<String, Object>> reviewResult(@PathVariable("id") Long id) {
        return R.ok(service.reviewResult(id));
    }

    @GetMapping("/submits/{submitId}/review")
    public R<HomeworkReview> submitReview(@PathVariable("submitId") Long submitId) {
        return R.ok(service.getReviewBySubmitId(submitId));
    }

    @GetMapping("/overdue")
    public R<List<Homework>> overdue() {
        return R.ok(service.overdue());
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return R.ok(null);
    }
}
