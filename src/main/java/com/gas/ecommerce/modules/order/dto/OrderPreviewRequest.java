package com.gas.ecommerce.modules.order.dto;
import jakarta.validation.constraints.*;
public record OrderPreviewRequest(@NotNull @Positive Long addressId, @Positive Long userCouponId) { }
