package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.Vocabulary;
import com.example.englishtutor.mapper.VocabularyMapper;
import com.example.englishtutor.service.VocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VocabularyServiceImpl implements VocabularyService {
    private final VocabularyMapper mapper;

    /** 标准词库顺序：学生词库永远排最前 */
    private static final List<String> STANDARD_BANKS =
            List.of(STUDENT_BANK, "四级词库", "六级词库");

    public void add(Vocabulary v) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可管理词汇");
        // 词汇库全局公用：仍记录创建老师，仅用于追溯，不再参与数据隔离
        v.setTeacherId(AuthContext.userId());
        if (v.getBank() == null || v.getBank().trim().isEmpty()) {
            v.setBank(STUDENT_BANK);
        } else {
            v.setBank(v.getBank().trim());
        }
        v.setCreateTime(LocalDateTime.now());
        mapper.insert(v);
    }

    public void update(Long id, Vocabulary v) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可管理词汇");
        Vocabulary exist = mapper.selectById(id);
        if (exist == null) throw new BizException("词汇不存在");
        exist.setWord(v.getWord());
        exist.setPhonetic(v.getPhonetic());
        exist.setTranslation(v.getTranslation());
        exist.setPartOfSpeech(v.getPartOfSpeech());
        exist.setExtensions(v.getExtensions());
        if (v.getBank() != null && !v.getBank().trim().isEmpty()) {
            exist.setBank(v.getBank().trim());
        }
        mapper.updateById(exist);
    }

    public Page<Vocabulary> list(int page, int size, String keyword) {
        return list(page, size, keyword, null);
    }

    public Page<Vocabulary> list(int page, int size, String keyword, String bank) {
        // 词汇库全局公用：所有老师和学生看到同一份词汇，不再按 teacher_id 过滤
        LambdaQueryWrapper<Vocabulary> q = new LambdaQueryWrapper<>();
        // 词库过滤：教师按所选词库；学生/家长端固定只看「学生词库」，保持原有行为
        String effectiveBank = resolveListBank(bank);
        if (effectiveBank != null) {
            q.eq(Vocabulary::getBank, effectiveBank);
        }
        // 模糊查询：同时搜索单词和释义，任一匹配即返回
        if (keyword != null && !keyword.trim().isEmpty()) {
            q.and(w -> w.like(Vocabulary::getWord, keyword)
                        .or()
                        .like(Vocabulary::getTranslation, keyword));
        }
        // 按主键倒序：id 是自增主键，天然有序，可走索引避免 create_time 无索引带来的 filesort
        q.orderByDesc(Vocabulary::getId);
        return mapper.selectPage(new Page<>(page, size), q);
    }

    public void delete(Long id) {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅老师可删除词汇");
        Vocabulary exist = mapper.selectById(id);
        if (exist == null) throw new BizException("词汇不存在");
        mapper.deleteById(id);
    }

    public List<Vocabulary> random(Long count) {
        return random(count, null);
    }

    public List<Vocabulary> random(Long count, String bank) {
        // 随机抽词限定在同一词库内，保证干扰项与考题来源一致
        LambdaQueryWrapper<Vocabulary> q = new LambdaQueryWrapper<>();
        q.eq(Vocabulary::getBank, resolveRandomBank(bank));
        long total = mapper.selectCount(q);
        if (total == 0) return Collections.emptyList();
        long offset = (long) (Math.random() * Math.max(0, total - count));
        return mapper.selectList(q.last("LIMIT " + offset + "," + count));
    }

    public Vocabulary getById(Long id) {
        return mapper.selectById(id);
    }

    public List<Vocabulary> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return mapper.selectBatchIds(ids);
    }

    @Override
    public List<String> banks() {
        // 标准词库始终返回，另合并数据库中出现过的自定义词库
        LinkedHashSet<String> set = new LinkedHashSet<>(STANDARD_BANKS);
        List<Vocabulary> rows = mapper.selectList(new LambdaQueryWrapper<Vocabulary>()
                .select(Vocabulary::getBank)
                .groupBy(Vocabulary::getBank));
        for (Vocabulary r : rows) {
            if (r.getBank() != null && !r.getBank().trim().isEmpty()) {
                set.add(r.getBank().trim());
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 列表/搜索的词库解析：
     *  - 非教师（学生/家长等）：固定「学生词库」，确保加入四六级后其他端数据不变；
     *  - 教师：传入则按所选词库，不传则不过滤（管理端可看全部词库）。
     */
    private String resolveListBank(String bank) {
        if (!"teacher".equals(AuthContext.role())) {
            return STUDENT_BANK;
        }
        return (bank == null || bank.trim().isEmpty()) ? null : bank.trim();
    }

    /** 随机抽词的词库解析：任何角色未指定时都默认「学生词库」 */
    private String resolveRandomBank(String bank) {
        return (bank == null || bank.trim().isEmpty()) ? STUDENT_BANK : bank.trim();
    }

    // ==================== 词汇搜索（单体版：MySQL LIKE） ====================

    @Override
    public Map<String, Object> esSearch(String keyword, int page, int size) {
        return esSearch(keyword, page, size, null);
    }

    @Override
    public Map<String, Object> esSearch(String keyword, int page, int size, String bank) {
        // 单体版未接入 ES：直接 MySQL LIKE 搜索（单词 + 释义），行为与微服务版 ES 降级路径一致
        Page<Vocabulary> p = list(page, size, keyword, bank);
        List<Map<String, Object>> records = p.getRecords().stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("word", v.getWord());
            m.put("phonetic", v.getPhonetic());
            m.put("translation", v.getTranslation());
            m.put("partOfSpeech", v.getPartOfSpeech());
            m.put("extensions", v.getExtensions());
            m.put("bank", v.getBank());
            m.put("createTime", v.getCreateTime());
            return m;
        }).toList();
        return Map.of("records", records, "total", p.getTotal());
    }

    @Override
    public long syncAllToES() {
        // 单体版无 ES 索引可同步，返回 0 表示无变化
        return 0;
    }
}