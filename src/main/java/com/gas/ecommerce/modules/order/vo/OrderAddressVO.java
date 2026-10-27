package com.gas.ecommerce.modules.order.vo;

public record OrderAddressVO(String receiver, String phone, String province,
        String city, String district, String detail,String provinceCode,String cityCode,String districtCode,
        java.math.BigDecimal latitude,java.math.BigDecimal longitude) {
    public OrderAddressVO(String receiver,String phone,String province,String city,String district,String detail) {
        this(receiver,phone,province,city,district,detail,null,null,null,null,null);
    }
    public OrderAddressVO withPhone(String value) {return new OrderAddressVO(receiver,value,province,city,district,detail,provinceCode,cityCode,districtCode,latitude,longitude);}
}
