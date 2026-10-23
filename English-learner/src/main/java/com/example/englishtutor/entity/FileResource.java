package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("file_resource") public class FileResource{ @TableId(type=IdType.AUTO) private Long id; private Long userId; private String originName; private String fileName; private String fileUrl; private String contentType; private Long size; private LocalDateTime createTime; }
