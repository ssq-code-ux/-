package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.AppVersionDTO;
import com.example.englishtutor.entity.AppVersion;
import com.example.englishtutor.service.AppVersionService;
import com.example.englishtutor.util.AliyunOSSOperator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * App 版本 / wgt 热更新接口。
 * <p>
 * GET  /api/app/version        —— 客户端启动检查更新（免登录，见 AuthInterceptor 白名单）
 * POST /api/admin/app/version  —— 管理员发布新版本（Service 层 assertAdmin）
 * GET  /api/admin/app/versions —— 管理员查看历史版本
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionService appVersionService;
    private final AliyunOSSOperator ossOperator;

    /** 客户端检查更新：platform=android，current=当前 versionCode */
    @GetMapping("/app/version")
    public R<Map<String, Object>> check(@RequestParam(required = false) String platform,
                                        @RequestParam(required = false) Integer current) {
        return R.ok(appVersionService.checkUpdate(platform, current));
    }

    /** 公开：官网首页获取最新安装包（APK）下载信息，免登录（/api/app/** 已在白名单） */
    @GetMapping("/app/download")
    public R<Map<String, Object>> download(@RequestParam(required = false) String platform) {
        return R.ok(appVersionService.latestDownload(platform));
    }

    /**
     * 公开：直接下载最新 APK。服务端从 OSS 取流再转发给浏览器，
     * 让下载走本站域名而不是 OSS 默认公网域名，避开阿里云 ApkDownloadForbidden（禁止公网分发 .apk）。
     */
    @GetMapping("/app/apk")
    public void downloadApk(@RequestParam(required = false) String platform,
                            HttpServletResponse response) throws IOException {
        Map<String, Object> info = appVersionService.latestDownload(platform);
        if (!Boolean.TRUE.equals(info.get("hasApk"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "暂无可下载的安装包");
            return;
        }
        String apkUrl = String.valueOf(info.get("apkUrl"));
        Object vn = info.get("versionName");
        String fname = "englishtutor-" + (vn == null || String.valueOf(vn).isBlank() ? "latest" : String.valueOf(vn)) + ".apk";
        response.reset();
        response.setContentType("application/vnd.android.package-archive");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
        try (InputStream in = ossOperator.openStream(apkUrl); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }

    /** 管理员发布新版本 */
    @PostMapping("/admin/app/version")
    public R<Void> publish(@RequestBody AppVersionDTO dto) {
        appVersionService.publish(dto);
        return R.ok(null);
    }

    /** 管理员查看历史版本 */
    @GetMapping("/admin/app/versions")
    public R<List<AppVersion>> versions(@RequestParam(required = false) String platform) {
        return R.ok(appVersionService.list(platform));
    }

    /** 管理员删除一条版本记录 */
    @DeleteMapping("/admin/app/version/{id}")
    public R<Void> delete(@PathVariable Long id) {
        appVersionService.delete(id);
        return R.ok(null);
    }
}
