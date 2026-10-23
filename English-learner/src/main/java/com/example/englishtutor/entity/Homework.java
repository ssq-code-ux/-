package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName("homework") public class Homework{ @TableId(type=IdType.AUTO) private Long id; private Long teacherId; private Long studentId; private String title; private String content; private String type; private String subject; private String attachmentUrl; private LocalDateTime deadline; private String status; private LocalDateTime createTime; }
