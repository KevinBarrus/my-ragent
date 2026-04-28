package com.tkevinjd.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.tkevinjd.framework.convention.Result;
import com.tkevinjd.framework.errorMessage.BaseErrorCode;
import com.tkevinjd.framework.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private String maxRequestSize;

    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = AbstractException.class)
    public Result<Void> abstractException(HttpServletRequest request, AbstractException aex) {
        if(aex.getCause() != null) {
            //最后一个参数是异常信息，不会填入到占位符当中，自动打印堆栈
            log.error("[{}] {} [ex] {}",request.getMethod(),getUrl(request), aex, aex.getCause());
        } else {
            StringBuilder stackTraceBuilder = new StringBuilder();
            stackTraceBuilder.append(aex.getClass().getName()).append(": ").append(aex.getErrorMessage()).append("\n");
            StackTraceElement[] stackTrace = aex.getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
            }
            log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), getUrl(request), aex, stackTraceBuilder);
        }
        return Results.failure(aex);
    }

    /**
     * 拦截参数验证异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        //获取校验结果
        BindingResult bindingResult = ex.getBindingResult();

        //获取第一个校验失败的字段
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());

        //提取错误信息，如果有
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);

        //记录日志
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);

        //返回给前端
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截未登录异常
     */
    @ExceptionHandler(value = NotLoginException.class)
    public Result<Void> notLoginException(HttpServletRequest request, NotLoginException ex) {
        log.warn("[{}] {} [auth] not-login: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "未登录或登录已过期");
    }

    /**
     * 拦截无角色权限异常
     */
    @ExceptionHandler(value = NotRoleException.class)
    public Result<Void> notRoleException(HttpServletRequest request, NotRoleException ex) {
        log.warn("[{}] {} [auth] no-role: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "权限不足");
    }

    /**
     * 拦截文件上传大小超限异常
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result<Void> maxUploadSizeExceededException(HttpServletRequest request, MaxUploadSizeExceededException ex) {

        String message;

        Throwable cause = ex.getCause();
        Throwable rootCause = cause != null ? cause.getCause() : null;

        if (cause instanceof IllegalStateException
                && rootCause instanceof FileSizeLimitExceededException) {

            // 单个文件太大 -> 用户知道要压缩文件
            message = "上传文件大小超过限制，单个文件最大允许 " + maxFileSize;

        } else {

            // 整个请求太大或其他原因 -> 用户知道要减少上传的文件
            message = "上传请求大小超过限制，单次请求最大允许 " + maxRequestSize;
        }

        log.warn("[{}] {} [upload] 文件上传大小超限: {}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }


    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
