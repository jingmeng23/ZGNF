package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(@NotBlank @Size(max=100) String name,
        @NotNull @DecimalMin(value="0.01") @Digits(integer=8, fraction=2) BigDecimal amount,
        @NotNull @DecimalMin(value="0.00") @Digits(integer=8, fraction=2) BigDecimal minimumAmount,
        @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
        @NotNull @Min(0) Integer quantity, @Pattern(regexp="active|inactive") String status) { }
