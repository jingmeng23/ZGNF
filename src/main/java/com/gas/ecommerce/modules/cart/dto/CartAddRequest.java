package com.gas.ecommerce.modules.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(@NotNull @Min(1) Long productId,
        @NotNull @Min(1) @Max(999) Integer quantity) { }
