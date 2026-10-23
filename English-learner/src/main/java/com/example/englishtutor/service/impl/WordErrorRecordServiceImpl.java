package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.WordErrorRecord;
import com.example.englishtutor.mapper.WordErrorRecordMapper;
import com.example.englishtutor.service.WordErrorRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错词记录服务 — 单词「三本」状态机
 *
 * 状态流转：
 *   答错 / 标记不熟                → 错题本 (error)
 *   错题本点「已掌握」              → 复习本 (review)
 *   复习本再次掌握                  → 已掌握本 (mastered)
 *   已掌握本点「忘了」              → 错题本 (error)
 *
 * 幂等策略：数据库 (student_id, word_id) 唯一索引 + 捕获 DuplicateKeyException 降级为 UPDATE。
 * 一个用户一个单词只保留一条记录，重复出错只累加错误次数并回到错题本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordErrorRecordServiceImpl implements WordErrorRecordService {

    /**
     * 艾宾浩斯记忆曲线复习间隔（天）：兼容旧的「今日复习」接口，三本模式下前端已不再使用。
     */
    private static final long[] INTERVAL_DAYS = {1, 2, 4, 7, 15, 30};

    private final WordErrorRecordMapper mapper;

    @Override
    public void recordError(Long studentId, Long wordId, String word, String translation,
                            String partOfSpeech, String extensions,
                            String errorType, String wrongAnswer, String correctAnswer) {
        recordError(studentId, wordId, word, null, translation, partOfSpeech, extensions,
                errorType, wrongAnswer, correctAnswer, null);
    }

    @Override
    public void recordError(Long studentId, Long wordId, String word, String translation,
                            String partOfSpeech, String extensions,
                            String errorType, String wrongAnswer, String correctAnswer, String bank) {
        recordError(studentId, wordId, word, null, translation, partOfSpeech, extensions,
                errorType, wrongAnswer, correctAnswer, bank);
    }

    @Override
    public void recordError(Long studentId, Long wordId, String word, String phonetic, String translation,
                            String partOfSpeech, String extensions,
                            String errorType, String wrongAnswer, String correctAnswer, String bank) {
        // 一个用户 + 一个单词只保留一条记录
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getWordId, wordId);
        WordErrorRecord exist = mapper.selectOne(q);

        if (exist != null) {
            // 已存在 → 累加错误次数，更新为最新详细信息，并回到错题本
            exist.setErrorCount((exist.getErrorCount() == null ? 0 : exist.getErrorCount()) + 1);
            exist.setErrorType(errorType);
            exist.setWrongAnswer(wrongAnswer);
            exist.setCorrectAnswer(correctAnswer);
            exist.setPartOfSpeech(partOfSpeech);
            exist.setExtensions(extensions);
            if (phonetic != null) exist.setPhonetic(phonetic);
            if (bank != null) exist.setBank(bank);
            exist.setBook(BOOK_ERROR);
            exist.setReviewed(0);
            exist.setReviewStage(0);
            exist.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
            exist.setCreateTime(LocalDateTime.now());
            mapper.updateById(exist);
            return;
        }

        WordErrorRecord record = new WordErrorRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setWord(word);
        record.setPhonetic(phonetic);
        record.setTranslation(translation);
        record.setPartOfSpeech(partOfSpeech);
        record.setExtensions(extensions);
        record.setBank(bank);
        record.setErrorType(errorType);
        record.setWrongAnswer(wrongAnswer);
        record.setCorrectAnswer(correctAnswer);
        record.setBook(BOOK_ERROR);
        record.setReviewed(0);
        record.setErrorCount(1);
        record.setReviewStage(0);
        record.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
        record.setCreateTime(LocalDateTime.now());

        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发写入 / 重复提交 → 降级为 UPDATE
            log.warn("错题记录幂等命中: student={}, word={}", studentId, wordId);
            WordErrorRecord retryExist = mapper.selectOne(q);
            if (retryExist != null) {
                retryExist.setErrorCount((retryExist.getErrorCount() == null ? 0 : retryExist.getErrorCount()) + 1);
                retryExist.setErrorType(errorType);
                retryExist.setWrongAnswer(wrongAnswer);
                if (phonetic != null) retryExist.setPhonetic(phonetic);
                if (bank != null) retryExist.setBank(bank);
                retryExist.setBook(BOOK_ERROR);
                retryExist.setReviewed(0);
                retryExist.setReviewStage(0);
                retryExist.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
                retryExist.setCreateTime(LocalDateTime.now());
                mapper.updateById(retryExist);
            }
        }
    }

    @Override
    public List<WordErrorRecord> getErrors(Long studentId) {
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .orderByDesc(WordErrorRecord::getCreateTime);
        return mapper.selectList(q);
    }

    @Override
    public Page<WordErrorRecord> getErrorsPaged(Long studentId, int page, int size) {
        return getBookPaged(studentId, page, size, null, BOOK_ERROR);
    }

    @Override
    public Page<WordErrorRecord> getErrorsPaged(Long studentId, int page, int size, String bank) {
        return getBookPaged(studentId, page, size, bank, BOOK_ERROR);
    }

    @Override
    public Page<WordErrorRecord> getBookPaged(Long studentId, int page, int size, String bank, String book) {
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getBook, normalizeBook(book));
        if (bank != null && !bank.trim().isEmpty()) q.eq(WordErrorRecord::getBank, bank.trim());
        q.orderByDesc(WordErrorRecord::getCreateTime);
        return mapper.selectPage(new Page<>(page, size), q);
    }

    // ==================== 三本流转 ====================

    @Override
    public void markMastered(Long id, Long studentId) {
        WordErrorRecord record = requireOwned(id, studentId);
        // 错题本 → 复习本
        record.setBook(BOOK_REVIEW);
        record.setReviewed(0);
        mapper.updateById(record);
    }

    @Override
    public void reviewMastered(Long id, Long studentId) {
        WordErrorRecord record = requireOwned(id, studentId);
        // 复习本 → 已掌握本
        record.setBook(BOOK_MASTERED);
        record.setReviewed(1);
        record.setNextReviewTime(null);
        mapper.updateById(record);
    }

    @Override
    public void forget(Long id, Long studentId) {
        WordErrorRecord record = requireOwned(id, studentId);
        // 任意本 → 错题本
        record.setBook(BOOK_ERROR);
        record.setReviewed(0);
        record.setReviewStage(0);
        record.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
        mapper.updateById(record);
    }

    @Override
    public void markWordMastered(Long studentId, Long wordId, String word, String phonetic, String translation,
                                 String partOfSpeech, String extensions, String bank) {
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getWordId, wordId);
        WordErrorRecord exist = mapper.selectOne(q);
        if (exist != null) {
            exist.setBook(BOOK_MASTERED);
            exist.setReviewed(1);
            exist.setNextReviewTime(null);
            if (phonetic != null) exist.setPhonetic(phonetic);
            if (bank != null) exist.setBank(bank);
            mapper.updateById(exist);
            return;
        }
        WordErrorRecord record = new WordErrorRecord();
        record.setStudentId(studentId);
        record.setWordId(wordId);
        record.setWord(word);
        record.setPhonetic(phonetic);
        record.setTranslation(translation);
        record.setPartOfSpeech(partOfSpeech);
        record.setExtensions(extensions);
        record.setBank(bank);
        record.setErrorType("quiz");
        record.setWrongAnswer(null);
        record.setCorrectAnswer(translation);
        record.setBook(BOOK_MASTERED);
        record.setReviewed(1);
        record.setErrorCount(0);
        record.setReviewStage(0);
        record.setCreateTime(LocalDateTime.now());
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            WordErrorRecord retry = mapper.selectOne(q);
            if (retry != null) {
                retry.setBook(BOOK_MASTERED);
                retry.setReviewed(1);
                mapper.updateById(retry);
            }
        }
    }

    @Override
    public void deleteError(Long id, Long studentId) {
        requireOwned(id, studentId);
        mapper.deleteById(id);
    }

    private WordErrorRecord requireOwned(Long id, Long studentId) {
        WordErrorRecord record = mapper.selectById(id);
        if (record == null || !record.getStudentId().equals(studentId)) {
            throw new BizException("错词记录不存在");
        }
        return record;
    }

    @Override
    public int resetMastered(Long studentId, String bank) {
        LambdaUpdateWrapper<WordErrorRecord> u = new LambdaUpdateWrapper<>();
        u.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getBook, BOOK_MASTERED)
         .set(WordErrorRecord::getBook, BOOK_ERROR)
         .set(WordErrorRecord::getReviewed, 0)
         .set(WordErrorRecord::getReviewStage, 0)
         .set(WordErrorRecord::getNextReviewTime, LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
        if (bank != null && !bank.trim().isEmpty()) u.eq(WordErrorRecord::getBank, bank.trim());
        return mapper.update(null, u);
    }

    // ==================== 计数 ====================

    @Override
    public long countErrors(Long studentId) {
        return countBook(studentId, null, BOOK_ERROR);
    }

    @Override
    public long countErrors(Long studentId, String bank) {
        return countBook(studentId, bank, BOOK_ERROR);
    }

    @Override
    public long countBook(Long studentId, String bank, String book) {
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getBook, normalizeBook(book));
        if (bank != null && !bank.trim().isEmpty()) q.eq(WordErrorRecord::getBank, bank.trim());
        return mapper.selectCount(q);
    }

    // ==================== 错题复习（从错题本抽题） ====================

    @Override
    public List<WordErrorRecord> randomErrors(Long studentId, int count) {
        return randomErrors(studentId, count, null);
    }

    @Override
    public List<WordErrorRecord> randomErrors(Long studentId, int count, String bank) {
        boolean hasBank = bank != null && !bank.trim().isEmpty();
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getBook, BOOK_ERROR)
         .orderByDesc(WordErrorRecord::getErrorCount)
         .last("LIMIT " + count);
        if (hasBank) q.eq(WordErrorRecord::getBank, bank.trim());
        List<WordErrorRecord> records = mapper.selectList(q);
        if (records.size() < count) {
            LambdaQueryWrapper<WordErrorRecord> q2 = new LambdaQueryWrapper<>();
            q2.eq(WordErrorRecord::getStudentId, studentId)
              .eq(WordErrorRecord::getBook, BOOK_REVIEW);
            if (hasBank) q2.eq(WordErrorRecord::getBank, bank.trim());
            q2.last("LIMIT " + (count - records.size()));
            records.addAll(mapper.selectList(q2));
        }
        return records;
    }

    // ==================== 兼容旧的「今日复习」接口（前端已不再使用） ====================

    /** 到期条件：未掌握 + 下次复习时间已到（兼容存量数据：无计划的未掌握错题也视为到期） */
    private LambdaQueryWrapper<WordErrorRecord> dueWrapper(Long studentId, String bank) {
        LambdaQueryWrapper<WordErrorRecord> q = new LambdaQueryWrapper<>();
        q.eq(WordErrorRecord::getStudentId, studentId)
         .eq(WordErrorRecord::getReviewed, 0)
         .and(w -> w.isNull(WordErrorRecord::getNextReviewTime)
                   .or().le(WordErrorRecord::getNextReviewTime, LocalDateTime.now()));
        if (bank != null && !bank.trim().isEmpty()) q.eq(WordErrorRecord::getBank, bank.trim());
        return q;
    }

    @Override
    public long countDueReviews(Long studentId) {
        return countDueReviews(studentId, null);
    }

    @Override
    public long countDueReviews(Long studentId, String bank) {
        return mapper.selectCount(dueWrapper(studentId, bank));
    }

    @Override
    public List<WordErrorRecord> getDueReviews(Long studentId, int limit) {
        return getDueReviews(studentId, limit, null);
    }

    @Override
    public List<WordErrorRecord> getDueReviews(Long studentId, int limit, String bank) {
        LambdaQueryWrapper<WordErrorRecord> q = dueWrapper(studentId, bank);
        q.orderByAsc(WordErrorRecord::getNextReviewTime)
         .orderByDesc(WordErrorRecord::getErrorCount)
         .last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        return mapper.selectList(q);
    }

    @Override
    public void submitReviewResult(Long id, Long studentId, boolean correct) {
        WordErrorRecord record = requireOwned(id, studentId);
        int stage = record.getReviewStage() != null ? record.getReviewStage() : 0;
        record.setLastReviewTime(LocalDateTime.now());
        if (correct) {
            stage++;
            if (stage >= INTERVAL_DAYS.length) {
                record.setReviewStage(stage);
                record.setReviewed(1);
                record.setNextReviewTime(null);
            } else {
                record.setReviewStage(stage);
                record.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[stage]));
            }
        } else {
            record.setReviewStage(0);
            record.setReviewed(0);
            record.setNextReviewTime(LocalDateTime.now().plusDays(INTERVAL_DAYS[0]));
        }
        mapper.updateById(record);
    }

    private String normalizeBook(String book) {
        if (book == null || book.trim().isEmpty()) return BOOK_ERROR;
        String b = book.trim();
        if (BOOK_REVIEW.equals(b) || BOOK_MASTERED.equals(b)) return b;
        return BOOK_ERROR;
    }
}