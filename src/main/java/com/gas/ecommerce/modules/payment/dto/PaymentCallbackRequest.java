package com.gas.ecommerce.modules.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCallbackRequest(
        @NotBlank @Size(max = 40) String paymentNo,
        @NotBlank @Size(max = 100) String transactionNo,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "success|failed") String status,
        @NotNull LocalDateTime eventTime) { }
