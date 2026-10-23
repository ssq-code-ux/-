package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.entity.Notification;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.infra.redis.TokenService;
import com.example.englishtutor.mapper.NotificationMapper;
import com.example.englishtutor.mapper.UserMapper;
import com.example.englishtutor.service.AuthService;
import com.example.englishtutor.service.InviteCodeService;
import com.example.englishtutor.util.JwtUtil;
import com.example.englishtutor.vo.LoginVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final NotificationMapper notificationMapper;
    private final InviteCodeService inviteCodeService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginVO login(LoginDTO d) {
        User u = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, d.getUsername()));
        if (u == null) {
            throw new BizException("账号或密码错误");
        }

        String storedPassword = u.getPassword();

        // 首次尝试 BCrypt 比对
        boolean matched;
        try {
            matched = passwordEncoder.matches(d.getPassword(), storedPassword);
        } catch (Exception e) {
            matched = false;
        }

        // 兼容旧数据：明文比对
        if (!matched && d.getPassword().equals(storedPassword)) {
            // 自动升级为 BCrypt
            u.setPassword(passwordEncoder.encode(d.getPassword()));
            userMapper.updateById(u);
            matched = true;
        }

        if (!matched) {
            throw new BizException("账号或密码错误");
        }

        // 封禁账号直接拒绝（管理端封禁后即使密码正确也进不来）
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException("账号已被封禁，请联系管理员");
        }

        // 双 Token：Access（短有效期）+ Refresh（长有效期，服务端可吊销）
        String accessToken = jwtUtil.generateAccess(u.getId(), u.getUsername(), u.getRole());
        String refreshToken = jwtUtil.generateRefresh(u.getId());
        tokenService.saveRefreshToken(refreshToken, u.getId());

        LoginVO vo = new LoginVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setRole(u.getRole());
        vo.setTeacherId(u.getTeacherId());
        vo.setToken(accessToken);
        vo.setRefreshToken(refreshToken);

        // 学生登录时返回绑定老师信息
        if ("student".equals(u.getRole()) && u.getTeacherId() != null) {
            User teacher = userMapper.selectById(u.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
                vo.setTeacherPhone(teacher.getPhone());
            }
        }
        return vo;
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException("请输入原密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException("新密码不能与原密码相同");
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        // 校验原密码：先 BCrypt 比对，兼容历史明文数据
        boolean matched;
        try {
            matched = passwordEncoder.matches(oldPassword, u.getPassword());
        } catch (Exception e) {
            matched = false;
        }
        if (!matched && oldPassword.equals(u.getPassword())) {
            matched = true;
        }
        if (!matched) {
            throw new BizException("原密码不正确");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(u);
        log.info("[修改密码] 用户 {} 修改了自己的登录密码", u.getUsername());
    }

    public void register(RegisterDTO d) {
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, d.getUsername())) > 0) {
            throw new BizException("用户名已存在");
        }
        // 家长注册：先校验孩子账号存在且是学生，避免绑定到错误账号
        User child = null;
        if ("parent".equals(d.getRole()) && d.getChildUsername() != null && !d.getChildUsername().isBlank()) {
            child = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, d.getChildUsername().trim()));
            if (child == null || !"student".equals(child.getRole())) {
                throw new BizException("孩子的学生账号不存在，请核对后再注册");
            }
        }
        // 需求 5：填了邀请码就建立邀请关系（邀请码不存在直接拦下，否则静默丢弃会让老师以为邀请成功了）
        User inviter = null;
        if (d.getInviteCode() != null && !d.getInviteCode().isBlank()) {
            inviter = inviteCodeService.findByCode(d.getInviteCode());
            if (inviter == null) {
                throw new BizException("邀请码不存在，请核对后再注册");
            }
        }
        User u = new User();
        u.setUsername(d.getUsername());
        u.setPassword(passwordEncoder.encode(d.getPassword()));
        u.setRealName(d.getRealName());
        u.setRole(d.getRole());
        u.setTeacherId(d.getTeacherId());
        u.setPhone(d.getPhone());
        u.setInviterId(inviter == null ? null : inviter.getId());
        // 每个新账号都发一个属于自己的唯一邀请码，转介绍可以一直传下去
        u.setInviteCode(inviteCodeService.generateUnique());
        u.setCreateTime(LocalDateTime.now());
        userMapper.insert(u);
        // 注册成功后把孩子挂到家长名下（一个学生只允许绑定一位家长，先到先得）
        if (child != null && child.getParentId() == null) {
            child.setParentId(u.getId());
            userMapper.updateById(child);
        }
        if (inviter != null) {
            notifyInviter(inviter, u);
        }
    }

    /** 告知邀请人“有人用你的邀请码注册了”，失败不能影响注册主流程 */
    private void notifyInviter(User inviter, User newcomer) {
        try {
            String name = newcomer.getRealName() != null && !newcomer.getRealName().isBlank()
                    ? newcomer.getRealName() : newcomer.getUsername();
            Notification n = new Notification();
            n.setUserId(inviter.getId());
            n.setType("system");
            n.setTitle("邀请注册成功");
            n.setContent("「" + name + "」（" + roleText(newcomer.getRole()) + "）通过您的邀请码 "
                    + inviter.getInviteCode() + " 注册了平台，您已累计邀请 "
                    + inviteCodeService.invitedCount(inviter.getId()) + " 人。");
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("[邀请码] 通知邀请人失败 inviterId={}：{}", inviter.getId(), e.getMessage());
        }
    }

    private String roleText(String role) {
        if ("teacher".equals(role)) return "教师";
        if ("parent".equals(role)) return "家长";
        if ("admin".equals(role)) return "管理员";
        return "学生";
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (!tokenService.isRefreshTokenValid(refreshToken)) {
            throw new BizException("登录已过期，请重新登录");
        }

        Claims claims = jwtUtil.parse(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        // 封禁后 Refresh Token 也不能再换新 Access（存量 Token 最长 2 小时后自然失效）
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException("账号已被封禁，请联系管理员");
        }

        // 不轮换 Refresh Token：同一 Refresh 在有效期内一直可用。
        // 原先「刷新即作废旧 Refresh」在多标签页/并发请求下会互相作废，
        // 表现为「一段时间没操作就被迫重新登录」；这里改为只续发 Access，彻底去掉该问题。
        String newAccess = jwtUtil.generateAccess(u.getId(), u.getUsername(), u.getRole());

        LoginVO vo = new LoginVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setRole(u.getRole());
        vo.setTeacherId(u.getTeacherId());
        vo.setToken(newAccess);
        vo.setRefreshToken(refreshToken);
        return vo;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            tokenService.addToBlacklist(accessToken, jwtUtil.getAccessExpiration());
        }
        if (refreshToken != null) {
            tokenService.revokeRefreshToken(refreshToken);
        }
    }
}
