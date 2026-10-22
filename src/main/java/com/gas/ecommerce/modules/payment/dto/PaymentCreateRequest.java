package com.gas.ecommerce.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentCreateRequest(@NotNull @Positive Long orderId,
        @NotBlank @Size(max = 30) String channel) { }
