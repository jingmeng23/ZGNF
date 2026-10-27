package com.gas.ecommerce.modules.order.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class OrderCursorRequest {
    @Pattern(regexp="pending_payment|paid|processing|completed|cancelled|refunded") private String status;
    @Min(1) @Max(100) private int size=20;
    @Positive private Long afterId;
}
