package com.gas.ecommerce.modules.order.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryRequest extends PageRequest {
    @Pattern(regexp = "pending_payment|paid|processing|completed|cancelled|refunded")
    private String status;
    @Size(max = 40) private String orderNo;
}
