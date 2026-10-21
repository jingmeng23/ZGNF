package com.gas.ecommerce.modules.payment.dto;
public record PaymentCreateRequest(Long orderId, String channel) { }
