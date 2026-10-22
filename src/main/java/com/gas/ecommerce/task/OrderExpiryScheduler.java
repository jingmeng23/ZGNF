package com.gas.ecommerce.task;

import com.gas.ecommerce.modules.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.orders.expiry-scan-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    public OrderExpiryScheduler(JdbcTemplate jdbc, OrderService orders) { this.jdbc = jdbc; this.orders = orders; }

    @Scheduled(initialDelayString = "${app.orders.expiry-scan-ms:60000}",
            fixedDelayString = "${app.orders.expiry-scan-ms:60000}")
    public void cancelExpiredOrders() {
        var orderNos = jdbc.queryForList("SELECT order_no FROM `order` WHERE status='pending_payment' " +
                "AND expire_time<=CURRENT_TIMESTAMP AND is_deleted=0 ORDER BY id LIMIT 100", String.class);
        for (String orderNo : orderNos) {
            try { orders.cancelExpired(orderNo); }
            catch (RuntimeException ex) { log.error("Failed to cancel expired order {}", orderNo, ex); }
        }
    }
}
