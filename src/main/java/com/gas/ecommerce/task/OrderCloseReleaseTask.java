package com.gas.ecommerce.task;
import com.gas.ecommerce.modules.order.service.OrderCloseReleaseService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.orders.close-release-enabled",havingValue="true",matchIfMissing=true)
public class OrderCloseReleaseTask {
    private final OrderCloseReleaseService releases;
    public OrderCloseReleaseTask(OrderCloseReleaseService releases) { this.releases=releases; }
    @Scheduled(initialDelayString="${app.orders.close-release-ms:30000}",fixedDelayString="${app.orders.close-release-ms:30000}")
    public void compensate() { releases.scan(); }
}
