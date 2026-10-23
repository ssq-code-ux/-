package com.example.englishtutor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.englishtutor.entity.User;
import com.example.englishtutor.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邀请码服务（需求 5：每个老师都有自己的唯一邀请码，可邀请别的老师注册平台）。
 * <p>
 * 码池刻意去掉了 0/O、1/I/L 这些容易看错的字符；新生成的码固定 6 位，
 * 而 SQL 迁移给存量用户回填的码是 "T" + 6 位ID = 7 位，长度不同，永不碰撞。
 * 唯一性最终由 {@code sys_user.uk_invite_code} 在数据库层兜底，应用层先查重再生成。
 * <p>
 * 这里是具体类而非“接口 + Impl”：它只是一组围绕 sys_user 两列的无状态工具方法，
 * 没有多实现需求，拆接口只会增加跳转成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private static final char[] POOL = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LEN = 6;
    private static final int MAX_RETRY = 10;
    private static final SecureRandom RND = new SecureRandom();

    private final UserMapper userMapper;

    /** 生成一个未被占用的邀请码（重试若干次仍冲突则退化为时间戳码，保证一定拿得到） */
    public String generateUnique() {
        for (int i = 0; i < MAX_RETRY; i++) {
            StringBuilder sb = new StringBuilder(CODE_LEN);
            for (int j = 0; j < CODE_LEN; j++) {
                sb.append(POOL[RND.nextInt(POOL.length)]);
            }
            String code = sb.toString();
            Long dup = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getInviteCode, code));
            if (dup == null || dup == 0) {
                return code;
            }
        }
        // 极端情况：退化为 INV + 时间戳后 8 位，仍然唯一（长度 11，与 6/7 位码不冲突）
        return "INV" + (System.currentTimeMillis() % 100000000L);
    }

    /**
     * 确保该用户有邀请码，没有就补发。
     * 存量数据已由 SQL 迁移回填，这里兜的是迁移之后由其他路径新建、或迁移没跑到的账号。
     */
    public String ensure(User u) {
        if (u == null) return null;
        if (u.getInviteCode() != null && !u.getInviteCode().isBlank()) {
            return u.getInviteCode();
        }
        String code = generateUnique();
        u.setInviteCode(code);
        userMapper.updateById(u);
        return code;
    }

    /** 按邀请码找邀请人；找不到返回 null（是否报错由调用方决定） */
    public User findByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, code.trim().toUpperCase()));
    }

    /** 单个用户邀请了几个人 */
    public long invitedCount(Long userId) {
        if (userId == null) return 0L;
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getInviterId, userId));
        return c == null ? 0L : c;
    }

    /**
     * 批量统计邀请人数（管理端用户列表用，一次 GROUP BY 搞定，避免逐行查询的 N+1）。
     *
     * @return userId → 邀请人数；没邀请过任何人的用户不会出现在结果里
     */
    public Map<Long, Long> invitedCounts(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.select("inviter_id AS inviterId", "COUNT(*) AS cnt")
                .in("inviter_id", userIds)
                .groupBy("inviter_id");
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : userMapper.selectMaps(qw)) {
            Object id = row.get("inviterId");
            Object cnt = row.get("cnt");
            if (id == null || cnt == null) continue;
            try {
                result.put(Long.valueOf(String.valueOf(id)), Long.valueOf(String.valueOf(cnt)));
            } catch (NumberFormatException e) {
                log.warn("[邀请码] 统计结果解析失败：{}", row);
            }
        }
        return result;
    }

    /** 某人邀请的用户明细（管理端点开“邀请了几位老师”时看名单） */
    public List<Map<String, Object>> invitedUsers(Long userId) {
        List<User> list = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getInviterId, userId)
                .orderByDesc(User::getCreateTime));
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("role", u.getRole());
            m.put("phone", u.getPhone());
            m.put("createTime", u.getCreateTime());
            out.add(m);
        }
        return out;
    }
}
