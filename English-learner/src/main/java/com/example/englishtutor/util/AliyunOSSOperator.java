package com.example.englishtutor.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.englishtutor.config.AliyunOSSProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 阿里云 OSS 操作器 — OSS 客户端以单例 Bean 存在，内部维护连接池，
 * 避免每次上传都重新建立 HTTPS 连接，显著降低网络波动导致的失败概率。
 */
@Slf4j
@Component
public class AliyunOSSOperator {

    private final AliyunOSSProperties props;
    private final OSS ossClient;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 2;

    public AliyunOSSOperator(AliyunOSSProperties props) {
        this.props = props;

        DefaultCredentialProvider credentialsProvider =
                new DefaultCredentialProvider(props.getAccessKeyId(), props.getAccessKeySecret());

        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setSignatureVersion(SignVersion.V4);
        // 连接建立超时 10s — 快速失败比长时间卡住好
        config.setConnectionTimeout(10000);
        // Socket 超时 300s — 大文件（APK 等）上传到 OSS 较慢，给足时间避免中途断开
        config.setSocketTimeout(300000);
        // 连接池大小，避免频繁创建连接
        config.setMaxConnections(20);

        this.ossClient = OSSClientBuilder.create()
                .endpoint(props.getEndpoint())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(config)
                .region(props.getRegion())
                .build();

        log.info("OSS 客户端初始化成功 — endpoint: {}, bucket: {}", props.getEndpoint(), props.getBucketName());
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS 客户端已关闭");
        }
    }

    /**
     * 上传文件到阿里云 OSS（带重试机制，流式上传避免内存溢出）
     * @param streamSupplier InputStream 提供者，每次重试时调用 get() 获取新流（因为流只能读一次）
     * @param contentLength 文件大小（字节）
     * @param originalFilename 原始文件名
     * @param contentType 文件 MIME 类型（如 image/jpeg）
     * @return 文件的完整访问 URL
     */
    public String upload(Supplier<InputStream> streamSupplier, long contentLength,
                         String originalFilename, String contentType) {
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // 用「目录/随机UUID/原始文件名」作为对象名：末段保留原始文件名，前端展示即为原名（如 时态.pdf）
        String uuid = UUID.randomUUID().toString();
        String safeName = safeFileName(originalFilename);
        String objectName = dir + "/" + uuid + "/" + safeName;

        // 设置 Content-Type，避免浏览器下载
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType != null ? contentType : "application/octet-stream");
        metadata.setContentLength(contentLength);

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            // 每次尝试获取新的 InputStream（前次尝试已消费）
            InputStream stream = streamSupplier.get();
            PutObjectRequest request = new PutObjectRequest(
                    props.getBucketName(), objectName, stream, metadata);
            try {
                ossClient.putObject(request);
                log.info("OSS上传成功: {} -> {} (大小: {}KB, 尝试: {})",
                        originalFilename, objectName, contentLength / 1024,
                        attempt > 0 ? "重试" + attempt : "首次");
                break; // 成功，退出重试循环
            } catch (Exception e) {
                lastException = e;
                // 关闭当前流（已消费或出错）
                try { stream.close(); } catch (Exception ignored) {}
                if (attempt < MAX_RETRIES) {
                    log.warn("OSS上传失败，准备重试 ({}): {} — {}",
                            attempt + 1, originalFilename, e.getMessage());
                } else {
                    log.error("OSS上传失败（已重试{}次）: {}", MAX_RETRIES, originalFilename, e);
                    throw new RuntimeException("文件上传失败（已重试" + MAX_RETRIES + "次）: " + e.getMessage(), e);
                }
            }
        }

        // URL 里对文件名做百分号编码（中文/空格等），前端 fileNameFromUrl 会 decode 回原名
        return "https://" + props.getBucketName() + "."
                + props.getEndpoint().replace("https://", "") + "/"
                + dir + "/" + uuid + "/" + urlEncodeSegment(safeName);
    }

    /** 规整上传文件名：去掉路径分隔与控制字符，保证可作为 OSS 对象名末段 */
    private String safeFileName(String name) {
        String n = name == null ? "" : name.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (n.isEmpty()) n = "file";
        if (n.length() > 120) n = n.substring(0, 120);
        return n;
    }

    /** 路径段百分号编码（空格用 %20，不用 +） */
    private String urlEncodeSegment(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 上传文件到阿里云 OSS（兼容旧调用，不推荐用于大文件）
     * @deprecated 使用 {@link #upload(Supplier, long, String, String)} 避免内存溢出
     */
    @Deprecated
    public String upload(byte[] content, String originalFilename, String contentType) {
        return upload(() -> new ByteArrayInputStream(content), content.length,
                originalFilename, contentType);
    }

    /**
     * 生成"浏览器直传"用的预签名 PUT URL：前端拿到后直接 PUT 文件到 OSS，
     * 绕过服务器中转（服务器上行带宽有限，APK 走服务端会慢/易超时）。
     * 返回 uploadUrl（预签名，30 分钟内有效）、fileUrl（最终访问地址）、objectName。
     * 注意：需在 OSS 控制台给该 Bucket 配置 CORS（允许来源本站域名、方法 PUT/GET/HEAD、允许头部 *）。
     */
    public Map<String, String> presignedPut(String originalFilename, String contentType) {
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String objectName = dir + "/" + UUID.randomUUID() + ext;
        Date expiration = new Date(System.currentTimeMillis() + 30L * 60 * 1000);
        URL url = ossClient.generatePresignedUrl(props.getBucketName(), objectName, expiration);
        String fileUrl = "https://" + props.getBucketName() + "."
                + props.getEndpoint().replace("https://", "") + "/" + objectName;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("uploadUrl", url.toString());
        m.put("fileUrl", fileUrl);
        m.put("objectName", objectName);
        m.put("contentType", contentType != null ? contentType : "application/octet-stream");
        return m;
    }

    /**
     * 从完整访问 URL 反解出 OSS objectName（去掉 https://{bucket}.{host}/ 前缀）。
     */
    private String extractKey(String fileUrl) {
        String host = props.getEndpoint().replaceFirst("^https?://", "");
        String prefix = "https://" + props.getBucketName() + "." + host + "/";
        String key;
        if (fileUrl.startsWith(prefix)) {
            key = fileUrl.substring(prefix.length());
        } else {
            // 兜底：截取域名后的路径部分（自定义域名/CDN 回源等场景）
            int scheme = fileUrl.indexOf("://");
            int slash = fileUrl.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
            key = slash >= 0 ? fileUrl.substring(slash + 1) : fileUrl;
        }
        // fileUrl 里的文件名做过百分号编码（中文/空格），OSS 取对象要用解码后的原始 key
        try {
            key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) { /* 解码失败按原样处理 */ }
        return key;
    }

    /**
     * 按访问 URL 打开 OSS 对象输入流。服务端用带签名的 SDK 访问，
     * 不受“OSS 默认公网域名禁止分发 .apk/.ipa”限制（那条限制只卡匿名浏览器直连）。
     * 调用方负责关闭返回的流。
     */
    public InputStream openStream(String fileUrl) {
        String key = extractKey(fileUrl);
        OSSObject obj = ossClient.getObject(props.getBucketName(), key);
        return obj.getObjectContent();
    }
}
