package com.gas.ecommerce.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 64) String receiver,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Size(max = 64) String province,
        @NotBlank @Size(max = 64) String city,
        @NotBlank @Size(max = 64) String district,
        @NotBlank @Size(max = 255) String detail,
        Boolean isDefault,
        @Size(max=20) String label,
        @Pattern(regexp="[0-9]{6,12}") String provinceCode,
        @Pattern(regexp="[0-9]{6,12}") String cityCode,
        @Pattern(regexp="[0-9]{6,12}") String districtCode,
        @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") java.math.BigDecimal latitude,
        @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") java.math.BigDecimal longitude,
        Boolean allowDuplicate) {
    public AddressRequest(String receiver,String phone,String province,String city,String district,String detail,Boolean isDefault) {
        this(receiver,phone,province,city,district,detail,isDefault,null,null,null,null,null,null,false);
    }
    @jakarta.validation.constraints.AssertTrue(message="省市区编码和经纬度必须分别成组提供")
    public boolean isLocationComplete() {
        return ((provinceCode==null && cityCode==null && districtCode==null) || (provinceCode!=null && cityCode!=null && districtCode!=null))
                && ((latitude==null)==(longitude==null));
    }
}
