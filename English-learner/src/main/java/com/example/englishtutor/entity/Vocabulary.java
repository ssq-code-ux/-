package com.example.englishtutor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("vocabulary")
public class Vocabulary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    /** 读音（音标，如 /ˈæpl/） */
    private String phonetic;
    private String translation;
    private String partOfSpeech;
    private String extensions;
    /** 所属词库：学生词库 / 四级词库 / 六级词库 */
    private String bank;
    private Long teacherId;
    private LocalDateTime createTime;
}
