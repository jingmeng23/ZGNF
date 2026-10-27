package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.exception.BusinessException;

public class CheckoutConflictException extends BusinessException {
    public CheckoutConflictException(String message) { super(409001,message); }
}
