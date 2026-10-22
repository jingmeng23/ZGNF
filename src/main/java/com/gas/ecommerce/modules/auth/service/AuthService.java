package com.gas.ecommerce.modules.auth.service;

import com.gas.ecommerce.modules.auth.dto.LoginRequest;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */

public interface AuthService {
    void sendSmsCode(String phone);
    LoginResponse login(LoginRequest request);
}
