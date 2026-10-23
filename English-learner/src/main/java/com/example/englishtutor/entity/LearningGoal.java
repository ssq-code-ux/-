package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("learning_goal") public class LearningGoal{ @TableId(type=IdType.AUTO) private Long id; private Long studentId; private Long teacherId; private String title; private String description; private LocalDate deadline; private String status; private LocalDateTime createTime; }
