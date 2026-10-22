package com.gas.ecommerce.modules.auth.dto;

import jakarta.validation.constraints.*;

public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 72) String password) { }
