package com.example.englishtutor.service;

import com.example.englishtutor.dto.GoalDTO;
import com.example.englishtutor.entity.LearningGoal;

import java.util.List;

public interface GoalService {
    void add(GoalDTO dto);

    List<LearningGoal> list(Long studentId);

    void updateStatus(Long id, String status);
}
