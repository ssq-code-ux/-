package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.GoalDTO;
import com.example.englishtutor.entity.LearningGoal;
import com.example.englishtutor.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {
    private final GoalService service;

    @PostMapping
    public R<Void> add(@RequestBody GoalDTO d) {
        service.add(d);
        return R.ok(null);
    }

    @GetMapping
    public R<List<LearningGoal>> list(@RequestParam(required = false) Long studentId) {
        return R.ok(service.list(studentId));
    }

    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id, @RequestParam String status) {
        service.updateStatus(id, status);
        return R.ok(null);
    }
}
