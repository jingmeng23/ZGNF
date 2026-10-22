package com.gas.ecommerce.modules.order.vo;

public record OrderAddressVO(String receiver, String phone, String province,
        String city, String district, String detail) { }
