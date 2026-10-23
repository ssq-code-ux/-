package com.example.englishtutor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Refresh Token 持久化
 * <p>
 * 原先 refresh token 存在 TokenService 的进程内 ConcurrentHashMap 里，
 * 每次重启/发版就清空，所有在线用户被强制重新登录。
 * 落库后「登录一次、长期有效、退出即吊销」才能跨重启成立。
 */
@Data
@TableName("sys_refresh_token")
public class SysRefreshToken {

    /** Refresh Token 的 jti（UUID），由业务侧生成，不用自增 */
    @TableId(type = IdType.INPUT)
    private String jti;

    private Long userId;

    /** 过期时间戳（毫秒） */
    private Long expireAt;
}
