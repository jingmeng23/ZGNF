package com.gas.ecommerce.common.exception;

import com.gas.ecommerce.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> business(BusinessException ex) { return Result.of(ex.getCode(), ex.getMessage(), null); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                e -> e.getField(), e -> e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage(), (a, b) -> a));
        return Result.of(ErrorCode.PARAMETER_INVALID, "参数校验失败", errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> denied(AccessDeniedException ex) { return Result.of(ErrorCode.ACCESS_DENIED, "无权访问", null); }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Request failed: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return Result.of(ErrorCode.INTERNAL_ERROR, "服务器内部错误", null);
    }
}
