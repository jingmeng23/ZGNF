package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.*;

public record RoleRequest(@NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_]{1,63}") String roleKey,
        @NotNull @Pattern(regexp="active|inactive") String status) { }
