package com.gas.ecommerce.modules.user.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
/**
 * 用户相关 HTTP 接口。目前 /me 只返回当前用户 ID，完整资料查询还未实现。
 */
public class UserController {
    /** 用户 ID 来自 JWT 过滤器放入 Spring Security 的当前登录上下文。 */
    // The profile endpoint is provided by UserManagementController.
}
