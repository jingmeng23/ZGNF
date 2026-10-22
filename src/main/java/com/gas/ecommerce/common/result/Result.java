package com.gas.ecommerce.common.result;

import com.gas.ecommerce.common.interceptor.TraceIdInterceptor;
import java.time.Instant;
/**
 * 统一响应和分页数据类型，约定接口返回的数据结构。
 */

public record Result<T>(int code, String message, T data, long timestamp, String traceId) {
    /** 创建成功响应，并把调用方提供的业务结果放入 data。 */
    public static <T> Result<T> success(T data) { return of(200, "success", data); }
    /** 创建不带业务数据的成功响应。 */
    public static Result<Void> success() { return success(null); }
    /** 创建指定业务码和说明的响应，同时记录当前时间和请求 traceId。 */
    public static <T> Result<T> of(int code, String message, T data) {
        return new Result<>(code, message, data, Instant.now().toEpochMilli(), TraceIdInterceptor.currentTraceId());
    }
}
