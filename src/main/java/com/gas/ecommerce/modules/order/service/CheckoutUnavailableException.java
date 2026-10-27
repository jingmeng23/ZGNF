package com.gas.ecommerce.modules.order.service;

public class CheckoutUnavailableException extends RuntimeException {
    public CheckoutUnavailableException(String dependency, Throwable cause) {
        super(dependency+"暂不可用，请稍后重新结算",cause);
    }
}
