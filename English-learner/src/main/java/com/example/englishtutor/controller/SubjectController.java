package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.Subject;
import com.example.englishtutor.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectService service;

    @GetMapping
    public R<List<Subject>> list() { return R.ok(service.list()); }

    @PostMapping
    public R<Void> add(@RequestParam("name") String name) { service.add(name); return R.ok(null); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { service.delete(id); return R.ok(null); }
}
