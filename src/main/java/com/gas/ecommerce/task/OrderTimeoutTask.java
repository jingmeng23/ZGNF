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
        orders.cancelExpired(orderNo);
    }
}
