package com.example.englishtutor.config;

import com.example.englishtutor.common.AuthContext;
import com.example.englishtutor.common.LoginUser;
import com.example.englishtutor.infra.redis.TokenService;
import com.example.englishtutor.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws IOException {
        // 1. 放行 OPTIONS 跨域预检
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        // 2. 放行登录、注册、刷新、登出接口（注意：上传接口 /api/files 需要认证，因为要记录 userId）
        //    /api/app/** 也放行：客户端冷启动、甚至还没登录就要能检查 App 版本更新
        String uri = req.getRequestURI();
        if (uri.startsWith("/api/auth") || uri.startsWith("/api/app") || uri.startsWith("/uploads")) {
            return true;
        }

        // 3. 校验 JWT Token
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(res, "请先登录");
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);

            // Refresh Token 只能调 /api/auth/refresh，不能访问业务接口
            if ("refresh".equals(claims.get("type"))) {
                return unauthorized(res, "请使用 Access Token");
            }

            // 登出黑名单：已登出的 Access Token 立即失效
            if (tokenService.isBlacklisted(token)) {
                return unauthorized(res, "登录已失效，请重新登录");
            }

            LoginUser u = new LoginUser();
            u.setId(Long.valueOf(claims.getSubject()));
            u.setUsername(claims.get("username", String.class));
            u.setRole(claims.get("role", String.class));
            AuthContext.set(u);
        } catch (ExpiredJwtException e) {
            return unauthorized(res, "Token 已过期");
        } catch (JwtException e) {
            return unauthorized(res, "Token 无效");
        }
        return true;
    }

    /**
     * 鉴权失败：HTTP 200 + code=1002（与微服务网关返回格式一致），
     * 前端 request 拦截器据此自动用 Refresh Token 续期并重放原请求
     */
    private boolean unauthorized(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":1002,\"message\":\"" + msg + "\",\"data\":null}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object h, Exception ex) {
        AuthContext.clear();
    }
}
