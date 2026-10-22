package com.gas.ecommerce.common.result;
/**
 * 统一响应和分页数据类型，约定接口返回的数据结构。
 */

public enum ResultCode {
    SUCCESS(200, "success"), PARAMETER_INVALID(400001, "参数错误"),
    UNAUTHENTICATED(401001, "请先登录"), ACCESS_DENIED(403001, "无权访问"),
    NOT_FOUND(404001, "资源不存在"), INTERNAL_ERROR(500001, "服务器内部错误");
    private final int code;
    private final String message;
    ResultCode(int code, String message) { this.code = code; this.message = message; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
