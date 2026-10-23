package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 答题卡学生作答与判分结果（一行一题）
 * <p>
 * 客观题由后端字符串比对判定，主观题由 AI 打分并给出 aiComment，教师可复核修改（teacherFixed=1）。
 */
@Data
@TableName("homework_answer")
public class HomeworkAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submitId;
    private Long questionId;
    private String studentAnswer;
    /** 1=对 0=错 null=待判 */
    private Integer correct;
    private Integer gotScore;
    /** AI 点评/扣分理由，客观题为空 */
    private String aiComment;
    private Integer teacherFixed;
    private LocalDateTime createTime;
}
