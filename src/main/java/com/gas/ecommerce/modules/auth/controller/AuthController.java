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
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }
    @PostMapping("/sms-code") public Result<Void> smsCode(@Valid @RequestBody SmsCodeRequest request) { authService.sendSmsCode(request.phone()); return Result.success(); }
    @PostMapping("/login") public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) { return Result.success(authService.login(request)); }
}
