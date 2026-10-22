package com.gas.ecommerce.task;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/**
 * 订单超时消息处理入口。条件更新使重复消息不会重复取消已变更状态的订单；库存释放仍需在订单事务中补充。
 */
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
