package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/** Preview binds an order number to its owner and quote, without reserving resources. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CheckoutTicketService {
    private final JdbcTemplate db;
    public CheckoutTicketService(JdbcTemplate db) { this.db = db; }
    public String issue(String number, Long userId, String fingerprint, LocalDateTime expires) {
        String token=com.gas.ecommerce.common.utils.CheckoutDigest.token();
        db.update("INSERT INTO order_checkout_ticket(order_no,user_id,fingerprint,expire_time,token_hash) VALUES(?,?,?,?,?)", number, userId, fingerprint, expires, com.gas.ecommerce.common.utils.CheckoutDigest.sha256(token));
        return token;
    }
    public void validateIdentity(Long userId, OrderCreateRequest request) { read(userId,request); }
    public void validate(Long userId, OrderCreateRequest request, String fingerprint) {
        if (request.orderNo() == null) return; // Compatibility with existing requestId-only clients.
        var row=read(userId,request);
        if (!fingerprint.equals(row.get("fingerprint"))) throw new BusinessException("商品、价格、地址或优惠已变化，请重新结算");
    }
    private Map<String,Object> read(Long userId, OrderCreateRequest request) {
        if (request.orderNo()==null) return Map.of();
        var rows = db.queryForList("SELECT fingerprint,expire_time,token_hash FROM order_checkout_ticket WHERE order_no=? AND user_id=? FOR UPDATE", request.orderNo(), userId);
        if (rows.isEmpty()) throw new BusinessException("结算订单号不存在");
        var row = rows.getFirst();
        if (request.checkoutToken()==null || !com.gas.ecommerce.common.utils.CheckoutDigest.sha256(request.checkoutToken()).equals(row.get("token_hash")))
            throw new BusinessException("结算Token无效");
        LocalDateTime expires = ((java.sql.Timestamp) row.get("expire_time")).toLocalDateTime();
        if (!expires.isAfter(LocalDateTime.now())) throw new BusinessException("结算预览已过期，请重新结算");
        return row;
    }
    public static String fingerprint(Long addressId, Long couponId, List<CartItem> cart,
            List<OrderItem> items, BigDecimal discount, String addressSnapshot) {
        StringBuilder value = new StringBuilder().append(addressSnapshot).append('|').append(addressId).append(':').append(couponId).append(':').append(discount.stripTrailingZeros().toPlainString());
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            value.append('|').append(cart.get(i).getId()).append(':').append(item.getProductId()).append(':')
                    .append(item.getQuantity()).append(':').append(item.getUnitPrice().stripTrailingZeros().toPlainString());
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
