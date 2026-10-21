package com.gas.ecommerce.task;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Conditional update makes repeated timeout messages harmless. Extend with stock unlock in the order transaction. */
@Component public class OrderTimeoutTask {
    private final JdbcTemplate jdbc;
    public OrderTimeoutTask(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @RabbitListener(queues = "gas.order.timeout.cancel")
    public void cancelExpiredOrder(String orderNo) {
        jdbc.update("""
            UPDATE `order` SET status='cancelled', update_time=NOW()
            WHERE order_no=? AND status='pending_payment' AND expire_time<=NOW() AND is_deleted=0
            """, orderNo);
    }
}