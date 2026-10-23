package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("homework_submit") public class HomeworkSubmit{ @TableId(type=IdType.AUTO) private Long id; private Long homeworkId; private Long studentId; private String textAnswer; private String fileUrl; private String status; private LocalDateTime submitTime; }
