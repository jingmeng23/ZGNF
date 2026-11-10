package com.gas.ecommerce.modules.payment.wechat;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WechatIdentityController {
    public record Code(@NotBlank @Size(max=256) String code) {}
    private final WechatIdentityService identities;
    public WechatIdentityController(WechatIdentityService identities) { this.identities=identities; }
    @PostMapping("/auth/wechat/login")
    public Result<LoginResponse> login(@Valid @RequestBody Code request) { return Result.success(identities.login(request.code())); }
    @PostMapping("/users/me/wechat-binding")
    public Result<Void> bind(@Valid @RequestBody Code request) { identities.bind(SecurityUtils.currentUserId(),request.code()); return Result.success(); }
}
