package com.example.englishtutor.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.entity.Vocabulary;
import java.util.List;
import java.util.Map;

public interface VocabularyService {
    /** 默认词库：数据库原本的词汇都归入该词库，学生端固定使用该词库 */
    String STUDENT_BANK = "学生词库";

    void add(Vocabulary v);
    void update(Long id, Vocabulary v);
    Page<Vocabulary> list(int page, int size, String keyword);
    /** 按词库分页：教师可传 bank 指定词库；学生/家长端固定为学生词库 */
    Page<Vocabulary> list(int page, int size, String keyword, String bank);
    void delete(Long id);
    List<Vocabulary> random(Long count);
    /** 按词库随机抽词（bank 为空时默认学生词库） */
    List<Vocabulary> random(Long count, String bank);
    Vocabulary getById(Long id);
    List<Vocabulary> getByIds(List<Long> ids);

    /** 全部词库名称（学生词库始终排在最前） */
    List<String> banks();

    /** 词汇搜索：返回 records + total（单体版为 MySQL LIKE，微服务版为 ES 全文检索优先、LIKE 降级） */
    Map<String, Object> esSearch(String keyword, int page, int size);
    /** 按词库搜索 */
    Map<String, Object> esSearch(String keyword, int page, int size, String bank);

    /** 全量同步到搜索索引（单体版无 ES，恒返回 0） */
    long syncAllToES();
}