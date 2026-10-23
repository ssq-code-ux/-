-- =====================================================================
-- 19_migrate_refresh_token.sql
-- Refresh Token 落库（登录态跨重启长期有效）
--
-- 背景：TokenService 原先把 refresh token 存在进程内 ConcurrentHashMap，
--       每次 docker compose up -d --build 重启就清空，在线用户全被踢回登录页；
--       再加上有效期只有 7 天，闲置一周以上也要重新登录。
--       落库 + 长有效期后：登录一次即长期有效，只有点「退出登录」（删行吊销）才需要重登。
--
-- 幂等性：CREATE TABLE IF NOT EXISTS，重复执行不会报错。
-- 兜底：  后端启动时会执行同样的建表语句（SysRefreshTokenMapper.createTableIfAbsent），
--         所以这一步漏跑不会导致登录崩，但仍建议与其它迁移一起执行。
-- 执行库：english_tutor_system
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_refresh_token` (
  `jti`       VARCHAR(64) NOT NULL COMMENT 'Refresh Token 的 jti（UUID），主键',
  `user_id`   BIGINT      NOT NULL COMMENT '用户ID',
  `expire_at` BIGINT      NOT NULL COMMENT '过期时间戳（毫秒）',
  PRIMARY KEY (`jti`),
  KEY `idx_rt_user` (`user_id`),
  KEY `idx_rt_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh Token 持久化（登录态跨重启长期有效）';
