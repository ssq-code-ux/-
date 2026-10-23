package com.example.englishtutor.common;

/**
 * 用户上下文适配层（单体版）。
 * <p>
 * 微服务版里业务代码统一通过 FeignAuthContext 读取网关注入的
 * X-User-Id / X-Username / X-Role 请求头；单体版没有网关，用户身份由
 * {@link com.example.englishtutor.config.AuthInterceptor} 解析 JWT 后放进
 * ThreadLocal 的 {@link AuthContext}。
 * <p>
 * 这里保持与微服务版完全相同的类名与方法签名，内部委托给 AuthContext，
 * 使移植过来的家教业务代码（TutorJob/Admin/ParentTutor 等 Service）
 * 不必逐处改写“取当前登录用户”的方式，两边代码可继续对齐维护。
 */
public class FeignAuthContext {

    public static Long userId() {
        return AuthContext.userId();
    }

    public static String username() {
        LoginUser u = AuthContext.get();
        return u == null ? null : u.getUsername();
    }

    public static String role() {
        return AuthContext.role();
    }
}
