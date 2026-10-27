package com.gas.ecommerce.modules.user.service;
import com.gas.ecommerce.common.exception.BusinessException;
public class AddressDuplicateException extends BusinessException {
    public AddressDuplicateException(){super("已存在相同收货地址，是否仍然保存？");}
}
