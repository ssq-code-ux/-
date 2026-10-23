package com.example.englishtutor.service;

import com.example.englishtutor.dto.AppVersionDTO;
import com.example.englishtutor.entity.AppVersion;

import java.util.List;
import java.util.Map;

public interface AppVersionService {

    /** 客户端启动检查更新：返回 { hasUpdate, forceUpdate, versionCode, versionName, wgtUrl, apkUrl, changelog } */
    Map<String, Object> checkUpdate(String platform, Integer currentCode);

    /** 管理员发布新版本（versionCode 必须递增；wgt/apk 至少一个） */
    void publish(AppVersionDTO dto);

    /** 管理员查看历史版本（最近 20 条） */
    List<AppVersion> list(String platform);

    /** 管理员删除一条版本记录（按主键 id） */
    void delete(Long id);

    /** 公开：取最新一条“带 APK 下载地址”的版本，供官网首页挂安装包下载链接（wgt 热更新不影响它） */
    Map<String, Object> latestDownload(String platform);
}
