package com.gas.ecommerce.modules.user.vo;

public record UserAddressVO(Long id, String receiver, String phone, String province,
        String city, String district, String detail, boolean isDefault,
        String label,String provinceCode,String cityCode,String districtCode,
        java.math.BigDecimal latitude,java.math.BigDecimal longitude,String coordinateSystem,
        boolean needsRegionCompletion,String regionMessage,String suggestedDistrictCode,String adcode) {
    @com.fasterxml.jackson.annotation.JsonProperty("fullAddress") public String fullAddress() {
        if (detail.startsWith(province)) return detail;
        return java.util.stream.Stream.of(province,city,district).distinct().filter(s -> !s.equals("市辖区") && !s.equals("县")).collect(java.util.stream.Collectors.joining()) + detail;
    }
}
