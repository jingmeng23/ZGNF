package com.gas.ecommerce.task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** A payment provider reconciliation adapter must be supplied before enabling this job. */
@Component public class PaymentReconciliationTask {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PaymentReconciliationTask.class);
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai") public void reconcile() {
        LOG.warn("Payment reconciliation skipped: no payment provider reconciliation adapter configured");
    }
}