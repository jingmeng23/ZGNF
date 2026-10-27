package com.gas.ecommerce.modules.order.dto;
import jakarta.validation.constraints.*;
public record OrderCreateRequest(@NotNull @Positive Long addressId,
        @Size(max = 500) String remark,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId,
        @Positive Long userCouponId,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String orderNo,
        @Pattern(regexp = "[a-f0-9]{64}") String checkoutToken) {
    public OrderCreateRequest(Long addressId, String remark, String requestId, Long userCouponId, String orderNo) {
        this(addressId, remark, requestId, userCouponId, orderNo, null);
    }
    public OrderCreateRequest(Long addressId, String remark, String requestId, Long userCouponId) {
        this(addressId, remark, requestId, userCouponId, null, null);
    }
    public OrderCreateRequest(Long addressId, String remark, String requestId) {
        this(addressId, remark, requestId, null, null, null);
    }
    @AssertTrue(message = "必须提供预览订单号或兼容的 requestId")
    public boolean isIdempotencyKeyPresent() { return orderNo != null || requestId != null; }
    public String idempotencyKey() { return orderNo == null ? requestId : orderNo; }
}
