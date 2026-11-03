package com.gas.ecommerce.modules.user.dto;
import jakarta.validation.constraints.*;
public record PasswordRequest(@NotBlank String currentPassword,@NotBlank @Size(min=8,max=64) String newPassword) {}
