package com.example.englishtutor.infra.redis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.entity.SysRefreshToken;
import com.example.englishtutor.mapper.SysRefreshTokenMapper;
import com.example.englishtutor.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 服务（单体版，无 Redis 依赖）
 *
 * 与微服务版 API 保持一致，仅把存储从 Redis 换成本地实现：
 *  - Refresh Token：落库 sys_refresh_token（jti 主键），存在即有效。
 *    原先存在进程内 ConcurrentHashMap，每次重启/发版就清空，所有在线用户被踢回登录页；
 *    落库 + 长有效期后，「登录一次、除非退出登录不用再登」才能跨重启成立。
 *  - 黑名单：已登出的 Access Token 仍放内存。access 只有 30 分钟寿命，
 *    重启丢黑名单最多影响半小时，不值得为每次登出写库。
 *
 * 说明：
 *  - 过期条目在读写时惰性清理，避免表/内存无限增长
 *  - 兼容旧版 token：双 Token 改造前签发的 token 没有 jti 声明，
 *    ConcurrentHashMap 的 get/put/remove 和库表主键都不接受 null，必须先判空
 *    （旧 token 不在黑名单/Refresh 表里，判空后按「不存在」处理即可）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final SysRefreshTokenMapper refreshTokenMapper;

    /** jti → 过期时间戳（毫秒），已登出的 access token 黑名单（内存即可） */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * 启动时幂等建表兜底：迁移脚本靠手工执行，漏跑会让下面的 saveRefreshToken 抛异常、
     * 连登录都进不去。建表失败只记日志不拦启动——此时数据库多半本身就连不上，
     * 业务接口一样不可用，让容器起来至少能在日志里看到原因。
     */
    @PostConstruct
    public void ensureRefreshTokenTable() {
        try {
            refreshTokenMapper.createTableIfAbsent();
        } catch (Exception e) {
            log.error("[登录态] sys_refresh_token 建表失败，请手工执行 sql/19_migrate_refresh_token.sql：{}",
                    e.getMessage());
        }
    }

    /** 保存 Refresh Token（刷新轮换时同一流程会先吊销旧的再存新的） */
    public void saveRefreshToken(String refreshToken, Long userId) {
        Claims claims = parseQuiet(refreshToken);
        // 无 jti 的不是本版本签发的 Refresh Token，无需保存
        if (claims == null || claims.getId() == null) {
            return;
        }
        // jti 是 UUID 不会撞，先删只是让「重复保存」变成覆盖而不是主键冲突
        refreshTokenMapper.deleteById(claims.getId());
        SysRefreshToken row = new SysRefreshToken();
        row.setJti(claims.getId());
        row.setUserId(userId);
        row.setExpireAt(System.currentTimeMillis() + jwtUtil.getRefreshExpiration());
        refreshTokenMapper.insert(row);
        // 轮换会让表规模≈在线会话数，这里顺手扫掉「再也不回来」的残留行（走 idx_rt_expire）
        refreshTokenMapper.delete(new LambdaQueryWrapper<SysRefreshToken>()
                .lt(SysRefreshToken::getExpireAt, System.currentTimeMillis()));
    }

    /** 校验 Refresh Token 是否有效（未被吊销且未过期） */
    public boolean isRefreshTokenValid(String refreshToken) {
        Claims claims = parseQuiet(refreshToken);
        // 签名不对、已过期、不是 refresh 类型、没有 jti —— 一律视为无效
        if (claims == null || !"refresh".equals(claims.get("type")) || claims.getId() == null) {
            return false;
        }
        SysRefreshToken row = refreshTokenMapper.selectById(claims.getId());
        // 不在表里 = 已退出登录，或已被上一次刷新轮换掉
        if (row == null) {
            return false;
        }
        if (row.getExpireAt() == null || row.getExpireAt() < System.currentTimeMillis()) {
            refreshTokenMapper.deleteById(claims.getId());
            return false;
        }
        return true;
    }

    /** 删除 Refresh Token（登出/刷新时吊销） */
    public void revokeRefreshToken(String refreshToken) {
        Claims claims = parseQuiet(refreshToken);
        // 旧版 token / 无效 token 没有 jti，本来就不在表里
        if (claims == null || claims.getId() == null) {
            return;
        }
        refreshTokenMapper.deleteById(claims.getId());
    }

    /** 将 Access Token 加入黑名单 */
    public void addToBlacklist(String accessToken, long ttlMillis) {
        Claims claims = parseQuiet(accessToken);
        // 旧版 token 没有 jti、已过期/无效 token 解析不出东西，都无从拉黑（自然过期即可）。
        // 这里必须判空：ConcurrentHashMap 不接受 null key，否则会 NPE 把登出接口打成 500
        if (claims == null || claims.getId() == null) {
            return;
        }
        blacklist.put(claims.getId(), System.currentTimeMillis() + ttlMillis);
    }

    /** 检查 Access Token 是否在黑名单中（已登出） */
    public boolean isBlacklisted(String accessToken) {
        try {
            Claims claims = jwtUtil.parse(accessToken);
            // 旧版（双 Token 改造前）签发的 Access Token 没有 jti 声明；
            // 黑名单只记录新版登出的 token，旧 token 直接放行（直到自然过期）
            if (claims.getId() == null) {
                return false;
            }
            Long expireAt = blacklist.get(claims.getId());
            if (expireAt == null) {
                return false;
            }
            if (expireAt < System.currentTimeMillis()) {
                blacklist.remove(claims.getId());
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            return true;
        }
    }

    /** 解析失败统一返回 null，调用方按「无效 token」处理 */
    private Claims parseQuiet(String token) {
        try {
            return jwtUtil.parse(token);
        } catch (JwtException e) {
            return null;
        }
    }
}
