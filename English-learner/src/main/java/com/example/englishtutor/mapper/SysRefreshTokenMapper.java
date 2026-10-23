package com.example.englishtutor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.englishtutor.entity.SysRefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshToken> {

    /**
     * 幂等建表兜底。
     * <p>
     * 项目没有 flyway/liquibase，迁移脚本靠手工执行；漏跑 sql/19_migrate_refresh_token.sql
     * 会让 saveRefreshToken 直接抛异常，连登录都进不去，所以启动时自建一次。
     * IF NOT EXISTS：手工跑过脚本再执行也没有副作用。
     */
    @Update("CREATE TABLE IF NOT EXISTS `sys_refresh_token` ("
            + "`jti` VARCHAR(64) NOT NULL COMMENT 'Refresh Token 的 jti（UUID），主键',"
            + "`user_id` BIGINT NOT NULL COMMENT '用户ID',"
            + "`expire_at` BIGINT NOT NULL COMMENT '过期时间戳（毫秒）',"
            + "PRIMARY KEY (`jti`),"
            + "KEY `idx_rt_user` (`user_id`),"
            + "KEY `idx_rt_expire` (`expire_at`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh Token 持久化（登录态跨重启长期有效）'")
    void createTableIfAbsent();
}
