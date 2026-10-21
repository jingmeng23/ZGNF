package com.gas.ecommerce.common.exception;

public interface ErrorCode {
    int PARAMETER_INVALID = 400001;
    int UNAUTHENTICATED = 401001;
    int ACCESS_DENIED = 403001;
    int INTERNAL_ERROR = 500001;
}
