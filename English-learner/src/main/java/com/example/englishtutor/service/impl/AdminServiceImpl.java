package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.SysRefreshToken;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.SysRefreshTokenMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.AdminService;
import com.example.englishtutor.service.InviteCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端实现 — 防线只有一道但必须牢固：assertAdmin()。
 * AuthInterceptor 只校验“登录了”，不校验“是什么角色”；角色校验在每个业务方法里做
 * （和 StudentService/ParentService 同一套约定，通过 FeignAuthContext 适配层读当前登录人）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final NotificationMapper notificationMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final InviteCodeService inviteCodeService;
    private final SysRefreshTokenMapper refreshTokenMapper;

    private static final Set<String> ROLES = Set.of("teacher", "student", "parent");

    // ==================== 用户管理 ====================

    @Override
    public Map<String, Object> users(String keyword, String role, Integer status, long page, long size) {
        assertAdmin();
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 一个关键字同时搜账号、姓名、手机号
            q.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (role != null && !role.isBlank()) {
            q.eq(User::getRole, role);
        }
        if (status != null) {
            q.eq(User::getStatus, status);
        }
        q.orderByDesc(User::getCreateTime);

        Page<User> p = userMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
        List<User> records = p.getRecords();
        records.forEach(u -> u.setPassword(null));

        // 学生的"所属老师/家长"名字批量补齐（列表直接可看，前端不用再查）
        Set<Long> refIds = new HashSet<>();
        for (User u : records) {
            if (u.getTeacherId() != null) refIds.add(u.getTeacherId());
            if (u.getParentId() != null) refIds.add(u.getParentId());
            if (u.getInviterId() != null) refIds.add(u.getInviterId());
        }
        Map<Long, String> nameMap = refIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(refIds).stream()
                        .collect(Collectors.toMap(User::getId, u ->
                                u.getRealName() != null ? u.getRealName() : u.getUsername()));

        // 需求 5：一次 GROUP BY 拿到本页所有人的邀请人数，避免逐行查询的 N+1
        Map<Long, Long> invitedCounts = inviteCodeService.invitedCounts(
                records.stream().map(User::getId).collect(Collectors.toSet()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("role", u.getRole());
            m.put("status", u.getStatus() == null ? 1 : u.getStatus());
            m.put("phone", u.getPhone());
            m.put("teacherId", u.getTeacherId());
            // Map.of() 的不可变 Map 不允许 null 键（get(null) 直接 NPE），先判空再取
            m.put("teacherName", u.getTeacherId() == null ? null : nameMap.get(u.getTeacherId()));
            m.put("parentId", u.getParentId());
            m.put("parentName", u.getParentId() == null ? null : nameMap.get(u.getParentId()));
            m.put("inviteCode", u.getInviteCode());
            m.put("inviterId", u.getInviterId());
            m.put("inviterName", u.getInviterId() == null ? null : nameMap.get(u.getInviterId()));
            m.put("invitedCount", invitedCounts.getOrDefault(u.getId(), 0L));
            m.put("createTime", u.getCreateTime());
            list.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return result;
    }

    /** 需求 5：点开某位老师看“他邀请了哪几位老师” */
    @Override
    public List<Map<String, Object>> invitedUsers(Long id) {
        assertAdmin();
        mustGetUser(id);
        return inviteCodeService.invitedUsers(id);
    }

    @Override
    public String resetPassword(Long id) {
        assertAdmin();
        User target = mustGetUser(id);
        if (Objects.equals(id, FeignAuthContext.userId())) {
            throw new BizException("不能重置自己的密码，请使用「修改密码」功能");
        }
        // 统一重置为初始密码，明文返回给管理员，由管理员线下告知用户
        String newPwd = "123456";
        target.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(target);

        audit(AdminAuditLog.ACTION_RESET_PASSWORD, id,
                "重置用户 " + target.getUsername() + " 的密码");
        notifyUser(target.getId(), "密码已被重置",
                "管理员已将您的密码重置为初始密码 " + newPwd + "，请尽快登录并修改。");
        return newPwd;
    }

    @Override
    public void updatePassword(Long id, String newPassword) {
        assertAdmin();
        User target = mustGetUser(id);
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        target.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(target);

        audit(AdminAuditLog.ACTION_UPDATE_PASSWORD, id,
                "管理员修改用户 " + target.getUsername() + " 的密码");
        notifyUser(target.getId(), "密码已被修改",
                "管理员已修改您的登录密码，请使用新密码登录。");
    }

    @Override
    public void deleteUser(Long id) {
        assertAdmin();
        User target = mustGetUser(id);
        if ("admin".equals(target.getRole())) {
            throw new BizException("不能删除管理员账号");
        }
        if (Objects.equals(id, FeignAuthContext.userId())) {
            throw new BizException("不能删除自己");
        }
        // 清理绑定关系：任何指向该用户的 teacherId / parentId 一律置空，避免留下指向已删账号的脏数据。
        // 必须用 UpdateWrapper.set(col, null) 显式写 NULL —— MyBatis-Plus updateById 默认策略(NOT_NULL)
        // 会跳过实体中为 null 的字段，setParentId(null)+updateById 根本清不掉列（这正是“删了家长学生仍绑着”的根因）。
        // 按引用清而非按角色清：无论被删的是教师还是家长，只要有人绑到它就一并解除。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getTeacherId, null).eq(User::getTeacherId, id));
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getParentId, null).eq(User::getParentId, id));
        // 吊销该用户所有 Refresh Token，登录态立即失效（存量 Access 最长 30 分钟自然过期）
        refreshTokenMapper.delete(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getUserId, id));
        userMapper.deleteById(id);

        audit(AdminAuditLog.ACTION_DELETE_USER, id,
                "删除用户 " + target.getUsername() + "（角色 " + target.getRole() + "）");
    }

    @Override
    public void setUserStatus(Long id, Integer status) {
        assertAdmin();
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("status 只能为 0（封禁）或 1（正常）");
        }
        User target = mustGetUser(id);
        if ("admin".equals(target.getRole())) {
            throw new BizException("不能封禁管理员账号");
        }
        if (Objects.equals(id, FeignAuthContext.userId())) {
            throw new BizException("不能封禁自己");
        }
        target.setStatus(status);
        userMapper.updateById(target);

        audit(AdminAuditLog.ACTION_SET_STATUS, id,
                (status == 0 ? "封禁" : "解封") + "用户 " + target.getUsername());
        // 封禁立即生效于下次登录/刷新；存量 Access Token 最长 2 小时后自然失效
        if (status == 0) {
            notifyUser(target.getId(), "账号已被封禁",
                    "您的账号已被管理员封禁，如有疑问请联系管理员。");
        }
    }

    @Override
    public void updateRole(Long id, String role) {
        assertAdmin();
        if (role == null || !ROLES.contains(role)) {
            throw new BizException("角色只能是 teacher / student / parent");
        }
        User target = mustGetUser(id);
        if ("admin".equals(target.getRole())) {
            throw new BizException("不能调整管理员的角色");
        }
        String oldRole = target.getRole();
        // 换角色后清掉不再有意义的绑定关系，避免"老师账号挂着家长"的脏数据。
        // 同样必须用 UpdateWrapper.set(col,null) 才能把列真正写成 NULL（updateById 默认跳过 null 字段）。
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .set(User::getRole, role)
                .eq(User::getId, id);
        if ("teacher".equals(role)) {
            uw.set(User::getParentId, null);
        } else if ("parent".equals(role)) {
            uw.set(User::getTeacherId, null);
        }
        userMapper.update(null, uw);

        audit(AdminAuditLog.ACTION_UPDATE_ROLE, id,
                "用户 " + target.getUsername() + " 角色 " + oldRole + " → " + role);
        notifyUser(target.getId(), "账号角色已调整",
                "管理员已将您的账号角色调整为「" + role + "」，重新登录后生效。");
    }

    @Override
    public void unbind(Long id, String type) {
        assertAdmin();
        User target = mustGetUser(id);
        if ("parent".equals(type)) {
            if (target.getParentId() == null) {
                throw new BizException("该账号没有绑定家长");
            }
            Long parentId = target.getParentId();
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getParentId, null).eq(User::getId, id));
            audit(AdminAuditLog.ACTION_UNBIND_PARENT, id,
                    "解除用户 " + target.getUsername() + " 与家长的绑定（原家长ID=" + parentId + "）");
        } else if ("teacher".equals(type)) {
            if (target.getTeacherId() == null) {
                throw new BizException("该账号没有绑定老师");
            }
            Long teacherId = target.getTeacherId();
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getTeacherId, null).eq(User::getId, id));
            audit(AdminAuditLog.ACTION_UNBIND_TEACHER, id,
                    "解除用户 " + target.getUsername() + " 与老师的绑定（原老师ID=" + teacherId + "）");
        } else {
            throw new BizException("解绑类型只能是 parent 或 teacher");
        }
    }

    // ==================== 数据看板 ====================

    @Override
    public Map<String, Object> dashboard() {
        assertAdmin();

        Map<String, Object> result = new LinkedHashMap<>();

        // —— 用户维度 ——
        Map<String, Long> roleCount = new LinkedHashMap<>();
        for (String r : List.of("teacher", "student", "parent")) {
            roleCount.put(r, userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getRole, r)));
        }
        result.put("totalUsers", roleCount.values().stream().mapToLong(Long::longValue).sum());
        result.put("roleCount", roleCount);
        result.put("bannedUsers", userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 0)));

        result.put("totalNotifications", notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()));

        // —— 最近 7 天注册趋势（今天往前推 6 天，补零对齐）——
        LocalDate start = LocalDate.now().minusDays(6);
        List<User> recent = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getCreateTime)
                .ge(User::getCreateTime, start.atStartOfDay()));
        Map<LocalDate, Long> byDay = recent.stream()
                .filter(u -> u.getCreateTime() != null)
                .collect(Collectors.groupingBy(u -> u.getCreateTime().toLocalDate(), Collectors.counting()));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.format(fmt));
            point.put("count", byDay.getOrDefault(d, 0L));
            trend.add(point);
        }
        result.put("registerTrend", trend);
        return result;
    }

    // ==================== 审计日志 ====================

    @Override
    public Map<String, Object> auditLogs(String action, long page, long size) {
        assertAdmin();
        LambdaQueryWrapper<AdminAuditLog> q = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            q.eq(AdminAuditLog::getAction, action);
        }
        q.orderByDesc(AdminAuditLog::getCreateTime);
        Page<AdminAuditLog> p = auditLogMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    // ==================== 内部工具 ====================

    /** 管理端统一防线：当前登录人必须是 admin 角色 */
    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    private User mustGetUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        return u;
    }

    /** 敏感操作留痕：谁、何时、动了谁、做了什么、从哪来 */
    private void audit(String action, Long targetUserId, String detail) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            l.setOperatorId(FeignAuthContext.userId());
            l.setOperatorName(FeignAuthContext.username());
            l.setAction(action);
            l.setTargetUserId(targetUserId);
            l.setDetail(detail);
            l.setIp(clientIp());
            l.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(l);
        } catch (Exception e) {
            // 审计失败不阻断主流程，但必须记日志（后台可对账）
            log.error("[审计] 写入失败 action={} target={}：{}", action, targetUserId, e.getMessage());
        }
    }

    /** 给用户发一条站内通知（铃铛可见），失败不影响主流程 */
    private void notifyUser(Long userId, String title, String content) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType("system");
            n.setTitle(title);
            n.setContent(content);
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("[管理端] 站内通知发送失败 userId={}：{}", userId, e.getMessage());
        }
    }

    /** 取客户端真实 IP：优先网关/代理透传的 X-Forwarded-For 第一个值 */
    private String clientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
