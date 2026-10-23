package com.example.englishtutor.controller;

import com.example.englishtutor.common.R;
import com.example.englishtutor.dto.*;
import com.example.englishtutor.service.AuthService;
import com.example.englishtutor.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;

    /** 登录 —— 返回双 Token */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody LoginDTO d) {
        return R.ok(service.login(d));
    }

    /** 注册 */
    @PostMapping("/register")
    public R<Void> register(@RequestBody RegisterDTO d) {
        service.register(d);
        return R.ok(null);
    }

    /** 刷新 Token —— Access 过期后用 Refresh Token 换新的双 Token */
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return R.fail("refreshToken 不能为空");
        }
        return R.ok(service.refreshToken(refreshToken));
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth,
                       @RequestBody(required = false) Map<String, String> body) {
        String accessToken = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            accessToken = auth.substring(7);
        }
        String refreshToken = body != null ? body.get("refreshToken") : null;
        service.logout(accessToken, refreshToken);
        return R.ok(null);
    }
}
