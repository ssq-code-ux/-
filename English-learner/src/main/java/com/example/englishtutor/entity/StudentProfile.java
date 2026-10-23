package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("student_profile") public class StudentProfile{ @TableId(type=IdType.AUTO) private Long id; private Long studentId; private Long teacherId; private String grade; private String subject; private String textbook; private String level; private String goals; private String weakPoints; private LocalDateTime createTime; }
