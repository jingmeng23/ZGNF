package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.*;

public record StoreRequest(
        @NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="[0-9+() -]{5,30}") String phone,
        @NotBlank @Size(max=64) String province,
        @NotBlank @Size(max=64) String city,
        @NotBlank @Size(max=64) String district,
        @NotBlank @Size(max=255) String address,
        @Size(max=100) String manager,
        @NotNull @Pattern(regexp="active|inactive") String status) { }
