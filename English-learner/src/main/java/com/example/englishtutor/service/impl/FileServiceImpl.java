package com.example.englishtutor.service.impl;
import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.BizException;
import com.example.englishtutor.entity.FileResource;
import com.example.englishtutor.mapper.FileResourceMapper;
import com.example.englishtutor.service.FileService;
import com.example.englishtutor.util.AliyunOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j @Service @RequiredArgsConstructor
public class FileServiceImpl implements FileService{
    private final FileResourceMapper mapper;
    private final AliyunOSSOperator ossOperator;

    public FileResource upload(MultipartFile file){
        // 基础校验
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        long sizeMB = file.getSize() / (1024 * 1024);
        log.info("开始上传: {} ({}MB), 类型: {}", originalName, sizeMB, file.getContentType());

        String fileUrl;
        try {
            // 使用 Supplier<InputStream> 进行流式上传，避免大文件全量加载到内存
            // 每次重试时会重新调用 file.getInputStream() 获取新流
            long fileSize = file.getSize();
            fileUrl = ossOperator.upload(
                    () -> {
                        try { return file.getInputStream(); }
                        catch (IOException e) { throw new RuntimeException("无法读取文件流", e); }
                    },
                    fileSize, originalName, file.getContentType());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            log.error("上传到OSS失败: {} — {}", originalName, msg);
            // 区分超时错误和连接错误，给出更友好的提示
            if (msg.contains("timeout") || msg.contains("Timeout") || msg.contains("timed out")) {
                throw new BizException("上传超时，文件过大或网络不稳定，请稍后重试");
            }
            if (msg.contains("connect") || msg.contains("Connect") || msg.contains("UnknownHost")) {
                throw new BizException("无法连接到文件存储服务，请检查网络后重试");
            }
            throw new BizException("文件上传失败: " + msg);
        }

        FileResource r = new FileResource();
        r.setUserId(AuthContext.userId());
        r.setOriginName(originalName);
        r.setFileName(UUID.randomUUID() + "_" + originalName);
        r.setFileUrl(fileUrl);
        r.setContentType(file.getContentType());
        r.setSize(file.getSize());
        r.setCreateTime(LocalDateTime.now());
        mapper.insert(r);
        log.info("上传成功: {} -> {}", originalName, fileUrl);
        return r;
    }

    @Override
    public Map<String, String> presign(String filename, String contentType) {
        if (filename == null || filename.isBlank()) {
            throw new BizException("缺少文件名");
        }
        if (AuthContext.userId() == null) {
            throw new BizException("请先登录");
        }
        log.info("生成直传预签名URL: {} ({})", filename, contentType);
        return ossOperator.presignedPut(filename, contentType);
    }
}
