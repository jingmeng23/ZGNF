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
        // A finite scan window prevents continuous inserts from extending this run.
        var cutoff=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class);
        Long upperId=jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM `order`",Long.class);
        long cursor=0;
        while (cursor<upperId) {
            var batch=jdbc.query("SELECT id,order_no FROM `order` WHERE status='pending_payment' " +
                    "AND expire_time<=? AND is_deleted=0 AND id>? AND id<=? ORDER BY id LIMIT 100",
                    (rs,n)->new ExpiredOrder(rs.getLong("id"),rs.getString("order_no")),cutoff,cursor,upperId);
            if(batch.isEmpty()) break;
            for (var order:batch) {
                // Advance even when closure fails: poison/UNKNOWN attempts must not starve later IDs.
                cursor=order.id();
                try { orders.cancelExpired(order.orderNo()); }
                catch (RuntimeException ex) { log.error("Failed to cancel expired order {}", order.orderNo(), ex); }
            }
        }
    }
    private record ExpiredOrder(long id,String orderNo) { }
}
