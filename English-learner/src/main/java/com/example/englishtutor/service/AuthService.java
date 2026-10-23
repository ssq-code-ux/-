package com.example.englishtutor.service;

import com.example.englishtutor.dto.*;
import com.example.englishtutor.vo.LoginVO;

public interface AuthService {
    LoginVO login(LoginDTO dto);

    void register(RegisterDTO dto);

    /** 本人修改密码（校验原密码；教师/学生/家长/管理员通用） */
    void changePassword(String oldPassword, String newPassword);

    /** 刷新 Token：用 Refresh Token 换取新的双 Token */
    LoginVO refreshToken(String refreshToken);

    /** 退出登录：Access Token 加黑名单 + Refresh Token 删除 */
    void logout(String accessToken, String refreshToken);
}
