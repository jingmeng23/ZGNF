package com.gas.ecommerce.modules.auth.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.auth.dto.*;
import com.gas.ecommerce.modules.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService auth;
    public AdminAuthController(AdminAuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(auth.login(request));
    }

    @GetMapping("/me")
    public Result<AdminProfile> me() { return Result.success(auth.profile(SecurityUtils.currentUserId())); }
}
