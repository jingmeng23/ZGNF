package com.gas.ecommerce.modules.user.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    @GetMapping("/me") public Result<Map<String, Object>> me() { return Result.success(Map.of("userId", SecurityUtils.currentUserId())); }
}
