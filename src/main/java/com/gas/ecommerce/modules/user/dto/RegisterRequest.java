package com.gas.ecommerce.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确") String phone,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 64) String nickname) { }
