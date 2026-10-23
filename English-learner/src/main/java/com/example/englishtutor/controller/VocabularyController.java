package com.example.englishtutor.controller;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.R;
import com.example.englishtutor.entity.Vocabulary;
import com.example.englishtutor.service.VocabularyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vocabularies")
@RequiredArgsConstructor
public class VocabularyController {
    private final VocabularyService service;

    @PostMapping
    public R<Void> add(@Valid @RequestBody Vocabulary v) { service.add(v); return R.ok(null); }

    /** 基础列表（MySQL LIKE 搜索，兼容旧接口）；bank 可选，指定则按词库过滤 */
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "6") int size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String bank) {
        var p = service.list(page, size, keyword, bank);
        return R.ok(Map.of("records", p.getRecords(), "total", p.getTotal()));
    }

    /** 词汇搜索（单词/释义模糊匹配，分页；微服务版为 ES 全文检索优先、LIKE 降级） */
    @GetMapping("/search")
    public R<Map<String, Object>> search(@RequestParam String keyword,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "10") int size,
                     @RequestParam(required = false) String bank) {
        return R.ok(service.esSearch(keyword, page, size, bank));
    }

    /** 全部词库名称（教师端「背单词」/词汇管理选择词库用） */
    @GetMapping("/banks")
    public R<List<String>> banks() {
        return R.ok(service.banks());
    }

    /** 重建搜索索引（教师操作；单体版无 ES，为前端兼容保留，恒返回 synced=0） */
    @PostMapping("/sync-es")
    public R<Map<String, Object>> syncES() {
        if (!"teacher".equals(AuthContext.role())) throw new BizException("仅教师可操作");
        long count = service.syncAllToES();
        return R.ok(Map.of("synced", count));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody Vocabulary v) { service.update(id, v); return R.ok(null); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { service.delete(id); return R.ok(null); }

    @GetMapping("/random")
    public R<List<Vocabulary>> random(@RequestParam(defaultValue = "10") Long count,
                     @RequestParam(required = false) String bank) {
        return R.ok(service.random(count, bank));
    }
}