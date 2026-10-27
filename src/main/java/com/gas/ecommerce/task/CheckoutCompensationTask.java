package com.gas.ecommerce.task;

import com.gas.ecommerce.modules.order.service.CheckoutCompensationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.orders.compensation-enabled",havingValue="true",matchIfMissing=true)
public class CheckoutCompensationTask {
    private final CheckoutCompensationService compensation;
    public CheckoutCompensationTask(CheckoutCompensationService compensation) { this.compensation=compensation; }
    @RabbitListener(queues="gas.order.compensate")
    public void message(String orderNo) { compensation.compensate(orderNo); }
    @Scheduled(fixedDelayString="${app.orders.compensation-delay-ms:30000}")
    public void scan() { compensation.recover(); }
}
