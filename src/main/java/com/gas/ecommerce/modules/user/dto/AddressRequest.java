package com.gas.ecommerce.modules.user.dto;
public record AddressRequest(String receiver, String phone, String province, String city, String district, String detail, Boolean isDefault) { }
