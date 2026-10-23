package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("error_record") public class ErrorRecord{ @TableId(type=IdType.AUTO) private Long id; private Long studentId; private Long homeworkId; private String errorContent; private String correctAnswer; private String errorType; private String analysis; private LocalDateTime createTime; }
