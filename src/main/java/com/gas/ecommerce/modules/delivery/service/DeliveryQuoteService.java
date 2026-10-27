package com.gas.ecommerce.modules.delivery.service;
import com.gas.ecommerce.common.exception.BusinessException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
/** Delivery owns reachability and freight; coordinates never override delivery rules. */
@Service
public class DeliveryQuoteService {
    private final JdbcTemplate db;private final boolean legacyFreeShipping;
    public DeliveryQuoteService(JdbcTemplate db,@Value("${app.checkout.delivery.legacy-free-shipping-enabled:false}") boolean legacyFreeShipping){this.db=db;this.legacyFreeShipping=legacyFreeShipping;}
    public BigDecimal quote(String province,String city,String district) {
        var rules=db.queryForList("SELECT reachable,freight_amount FROM checkout_delivery_rule WHERE province IN (?, '*') AND city IN (?, '*') AND district IN (?, '*') ORDER BY (CASE WHEN province='*' THEN 0 ELSE 4 END + CASE WHEN city='*' THEN 0 ELSE 2 END + CASE WHEN district='*' THEN 0 ELSE 1 END) DESC LIMIT 1",province,city,district);
        if(rules.isEmpty()) {if(legacyFreeShipping) return BigDecimal.ZERO;throw new BusinessException("配送服务未配置该地区规则，暂不能结算");}
        var row=rules.getFirst();if(!Boolean.TRUE.equals(row.get("reachable"))) throw new BusinessException("该地址不在配送范围内");
        var freight=(BigDecimal)row.get("freight_amount");if(freight==null || freight.signum()<0) throw new BusinessException("运费规则异常");return freight;
    }
}
