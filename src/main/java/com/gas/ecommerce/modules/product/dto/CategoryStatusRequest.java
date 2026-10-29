package com.gas.ecommerce.modules.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoryStatusRequest(@NotBlank @Pattern(regexp = "active|inactive") String status) { }
