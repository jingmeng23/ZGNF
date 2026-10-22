package com.gas.ecommerce.modules.auth.dto;

public record AdminLoginResponse(String accessToken, long expiresIn, AdminProfile admin) { }
