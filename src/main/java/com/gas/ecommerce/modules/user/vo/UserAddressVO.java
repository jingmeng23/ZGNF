package com.gas.ecommerce.modules.user.vo;

public record UserAddressVO(Long id, String receiver, String phone, String province,
        String city, String district, String detail, boolean isDefault) { }
