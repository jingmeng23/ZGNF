package com.gas.ecommerce.modules.auth.service;

import com.gas.ecommerce.modules.auth.dto.LoginRequest;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;

public interface AuthService {
    void sendSmsCode(String phone);
    LoginResponse login(LoginRequest request);
}
