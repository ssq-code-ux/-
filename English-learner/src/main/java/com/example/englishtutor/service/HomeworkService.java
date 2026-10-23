package com.example.englishtutor.service;

import com.example.englishtutor.dto.*;
import com.example.englishtutor.entity.*;

import java.util.List;
import java.util.Map;

public interface HomeworkService {
    void assign(HomeworkDTO dto);

    void update(Long id, HomeworkDTO dto);

    List<Homework> list(Long studentId);

    Homework getById(Long id);

    Map<String, Object> listGrouped();

    void submit(SubmitDTO dto);

    List<HomeworkSubmit> submits(Long homeworkId);

    void review(ReviewDTO dto);

    Map<String, Object> reviewResult(Long homeworkId);

    HomeworkReview getReviewBySubmitId(Long submitId);

    List<Homework> overdue();

    void delete(Long id);
}
