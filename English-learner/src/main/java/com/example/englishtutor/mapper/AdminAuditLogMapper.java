package com.example.englishtutor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.englishtutor.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端审计日志 Mapper — 管理员的敏感操作全部走这里落库留痕
 */
@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
