package com.gas.ecommerce.modules.user.vo;

public record UserAddressVO(Long id, String receiver, String phone, String province,
        String city, String district, String detail, boolean isDefault,
        String label,String provinceCode,String cityCode,String districtCode,
        java.math.BigDecimal latitude,java.math.BigDecimal longitude,String coordinateSystem,
        boolean needsRegionCompletion,String regionMessage,String suggestedDistrictCode) { }
