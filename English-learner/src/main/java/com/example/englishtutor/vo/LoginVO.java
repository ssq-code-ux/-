package com.example.englishtutor.vo;
import lombok.Data;

@Data
public class LoginVO {
    private String token;           // Access Token（30min）
    private String refreshToken;    // Refresh Token（7d）
    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long teacherId;
    private String teacherName;
    private String teacherPhone;
}
