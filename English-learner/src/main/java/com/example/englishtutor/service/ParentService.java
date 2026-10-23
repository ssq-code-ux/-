package com.example.englishtutor.service;

import com.example.englishtutor.entity.User;
import com.example.englishtutor.vo.StudentAnalysisVO;

import java.util.List;
import java.util.Map;

public interface ParentService {
    /** 当前家长绑定的孩子列表 */
    List<User> myChildren();

    /** 按学生账号绑定孩子 */
    void bindChild(String childUsername);

    /** 孩子的学习分析报告 */
    StudentAnalysisVO childAnalysis(Long childId);

    /** 孩子的作业列表 */
    List<Map<String, Object>> childHomeworks(Long childId);

    /** 孩子的学习周报（最近 7 天聚合） */
    Map<String, Object> childWeeklyReport(Long childId);

    /** 某份作业的详细内容（题目/提交/批改结果），家长查看用 */
    Map<String, Object> childHomeworkDetail(Long childId, Long homeworkId);

    /** 孩子的 AI 内容（type=daily_advice 每日建议 / weekly_summary 周报总结），读当日/当周缓存 */
    Map<String, Object> childAiContent(Long childId, String type);

    /** 手动刷新孩子的 AI 内容（重新生成并覆盖缓存） */
    Map<String, Object> refreshChildAiContent(Long childId, String type);

    /** 定时预生成入口（无权限校验，供调度任务调用） */
    String pregenerateAiContent(Long studentId, String type);

    /** 学生的周报聚合数据（无权限校验，供周报推送拼文案） */
    Map<String, Object> weeklyReportData(Long studentId);

    /** 当前家长的站内通知列表（最新在前） */
    List<Map<String, Object>> myNotifications();

    /** 当前家长的未读通知数 */
    Long unreadCount();

    /** 标记单条通知已读 */
    void markRead(Long notificationId);

    /** 全部标记已读 */
    void markAllRead();
}
