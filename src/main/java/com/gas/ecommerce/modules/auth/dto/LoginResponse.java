package com.gas.ecommerce.modules.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn) { }
