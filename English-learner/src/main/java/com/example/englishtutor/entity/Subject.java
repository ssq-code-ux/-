package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("subject")
public class Subject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 创建学科老师ID（仅追溯，学科全局共享） */
    private Long teacherId;
    private LocalDateTime createTime;
}
