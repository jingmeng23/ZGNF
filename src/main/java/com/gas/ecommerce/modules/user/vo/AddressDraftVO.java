package com.gas.ecommerce.modules.user.vo;
import java.util.List;
/** A suggestion only. Never persisted until the user reviews the address form and saves it. */
public record AddressDraftVO(String receiver,String phone,String province,String city,String district,String detail,
        String provinceCode,String cityCode,String districtCode,boolean needsConfirmation,List<String> warnings) { }
