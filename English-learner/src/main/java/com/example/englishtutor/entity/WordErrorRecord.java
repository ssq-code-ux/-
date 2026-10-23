package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 单词错误记录 — 词汇测验专用错题库
 * 与 homework 的 ErrorRecord 分离，独立管理词汇学习中的错误
 */
@Data
@TableName("word_error_record")
public class WordErrorRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 词汇ID（关联 vocabulary 表） */
    private Long wordId;

    /** 单词（冗余存储，方便查询展示） */
    private String word;

    /** 读音（音标，冗余存储） */
    private String phonetic;

    /** 释义（冗余存储） */
    private String translation;

    /** 所属词库（冗余存储）：教师错题本按词库复习用 */
    private String bank;

    /**
     * 错误类型：
     *   meaning  — 阶段一：看单词选意思，选错了
     *   spelling — 阶段二：看意思写单词，拼错了
     */
    private String errorType;

    /** 学生提交的错误答案 */
    private String wrongAnswer;

    /** 正确答案 */
    private String correctAnswer;

    /** 词性（冗余存储，方便错题库展示完整单词信息） */
    private String partOfSpeech;

    /** 延伸词（冗余存储） */
    private String extensions;

    /** 是否已在复习中掌握（0-未掌握 1-已掌握；兼容旧数据，新逻辑以 book 为准） */
    private Integer reviewed;

    /**
     * 所属本子：
     *   error    — 错题本（答错 / 标记不熟）
     *   review   — 复习本（错题本点「已掌握」后进入）
     *   mastered — 已掌握本（复习本再次掌握后进入）
     */
    private String book;

    /** 累计错误次数（同一单词可能多次错） */
    private Integer errorCount;

    /** 艾宾浩斯复习阶段（0 起步，每答对一次 +1，走完全部阶段后掌握） */
    private Integer reviewStage;

    /** 下次计划复习时间（艾宾浩斯调度计算；null = 已掌握/无计划） */
    private LocalDateTime nextReviewTime;

    /** 最近一次复习时间 */
    private LocalDateTime lastReviewTime;

    private LocalDateTime createTime;
}
