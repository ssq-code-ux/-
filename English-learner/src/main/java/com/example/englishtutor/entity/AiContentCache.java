package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长端 AI 内容缓存
 * <p>
 * 「学习建议」每天生成一次、「周报总结」每周生成一次，由定时任务在家长登录前预生成；
 * 家长端接口只读缓存，不现场等 AI。同一 (student, type, period) 唯一，手动刷新覆盖旧内容。
 */
@Data
@TableName("ai_content_cache")
public class AiContentCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    /** daily_advice=每日学习建议 weekly_summary=周报总结 */
    private String contentType;
    /** 周期键：每日=2026-08-28；每周=2026-W35（ISO 周） */
    private String periodKey;
    private String content;
    private LocalDateTime createTime;
}
