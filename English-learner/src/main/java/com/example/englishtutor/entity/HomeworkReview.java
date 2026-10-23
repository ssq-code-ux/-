package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("homework_review")
public class HomeworkReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    private Long submitId;
    private Long teacherId;
    private Integer score;
    private String comment;
    /** 语音评语文件地址（OSS URL），纯文字批改时为空 */
    private String voiceUrl;
    private String needRedo;
    private LocalDateTime reviewTime;
}
