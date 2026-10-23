package com.example.englishtutor.service;

import com.example.englishtutor.entity.FileResource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {
    FileResource upload(MultipartFile file);

    /** 生成浏览器直传 OSS 的预签名 PUT URL（大文件上传用） */
    Map<String, String> presign(String filename, String contentType);
}
