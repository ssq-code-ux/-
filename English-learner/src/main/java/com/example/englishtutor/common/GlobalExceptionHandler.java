package com.example.englishtutor.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("参数错误");
        log.warn("参数校验失败: {}", msg);
        return R.fail(R.ErrorCode.BAD_REQUEST.getMessage() + " - " + msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handle(Exception e) {
        log.error("未处理异常 — 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
        return R.fail(e.getMessage() != null ? e.getMessage() : "服务器异常");
    }

    /** 上传文件大小超限 */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMaxSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        long max = e.getMaxUploadSize();
        // max <= 0 时表示异常未携带限制值（部分容器/配置下出现），避免展示误导性的“最大 0KB”
        String limit = max <= 0 ? "服务端配置上限" : (max >= 1024 * 1024 ? (max / 1024 / 1024) + "MB" : (max / 1024) + "KB");
        log.warn("上传文件超限 — 最大允许: {}, 实际: 超过限制", limit);
        return R.fail("文件大小超过限制（最大" + limit + "），请压缩后重试");
    }

    /** 上传请求异常（如 multipart 解析失败） */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMultipart(org.springframework.web.multipart.MultipartException e) {
        log.error("Multipart 解析异常 — 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
        return R.fail("文件上传异常: " + (e.getMessage() != null ? e.getMessage() : "请重试"));
    }
}
