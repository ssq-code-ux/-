package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 答题卡题目
 * <p>
 * AI 解析教师给的题目文本后生成（aiGenerated=1），教师核验确认（confirmed=1）后学生才能看到。
 * 注意：standardAnswer / analysis 属于答案信息，返回给学生端前必须剥离。
 */
@Data
@TableName("homework_question")
public class HomeworkQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    /** 所属大题ID（homework_section.id）；null=单句题，没有共享材料 */
    private Long sectionId;
    /** 题号，从 1 开始 */
    private Integer seq;
    /** single=单选 multi=多选 judge=判断 blank=填空 short=简答/写作 */
    private String questionType;
    private String content;
    /** 选项 JSON 数组：[{"key":"A","text":"..."}]，非选择题为 null */
    private String options;
    /** 标准答案：选择题存选项key（多选如 "AC"）；填空多解用竖线分隔 */
    private String standardAnswer;
    private String analysis;
    /** 该题分值 */
    private Integer score;
    private String knowledgePoint;
    private Integer aiGenerated;
    /** 教师是否已核验：0=待核验（学生看不到）1=已确认 */
    private Integer confirmed;
    private LocalDateTime createTime;
}
