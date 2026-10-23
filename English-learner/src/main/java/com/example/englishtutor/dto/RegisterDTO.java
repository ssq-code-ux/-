package com.example.englishtutor.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String username;
    private String password;
    private String realName;
    private String role;
    private Long teacherId;
    private String phone;
    /** 家长注册时填孩子的学生账号，注册成功后自动绑定 */
    private String childUsername;
    /** 邀请码（选填）：填了就与邀请人建立关系，管理端可统计每位老师邀请了几人 */
    private String inviteCode;
}
