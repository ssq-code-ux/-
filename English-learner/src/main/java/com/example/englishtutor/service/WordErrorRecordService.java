package com.example.englishtutor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.entity.WordErrorRecord;

import java.util.List;

public interface WordErrorRecordService {

    /** 本子类型：错题本 */
    String BOOK_ERROR = "error";
    /** 本子类型：复习本 */
    String BOOK_REVIEW = "review";
    /** 本子类型：已掌握本 */
    String BOOK_MASTERED = "mastered";

    /** 记录一条错误 */
    void recordError(Long studentId, Long wordId, String word, String translation,
                     String partOfSpeech, String extensions,
                     String errorType, String wrongAnswer, String correctAnswer);

    /** 记录一条错误（含所属词库：教师错题本按词库复习用） */
    void recordError(Long studentId, Long wordId, String word, String translation,
                     String partOfSpeech, String extensions,
                     String errorType, String wrongAnswer, String correctAnswer, String bank);

    /** 记录一条错误（完整详细信息：单词 + 读音 + 词性 + 释义 + 延伸词），错题本/复习本详情展示用 */
    void recordError(Long studentId, Long wordId, String word, String phonetic, String translation,
                     String partOfSpeech, String extensions,
                     String errorType, String wrongAnswer, String correctAnswer, String bank);

    /** 获取学生的所有错词（未掌握的在前） */
    List<WordErrorRecord> getErrors(Long studentId);

    /** 分页获取错词 */
    Page<WordErrorRecord> getErrorsPaged(Long studentId, int page, int size);

    /** 分页获取错词；bank 非空时只返回该词库的错词 */
    Page<WordErrorRecord> getErrorsPaged(Long studentId, int page, int size, String bank);

    /** 将错词标记为已掌握：错题本 → 复习本 */
    void markMastered(Long id, Long studentId);

    /** 复习本再次掌握：复习本 → 已掌握本 */
    void reviewMastered(Long id, Long studentId);

    /** 按单词直接标记为已掌握（测验两阶段都通过时调用）：不存在则新建，存在则移入已掌握本 */
    void markWordMastered(Long studentId, Long wordId, String word, String phonetic, String translation,
                          String partOfSpeech, String extensions, String bank);

    /** 清零某词库的「已掌握」进度：该词库所有已掌握单词退回错题本（状态=不熟），返回影响行数 */
    int resetMastered(Long studentId, String bank);

    /** 忘了：从任意本退回错题本 */
    void forget(Long id, Long studentId);

    /** 删除一条错题记录 */
    void deleteError(Long id, Long studentId);

    /** 按本子分页查询（book：error/review/mastered，为空默认 error） */
    Page<WordErrorRecord> getBookPaged(Long studentId, int page, int size, String bank, String book);

    /** 统计某本子的单词数 */
    long countBook(Long studentId, String bank, String book);

    /** 获取学生错词总数 */
    long countErrors(Long studentId);

    /** 获取学生某词库错词总数（bank 为空则统计全部） */
    long countErrors(Long studentId, String bank);

    /** 随机获取 N 个未掌握的错词用于复习 */
    List<WordErrorRecord> randomErrors(Long studentId, int count);

    /** 随机获取 N 个未掌握的错词用于复习（bank 非空时限定词库） */
    List<WordErrorRecord> randomErrors(Long studentId, int count, String bank);

    /** 统计今日到期需复习的错词数（艾宾浩斯调度） */
    long countDueReviews(Long studentId);

    /** 统计今日到期需复习的错词数（bank 非空时限定词库） */
    long countDueReviews(Long studentId, String bank);

    /** 获取今日到期需复习的错词列表 */
    List<WordErrorRecord> getDueReviews(Long studentId, int limit);

    /** 获取今日到期需复习的错词列表（bank 非空时限定词库） */
    List<WordErrorRecord> getDueReviews(Long studentId, int limit, String bank);

    /** 提交单条复习结果：记住 → 推进下一阶段；没记住 → 重置回第一阶段 */
    void submitReviewResult(Long id, Long studentId, boolean correct);
}
