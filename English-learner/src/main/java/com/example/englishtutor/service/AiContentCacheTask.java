package com.example.englishtutor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.entity.AiContentCache;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AiContentCacheMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.impl.ParentServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家长端 AI 内容预生成任务
 * <p>
 * 「学习建议」一天一份、「周报总结」一周一份，定时任务在家长登录之前生成好，
 * 家长打开页面直接读缓存秒开，不再现场等 AI 十几秒。
 * 周报生成完会顺手推一条微信给家长（文案见 WeeklyReportPushService，可退订）。
 * <p>
 * 除了两个全量刷新点，白天每小时还有一次补漏（只给当前周期还没有缓存的学生生成，
 * 不覆盖已有内容），这样服务重启错过定时点、或新绑定的学生也能在一个小时内补上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContentCacheTask {

    private final UserMapper userMapper;
    private final AiContentCacheMapper aiCacheMapper;
    private final ParentService parentService;
    private final WeeklyReportPushService weeklyReportPush;

    /** 每天 6:30 全量刷新当日学习建议（一天一更新） */
    @Scheduled(cron = "0 30 6 * * ?")
    public void refreshDailyAdvice() {
        pregenerateAll(ParentServiceImpl.TYPE_DAILY, "每日学习建议");
    }

    /** 每周一 6:45 全量生成本周周报总结（一周一更新） */
    @Scheduled(cron = "0 45 6 * * MON")
    public void refreshWeeklySummary() {
        pregenerateAll(ParentServiceImpl.TYPE_WEEKLY, "周报总结");
    }

    /** 7~21 点每小时 20 分补漏：只给当前周期还没有缓存的学生生成，不动已有内容 */
    @Scheduled(cron = "0 20 7-21 * * ?")
    public void fillMissing() {
        fillMissingFor(ParentServiceImpl.TYPE_DAILY, "每日学习建议", LocalDate.now().toString());
        fillMissingFor(ParentServiceImpl.TYPE_WEEKLY, "周报总结", weekKey());
    }

    private void pregenerateAll(String type, String label) {
        List<User> students = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student"));
        log.info("[家长端AI] 开始预生成{}：共 {} 名学生", label, students.size());
        int ok = 0, fail = 0;
        for (User s : students) {
            try {
                parentService.pregenerateAiContent(s.getId(), type);
                ok++;
                // 周报生成完顺手推给家长（每周只走一次这里，不会重复推）
                if (ParentServiceImpl.TYPE_WEEKLY.equals(type)) {
                    weeklyReportPush.pushToParent(s.getId());
                }
            } catch (Exception e) {
                fail++;
                log.warn("[家长端AI] {}生成失败 studentId={}：{}", label, s.getId(), e.getMessage());
            }
        }
        log.info("[家长端AI] {}预生成完成：成功 {}，失败 {}", label, ok, fail);
    }

    private void fillMissingFor(String type, String label, String periodKey) {
        Set<Long> done = aiCacheMapper.selectList(new LambdaQueryWrapper<AiContentCache>()
                        .eq(AiContentCache::getContentType, type)
                        .eq(AiContentCache::getPeriodKey, periodKey))
                .stream().map(AiContentCache::getStudentId).collect(Collectors.toSet());
        List<User> students = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student"));
        int ok = 0, fail = 0;
        for (User s : students) {
            if (done.contains(s.getId())) continue;
            try {
                parentService.pregenerateAiContent(s.getId(), type);
                ok++;
                // 补漏生成的周报同样推给家长（周一全量没跑成的学生，在这里补上推送）
                if (ParentServiceImpl.TYPE_WEEKLY.equals(type)) {
                    weeklyReportPush.pushToParent(s.getId());
                }
            } catch (Exception e) {
                fail++;
                log.warn("[家长端AI] {}补漏失败 studentId={}：{}", label, s.getId(), e.getMessage());
            }
        }
        if (ok > 0) {
            log.info("[家长端AI] {}补漏完成：新增 {} 份，失败 {} 份", label, ok, fail);
        }
    }

    /** ISO 周号（如 2026-W35），与 ParentServiceImpl.periodKeyOf 保持一致 */
    private String weekKey() {
        LocalDate today = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return String.format("%d-W%02d", today.get(wf.weekBasedYear()), today.get(wf.weekOfWeekBasedYear()));
    }
}
