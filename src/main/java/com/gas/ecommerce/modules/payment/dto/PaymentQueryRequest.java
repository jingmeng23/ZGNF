package com.gas.ecommerce.modules.payment.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class PaymentQueryRequest extends PageRequest {
    @Pattern(regexp = "pending|success|failed|refunded") private String status;
    @Positive private Long orderId;
}
