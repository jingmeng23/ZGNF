package com.gas.ecommerce.task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/**
 * 每日支付对账任务的框架入口。接入支付服务商的对账适配器之前，本任务只记录未配置状态，不会执行真实对账。
 */
@Component public class PaymentReconciliationTask {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PaymentReconciliationTask.class);
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai") public void reconcile() {
        LOG.warn("Payment reconciliation skipped: no payment provider reconciliation adapter configured");
    }
}
