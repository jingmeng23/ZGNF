package com.gas.ecommerce.common.exception;
/**
 * 异常或错误码类型，用于统一表达程序处理失败的原因。
 */

public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public BusinessException(String message) { this(400001, message); }
    public int getCode() { return code; }
}
