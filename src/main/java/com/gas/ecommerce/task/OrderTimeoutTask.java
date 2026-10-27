package com.gas.ecommerce.task;

import com.gas.ecommerce.modules.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutTask {
    private final OrderService orders;
    public OrderTimeoutTask(OrderService orders) { this.orders = orders; }

    @RabbitListener(queues = "gas.order.timeout.cancel")
    public void cancelExpiredOrder(String orderNo) {
        try { orders.cancelExpired(orderNo); }
        catch (RuntimeException ex) {
            // Do not hot-loop poison/unknown-provider messages. DB expiry scan retries.
            org.slf4j.LoggerFactory.getLogger(OrderTimeoutTask.class)
                    .error("ORDER_TIMEOUT_DEFERRED orderNo={} database scan will retry",orderNo,ex);
        }
    }
}
