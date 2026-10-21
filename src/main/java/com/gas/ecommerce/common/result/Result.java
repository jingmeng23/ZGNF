package com.gas.ecommerce.common.result;

import com.gas.ecommerce.common.interceptor.TraceIdInterceptor;
import java.time.Instant;

public record Result<T>(int code, String message, T data, long timestamp, String traceId) {
    public static <T> Result<T> success(T data) { return of(200, "success", data); }
    public static Result<Void> success() { return success(null); }
    public static <T> Result<T> of(int code, String message, T data) {
        return new Result<>(code, message, data, Instant.now().toEpochMilli(), TraceIdInterceptor.currentTraceId());
    }
}
