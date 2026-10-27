package com.gas.ecommerce.modules.order.service;

/** Integration port. Releases MUST be idempotent by orderNo and fence any delayed reservation. */
public interface CheckoutPointsGateway {
    void reserve(String orderNo, Long userId);
    void release(String orderNo);
}
