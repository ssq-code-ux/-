package com.example.englishtutor.common;
import lombok.Data;

@Data
public class R<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String msg) {
        R<T> r = new R<>();
        r.code = ErrorCode.BIZ_ERROR.getCode();
        r.message = msg;
        return r;
    }

    public static <T> R<T> fail(ErrorCode code) {
        R<T> r = new R<>();
        r.code = code.getCode();
        r.message = code.getMessage();
        return r;
    }

    public enum ErrorCode {
        SUCCESS(200, "success"),
        BAD_REQUEST(1001, "请求参数错误"),
        UNAUTHORIZED(1002, "请先登录"),
        TOKEN_EXPIRED(1003, "登录已过期"),
        FORBIDDEN(1004, "权限不足"),
        NOT_FOUND(1005, "资源不存在"),
        BIZ_ERROR(2001, "业务异常");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
    }
}
