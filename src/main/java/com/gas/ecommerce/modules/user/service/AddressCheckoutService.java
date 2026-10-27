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
    private final JdbcTemplate db;
    private final boolean legacyFreeShipping;
    public AddressCheckoutService(UserAddressService addresses, JdbcTemplate db,
            @Value("${app.checkout.delivery.legacy-free-shipping-enabled:false}") boolean legacyFreeShipping) {
        this.addresses=addresses; this.db=db; this.legacyFreeShipping=legacyFreeShipping;
    }
    public Quote quote(Long userId, Long addressId) {
        UserAddress address=addresses.getById(addressId);
        if (address==null || !userId.equals(address.getUserId())) throw new BusinessException("收货地址不存在");
        var rules=db.queryForList("SELECT reachable,freight_amount FROM checkout_delivery_rule WHERE province IN (?, '*') AND city IN (?, '*') AND district IN (?, '*') ORDER BY (CASE WHEN province='*' THEN 0 ELSE 4 END + CASE WHEN city='*' THEN 0 ELSE 2 END + CASE WHEN district='*' THEN 0 ELSE 1 END) DESC LIMIT 1",
                address.getProvince(),address.getCity(),address.getDistrict());
        if (rules.isEmpty()) {
            if (legacyFreeShipping) return new Quote(address,BigDecimal.ZERO,true);
            throw new BusinessException("地址服务未配置该地区配送规则，暂不能结算");
        }
        var rule=rules.getFirst();
        boolean reachable=Boolean.TRUE.equals(rule.get("reachable"));
        BigDecimal freight=(BigDecimal)rule.get("freight_amount");
        if (!reachable) throw new BusinessException("该地址不在配送范围内");
        if (freight==null || freight.signum()<0) throw new BusinessException("运费规则异常");
        return new Quote(address,freight,true);
    }
}
