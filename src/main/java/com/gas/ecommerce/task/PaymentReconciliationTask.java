package com.gas.ecommerce.task;
import com.gas.ecommerce.modules.payment.service.PaymentRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="app.payments.reconciliation-enabled",havingValue="true",matchIfMissing=true)
public class PaymentReconciliationTask {
    private final PaymentRecoveryService recovery;
    public PaymentReconciliationTask(PaymentRecoveryService recovery) { this.recovery=recovery; }
    @Scheduled(initialDelayString="${app.payments.reconciliation-ms:30000}",fixedDelayString="${app.payments.reconciliation-ms:30000}")
    public void reconcile() { recovery.recover(); }
}
