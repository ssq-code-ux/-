package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 答题卡大题（题组）
 * <p>
 * 英语卷子里完形填空、阅读理解、选词填空、补全对话都是「多个小题共享一段材料」，
 * 材料必须挂在大题上而不是复制到每个小题里，否则学生端看不到上下文。
 * <p>
 * sectionType 决定<b>怎么展示</b>（文章要不要折叠、要不要显示词库/选项框），
 * 小题的 questionType 决定<b>怎么判分</b>，两者互不干扰。
 */
@Data
@TableName("homework_section")
public class HomeworkSection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    /** 大题序号，从 1 开始 */
    private Integer seq;
    /**
     * cloze=完形填空 reading=阅读理解 word_bank=选词填空
     * blank_fill=盲填/首字母填空 dialogue=补全对话 writing=书面表达
     * standalone=单句题（无材料）
     */
    private String sectionType;
    private String title;
    /** 答题要求，如「从每题所给的四个选项中选出最佳选项」 */
    private String instruction;
    /** 材料原文：完形/阅读的文章、对话原文、写作要求；单句题为 null */
    private String passage;
    /** 选词填空的备选词库，JSON 数组：["make","happy"] */
    private String wordBank;
    /** 整个大题共用的选项（补全对话 7选5），JSON 数组：[{"key":"A","text":"..."}] */
    private String sharedOptions;
    private LocalDateTime createTime;
}
