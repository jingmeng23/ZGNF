package com.gas.ecommerce.modules.auth.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.auth.dto.LoginRequest;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
import com.gas.ecommerce.modules.auth.dto.SmsCodeRequest;
import com.gas.ecommerce.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
/**
 * HTTP 接口控制器，接收请求参数、调用业务服务并返回统一响应。
 */
public class AuthController {
    private final AuthService authService;
    /** 构造方法注入认证服务；控制器本身不直接操作 Redis 或数据库。 */
    public AuthController(AuthService authService) { this.authService = authService; }
    /** 接收手机号并提交验证码发送任务，成功时返回统一格式的空 data 响应。 */
    @PostMapping("/sms-code") public Result<Void> smsCode(@Valid @RequestBody SmsCodeRequest request) { authService.sendSmsCode(request.phone()); return Result.success(); }
    /** 接收手机号和验证码，成功后返回 access token 和 refresh token。 */
    @PostMapping("/login") public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) { return Result.success(authService.login(request)); }
}
