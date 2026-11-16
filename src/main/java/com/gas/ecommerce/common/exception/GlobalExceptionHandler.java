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
/**
 * 异常或错误码类型，用于统一表达程序处理失败的原因。
 */
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> uploadTooLarge(Exception ex) {
        return Result.of(ErrorCode.PARAMETER_INVALID, "单张图片不能超过 10MB", null);
    }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> missingUpload(Exception ex) {
        return Result.of(ErrorCode.PARAMETER_INVALID, "请选择要上传的商品图片", null);
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> notFound(Exception ex) { return Result.of(404, "接口不存在", null); }
    @ExceptionHandler(com.gas.ecommerce.modules.user.service.AddressDuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> duplicateAddress(com.gas.ecommerce.modules.user.service.AddressDuplicateException ex) {
        return Result.of(409002,ex.getMessage(),null);
    }
    @ExceptionHandler(com.gas.ecommerce.modules.order.service.CheckoutUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> checkoutUnavailable(com.gas.ecommerce.modules.order.service.CheckoutUnavailableException ex) {
        return Result.of(503001,ex.getMessage(),null);
    }
    @ExceptionHandler(com.gas.ecommerce.modules.order.service.CheckoutConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> checkoutConflict(com.gas.ecommerce.modules.order.service.CheckoutConflictException ex) {
        return Result.of(ex.getCode(),ex.getMessage(),null);
    }
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> credentials(Exception ex) { return Result.of(ErrorCode.UNAUTHENTICATED, "账号、密码或管理员授权无效", null); }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidInput(Exception ex) { return Result.of(ErrorCode.PARAMETER_INVALID, "请求参数格式错误", null); }
    /** 记录完整异常供服务器排查；返回客户端的消息不会包含堆栈信息。 */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务主动拒绝当前操作时，返回异常携带的业务码和说明。 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> business(BusinessException ex) { return Result.of(ex.getCode(), ex.getMessage(), null); }

    /** @Valid 校验失败时，把每个参数字段及其首条错误说明放进响应 data。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                e -> e.getField(), e -> e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage(), (a, b) -> a));
        return Result.of(ErrorCode.PARAMETER_INVALID, "参数校验失败", errors);
    }

    /** 用户身份有效但权限不足时，返回禁止访问的响应。 */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> denied(AccessDeniedException ex) { return Result.of(ErrorCode.ACCESS_DENIED, "无权访问", null); }

    /** 兜底处理未预期异常：详细堆栈只写服务端日志，客户端只看到通用提示。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Request failed: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return Result.of(ErrorCode.INTERNAL_ERROR, "服务器内部错误", null);
    }
}
