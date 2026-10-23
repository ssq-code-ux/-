package com.example.englishtutor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.entity.Homework;
import com.example.englishtutor.entity.SmsNotifyRecord;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.HomeworkMapper;
import com.example.englishtutor.mapper.SmsNotifyRecordMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.util.SmsOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业逾期未交 → 提醒家长
 *
 * 执行策略：
 * - 每 30 分钟扫描一次，且只在 8:00-21:00 之间提醒（避免深夜打扰）
 * - 主通道：站内通知（写入 notification 表，家长端铃铛可见）
 * - 补充渠道：微信推送（家长自己配了推送令牌才发）+ 阿里云短信（四项配置齐全时才发）
 * - 同一作业每天最多提醒一次（sms_notify_record 按天去重，持续到提交为止）
 * - 学生未绑定家长 → 自动跳过，不影响主流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueSmsTask {

    private static final String BIZ_TYPE = "homework_overdue";

    private final HomeworkMapper homeworkMapper;
    private final UserMapper userMapper;
    private final SmsNotifyRecordMapper recordMapper;
    private final NotifyService notifyService;
    private final SmsOperator smsOperator;

    @Scheduled(cron = "0 */30 8-21 * * ?")
    public void scanOverdueAndNotify() {
        List<Homework> overdueList = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .in(Homework::getStatus, "未提交", "需重做")
                .lt(Homework::getDeadline, LocalDateTime.now()));
        if (overdueList.isEmpty()) return;

        for (Homework h : overdueList) {
            try {
                notifyOne(h);
            } catch (Exception e) {
                log.error("[逾期提醒] 处理作业 {} 异常", h.getId(), e);
            }
        }
    }

    private void notifyOne(Homework h) {
        // 按天去重：当天已提醒过的作业不再重复提醒
        Long sentToday = recordMapper.selectCount(new LambdaQueryWrapper<SmsNotifyRecord>()
                .eq(SmsNotifyRecord::getBizType, BIZ_TYPE)
                .eq(SmsNotifyRecord::getBizId, h.getId())
                .eq(SmsNotifyRecord::getStatus, "SENT")
                .ge(SmsNotifyRecord::getCreateTime, LocalDate.now().atStartOfDay()));
        if (sentToday > 0) return;

        User student = userMapper.selectById(h.getStudentId());
        if (student == null || student.getParentId() == null) return; // 未绑定家长
        User parent = userMapper.selectById(student.getParentId());
        if (parent == null) return;

        String name = student.getRealName() != null ? student.getRealName() : student.getUsername();
        String hwTitle = h.getTitle() != null ? h.getTitle() : "未命名作业";
        String content = String.format("%s 的作业《%s》已过截止时间仍未提交，请提醒孩子尽快完成。", name, hwTitle);

        // 站内通知（家长端铃铛）+ 微信推送（未配置令牌自动跳过）
        boolean pushOk = notifyService.notifyUser(parent.getId(), BIZ_TYPE,
                "作业逾期未交提醒", content, h.getId());

        // 补充渠道：阿里云短信（未配置签名/模板时 SmsOperator 自动降级，不报错）
        boolean smsOk = false;
        if (parent.getPhone() != null && !parent.getPhone().isBlank()) {
            String shortTitle = hwTitle.length() > 20 ? hwTitle.substring(0, 20) : hwTitle;
            smsOk = smsOperator.send(parent.getPhone(), Map.of("name", name, "title", shortTitle));
        }

        SmsNotifyRecord r = new SmsNotifyRecord();
        r.setBizType(BIZ_TYPE);
        r.setBizId(h.getId());
        r.setPhone(parent.getPhone() != null ? parent.getPhone() : "-");
        r.setStatus("SENT");
        r.setMessage("站内通知已发送"
                + (pushOk ? "+微信推送" : "")
                + (smsOk ? "+短信" : ""));
        r.setCreateTime(LocalDateTime.now());
        recordMapper.insert(r);
        log.info("[逾期提醒] 已通知家长 {}：学生 {} 的作业《{}》逾期未交（微信:{} 短信:{}）",
                parent.getUsername(), name, hwTitle, pushOk ? "已发" : "跳过", smsOk ? "已发" : "跳过");
    }
}
