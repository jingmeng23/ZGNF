package com.gas.ecommerce.modules.user.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AddressCheckoutService {
    public record Quote(UserAddress address, BigDecimal freightAmount, boolean reachable) { }
    private final UserAddressService addresses;
    private final AddressRegionService regions;
    private final com.gas.ecommerce.modules.delivery.service.DeliveryQuoteService delivery;
    private final boolean requireCodes;
    public AddressCheckoutService(UserAddressService addresses,AddressRegionService regions,com.gas.ecommerce.modules.delivery.service.DeliveryQuoteService delivery,
            @Value("${app.addresses.require-region-codes-at-checkout:true}") boolean requireCodes) {
        this.addresses=addresses;this.regions=regions;this.delivery=delivery;this.requireCodes=requireCodes;
    }
    public Quote quote(Long userId, Long addressId) {
        UserAddress address=addresses.getById(addressId);
        if (address==null || !userId.equals(address.getUserId())) throw new BusinessException("收货地址不存在");
        if(requireCodes || address.getProvinceCode()!=null || address.getCityCode()!=null || address.getDistrictCode()!=null) regions.requireValid(address);
        return new Quote(address,delivery.quote(address.getProvince(),address.getCity(),address.getDistrict()),true);
    }
}
