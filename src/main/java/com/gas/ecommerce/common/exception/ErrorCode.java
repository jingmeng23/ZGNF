package com.gas.ecommerce.common.exception;
/**
 * 异常或错误码类型，用于统一表达程序处理失败的原因。
 */

public interface ErrorCode {
    int PARAMETER_INVALID = 400001;
    int UNAUTHENTICATED = 401001;
    int ACCESS_DENIED = 403001;
    int INTERNAL_ERROR = 500001;
}
