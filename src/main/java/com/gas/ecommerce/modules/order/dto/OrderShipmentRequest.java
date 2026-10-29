package com.gas.ecommerce.modules.order.dto;

import jakarta.validation.constraints.*;

public record OrderShipmentRequest(@NotBlank @Size(max=80) String carrier,
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{2,99}") String trackingNo) { }
