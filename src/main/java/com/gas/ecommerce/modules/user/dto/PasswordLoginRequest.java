package com.gas.ecommerce.modules.user.dto;
import jakarta.validation.constraints.*;
public record PasswordLoginRequest(@NotBlank @Pattern(regexp="1[3-9]\\d{9}", message="手机号格式不正确") String account,@NotBlank @Size(min=8,max=64) String password) {}
