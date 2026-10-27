package com.gas.ecommerce.modules.order.service;

import org.springframework.stereotype.Component;

/** No points redemption exists in this repository. The checkout API accepts no points amount. */
@Component
public class NoPointsCheckoutGateway implements CheckoutPointsGateway {
    public void reserve(String orderNo, Long userId) { }
    public void release(String orderNo) { }
}
