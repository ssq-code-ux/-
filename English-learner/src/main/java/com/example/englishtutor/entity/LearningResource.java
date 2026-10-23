package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("learning_resource")
public class LearningResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    /** 学科ID（默认英语学科） */
    private Long subjectId;
    private String level;
    private String content;
    private String tags;
    private String fileUrl;
    private Long teacherId;
    private LocalDateTime createTime;

    /** 学科名称（非表字段，查询时回填用于展示） */
    @TableField(exist = false)
    private String subjectName;

    /** 发布教师姓名（非表字段，查询时回填用于展示，共享场景区分资料来源） */
    @TableField(exist = false)
    private String teacherName;
}
