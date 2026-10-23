package com.example.englishtutor.common;

public class AuthContext {
    private static final ThreadLocal<LoginUser> TL = new ThreadLocal<>();

    public static void set(LoginUser u) {
        TL.set(u);
    }

    public static LoginUser get() {
        return TL.get();
    }

    public static Long userId() {
        return TL.get() == null ? null : TL.get().getId();
    }

    public static String role() {
        return TL.get() == null ? null : TL.get().getRole();
    }

    public static void clear() {
        TL.remove();
    }
}
