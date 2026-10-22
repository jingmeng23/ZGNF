package com.gas.ecommerce.modules.order.dto;

import jakarta.validation.constraints.*;

public record OrderCreateRequest(@NotNull @Positive Long addressId,
        @Size(max = 500) String remark,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId, @Positive Long userCouponId) {
    public OrderCreateRequest(Long addressId, String remark, String requestId) { this(addressId, remark, requestId, null); }
}
