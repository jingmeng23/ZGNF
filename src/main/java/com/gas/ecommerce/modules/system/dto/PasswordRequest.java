package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.*;

public record PasswordRequest(@NotBlank @Size(min=12,max=72) String password) { }
