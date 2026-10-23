package com.example.englishtutor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.entity.Homework;
import com.example.englishtutor.entity.SmsNotifyRecord;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.HomeworkMapper;
import com.example.englishtutor.mapper.SmsNotifyRecordMapper;
import com.example.englishtutor.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 作业快到截止时间还没交 → 提前提醒（赶在逾期之前救一把）
 *
 * 与 OverdueSmsTask 的分工：
 * - 本任务：截止前 2 小时窗口内提醒「学生 + 家长」，逾期前主动拦截
 * - OverdueSmsTask：已经逾期了才提醒家长（每天一次，附短信兜底）
 *
 * 执行策略：
 * - 每 15 分钟扫描一次，且只在 8:00-21:00 之间提醒（避免深夜打扰）
 * - 同一作业整段截止周期只提醒一次（sms_notify_record 按 bizType+bizId 去重）；
 *   学生在窗口内提交了，状态变化后后续扫描自然跳过
 * - 学生未绑定家长 → 只提醒学生；推送/站内/偏好过滤都复用 NotifyService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineRemindTask {

    private static final String BIZ_TYPE = "homework_deadline";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HomeworkMapper homeworkMapper;
    private final UserMapper userMapper;
    private final SmsNotifyRecordMapper recordMapper;
    private final NotifyService notifyService;

    @Scheduled(cron = "0 */15 8-21 * * ?")
    public void scanDeadlineAndNotify() {
        LocalDateTime now = LocalDateTime.now();
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .in(Homework::getStatus, "未提交", "需重做")
                .gt(Homework::getDeadline, now)
                .le(Homework::getDeadline, now.plusHours(2)));
        if (list.isEmpty()) return;

        for (Homework h : list) {
            try {
                notifyOne(h);
            } catch (Exception e) {
                log.error("[截止提醒] 处理作业 {} 异常", h.getId(), e);
            }
        }
    }

    private void notifyOne(Homework h) {
        // 整段截止周期只发一次：发过记录就跳过，任务重启也不会重发
        Long sent = recordMapper.selectCount(new LambdaQueryWrapper<SmsNotifyRecord>()
                .eq(SmsNotifyRecord::getBizType, BIZ_TYPE)
                .eq(SmsNotifyRecord::getBizId, h.getId()));
        if (sent > 0) return;

        User student = userMapper.selectById(h.getStudentId());
        if (student == null) return;

        String hwTitle = h.getTitle() != null ? h.getTitle() : "未命名作业";
        String deadline = h.getDeadline().format(FMT);

        notifyService.notifyUser(student.getId(), BIZ_TYPE, "作业即将截止",
                String.format("《%s》将于 %s 截止，还有最后约 2 小时，抓紧完成提交～", hwTitle, deadline), h.getId());

        User parent = student.getParentId() != null ? userMapper.selectById(student.getParentId()) : null;
        if (parent != null) {
            String name = student.getRealName() != null ? student.getRealName() : student.getUsername();
            notifyService.notifyUser(parent.getId(), BIZ_TYPE, "孩子的作业即将截止",
                    String.format("%s 的作业《%s》将于 %s 截止，还未提交，请提醒孩子抓紧。", name, hwTitle, deadline), h.getId());
        }

        SmsNotifyRecord r = new SmsNotifyRecord();
        r.setBizType(BIZ_TYPE);
        r.setBizId(h.getId());
        r.setPhone(parent != null && parent.getPhone() != null ? parent.getPhone() : "-");
        r.setStatus("SENT");
        r.setMessage("截止前提醒已发送（学生" + (parent != null ? "+家长" : "") + "）");
        r.setCreateTime(LocalDateTime.now());
        recordMapper.insert(r);
        log.info("[截止提醒] 已提醒：学生 {} 的作业《{}》将于 {} 截止",
                student.getUsername(), hwTitle, deadline);
    }
}
