package com.example.englishtutor.vo;

import lombok.Data;
import java.util.*;

@Data
public class StudentAnalysisVO {
    private Long studentId;
    private Long totalHomework;
    private Long submittedCount;
    private Double submitRate;
    private Double averageScore;
    private Map<String, Long> errorTypeCount;
    private List<String> suggestions;
    // 新增
    private Long vocabCount;
    private List<Map<String, Object>> scoreTrend;
    private List<Map<String, Object>> submitTrend;
}
