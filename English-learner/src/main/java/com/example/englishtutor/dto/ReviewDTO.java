package com.example.englishtutor.dto;

import lombok.Data;

@Data
public class ReviewDTO {
    private Long submitId;
    private Integer score;
    private String comment;
    /** 语音评语地址（前端录音后先调 /api/files/upload 拿到 URL） */
    private String voiceUrl;
    private String errorTypes;
    private String needRedo;
}
