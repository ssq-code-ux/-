package com.example.englishtutor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.common.FeignAuthContext;
import com.example.englishtutor.dto.AppVersionDTO;
import com.example.englishtutor.entity.AdminAuditLog;
import com.example.englishtutor.entity.AppVersion;
import com.example.englishtutor.mapper.AdminAuditLogMapper;
import com.example.englishtutor.mapper.AppVersionMapper;
import com.example.englishtutor.service.AppVersionService;
import com.example.englishtutor.util.AliyunOSSOperator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * App 版本 / wgt 热更新实现。
 * <p>
 * 检查更新（checkUpdate）是公开接口，客户端冷启动、甚至还没登录就要能拉；
 * 发布（publish）与列表（list）是管理端操作，沿用全站约定在方法内 assertAdmin()。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionServiceImpl implements AppVersionService {

    private final AppVersionMapper mapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final AliyunOSSOperator ossOperator;

    /** 只用于解析 wgt 包内部 manifest.json 的 version.code */
    private final ObjectMapper wgtMapper = new ObjectMapper();

    /** 启动自建表，避免漏跑迁移脚本导致检查更新接口报错 */
    @PostConstruct
    public void init() {
        try {
            mapper.createTableIfAbsent();
        } catch (Exception e) {
            log.error("[App版本] 启动建表失败：{}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> checkUpdate(String platform, Integer currentCode) {
        String pf = (platform == null || platform.isBlank()) ? "android" : platform;
        AppVersion latest = mapper.selectOne(new LambdaQueryWrapper<AppVersion>()
                .eq(AppVersion::getPlatform, pf)
                .orderByDesc(AppVersion::getVersionCode)
                .last("LIMIT 1"));

        Map<String, Object> r = new LinkedHashMap<>();
        int cur = currentCode == null ? 0 : currentCode;
        if (latest == null || latest.getVersionCode() == null || latest.getVersionCode() <= cur) {
            r.put("hasUpdate", false);
            return r;
        }
        // wgt 与 apk 至少有一个可下载地址才算有效更新
        boolean hasWgt = latest.getWgtUrl() != null && !latest.getWgtUrl().isBlank();
        boolean hasApk = latest.getApkUrl() != null && !latest.getApkUrl().isBlank();
        if (!hasWgt && !hasApk) {
            r.put("hasUpdate", false);
            return r;
        }
        r.put("hasUpdate", true);
        r.put("forceUpdate", latest.getForceUpdate() != null && latest.getForceUpdate() == 1);
        r.put("versionCode", latest.getVersionCode());
        r.put("versionName", latest.getVersionName());
        r.put("wgtUrl", latest.getWgtUrl());
        r.put("apkUrl", latest.getApkUrl());
        r.put("changelog", latest.getChangelog());
        return r;
    }

    @Override
    public void publish(AppVersionDTO dto) {
        assertAdmin();
        if (dto == null) {
            throw new BizException("参数不能为空");
        }
        if (dto.getVersionCode() == null || dto.getVersionCode() <= 0) {
            throw new BizException("版本号 versionCode 必填且大于 0");
        }
        boolean hasWgt = dto.getWgtUrl() != null && !dto.getWgtUrl().isBlank();
        boolean hasApk = dto.getApkUrl() != null && !dto.getApkUrl().isBlank();
        if (!hasWgt && !hasApk) {
            throw new BizException("请至少上传 wgt 热更新包或填写 APK 地址");
        }
        String platform = (dto.getPlatform() == null || dto.getPlatform().isBlank()) ? "android" : dto.getPlatform();

        // 以 wgt 包内部 manifest 的真实 versionCode 为准：手机装完后上报的就是这个号。
        // 若后台记录填得比它大，客户端会“装完还提示更新”陷入死循环；读不到才回退到表单值。
        Integer versionCode = dto.getVersionCode();
        if (hasWgt) {
            Integer wgtCode = readWgtVersionCode(dto.getWgtUrl());
            if (wgtCode != null) {
                if (!wgtCode.equals(versionCode)) {
                    log.info("[App版本] 表单 versionCode={} 与 wgt 内部 {} 不一致，已自动以 wgt 内部为准", versionCode, wgtCode);
                }
                versionCode = wgtCode;
            }
        }

        // 版本号必须大于当前最新，否则客户端按“取最大”比对时永远拉不到这条
        AppVersion exist = mapper.selectOne(new LambdaQueryWrapper<AppVersion>()
                .eq(AppVersion::getPlatform, platform)
                .orderByDesc(AppVersion::getVersionCode)
                .last("LIMIT 1"));
        if (exist != null && exist.getVersionCode() != null && versionCode <= exist.getVersionCode()) {
            throw new BizException("版本号必须大于当前最新的 " + exist.getVersionCode());
        }

        AppVersion v = new AppVersion();
        v.setPlatform(platform);
        v.setVersionCode(versionCode);
        v.setVersionName(dto.getVersionName());
        v.setWgtUrl(dto.getWgtUrl());
        v.setApkUrl(dto.getApkUrl());
        v.setForceUpdate(dto.getForceUpdate() != null && dto.getForceUpdate() == 1 ? 1 : 0);
        v.setChangelog(dto.getChangelog());
        v.setCreateTime(LocalDateTime.now());
        mapper.insert(v);

        audit(AdminAuditLog.ACTION_PUBLISH_APP_VERSION,
                "发布 App 新版本 " + platform + " " + (v.getVersionName() == null ? "" : v.getVersionName())
                + "（code=" + v.getVersionCode() + "）" + (v.getForceUpdate() == 1 ? "【强制更新】" : ""));
        log.info("[App版本] 发布 {} {} code={} wgt={} apk={}",
                platform, v.getVersionName(), v.getVersionCode(), v.getWgtUrl(), v.getApkUrl());
    }

    @Override
    public List<AppVersion> list(String platform) {
        assertAdmin();
        LambdaQueryWrapper<AppVersion> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(AppVersion::getPlatform, platform);
        }
        q.orderByDesc(AppVersion::getVersionCode).last("LIMIT 20");
        return mapper.selectList(q);
    }

    @Override
    public void delete(Long id) {
        assertAdmin();
        if (id == null) {
            throw new BizException("缺少版本 id");
        }
        AppVersion exist = mapper.selectById(id);
        if (exist == null) {
            throw new BizException("版本记录不存在或已被删除");
        }
        mapper.deleteById(id);
        audit(AdminAuditLog.ACTION_DELETE_APP_VERSION,
                "删除 App 版本 " + exist.getPlatform() + " "
                        + (exist.getVersionName() == null ? "" : exist.getVersionName())
                        + "（code=" + exist.getVersionCode() + "）");
        log.info("[App版本] 删除 id={} {} {} code={}",
                id, exist.getPlatform(), exist.getVersionName(), exist.getVersionCode());
    }

    @Override
    public Map<String, Object> latestDownload(String platform) {
        String pf = (platform == null || platform.isBlank()) ? "android" : platform;
        // 只取带 APK 地址的版本，且按 versionCode 倒序取最新一条：
        // 这样日常发 wgt 热更新（apkUrl 为空）不会把官网下载链接冲掉
        List<AppVersion> rows = mapper.selectList(new LambdaQueryWrapper<AppVersion>()
                .eq(AppVersion::getPlatform, pf)
                .isNotNull(AppVersion::getApkUrl)
                .ne(AppVersion::getApkUrl, "")
                .orderByDesc(AppVersion::getVersionCode)
                .last("LIMIT 1"));
        Map<String, Object> r = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            r.put("hasApk", false);
            return r;
        }
        AppVersion v = rows.get(0);
        r.put("hasApk", true);
        r.put("apkUrl", v.getApkUrl());
        r.put("versionCode", v.getVersionCode());
        r.put("versionName", v.getVersionName());
        r.put("changelog", v.getChangelog());
        return r;
    }

    /**
     * 读取 wgt 包内部 manifest.json 的 version.code——这才是手机安装后真正上报的 versionCode。
     * wgt 本质是 zip，manifest.json 在根目录；从 OSS 取流后解压读取。
     * 读不到（网络/格式异常）时返回 null，调用方回退到管理员手填的号。
     */
    private Integer readWgtVersionCode(String wgtUrl) {
        try (InputStream in = ossOperator.openStream(wgtUrl);
             ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    byte[] buf = zin.readAllBytes();
                    JsonNode root = wgtMapper.readTree(buf);
                    String code = root.path("version").path("code").asText("");
                    if (!code.isBlank()) {
                        return Integer.parseInt(code.trim());
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            log.warn("[App版本] 读取 wgt 内部版本号失败，回退到表单填写值：{}", e.getMessage());
        }
        return null;
    }

    /** 管理端统一防线：当前登录人必须是 admin（与 AdminServiceImpl 同一套约定） */
    private void assertAdmin() {
        if (!"admin".equals(FeignAuthContext.role())) {
            throw new BizException("无权访问，需要管理员账号");
        }
    }

    private void audit(String action, String detail) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            l.setOperatorId(FeignAuthContext.userId());
            l.setOperatorName(FeignAuthContext.username());
            l.setAction(action);
            l.setDetail(detail);
            l.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(l);
        } catch (Exception e) {
            log.error("[审计] App 版本操作写入失败：{}", e.getMessage());
        }
    }
}
