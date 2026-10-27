package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Caller holds the order lock. Only definitive provider closure permits releasing resources. */
@Service
public class PaymentCloseGuard {
    private final PaymentMapper payments;
    private final PaymentProviderGateway gateway;
    private final PaymentOutcomeHandler outcomes;
    public PaymentCloseGuard(PaymentMapper payments, PaymentProviderGateway gateway, PaymentOutcomeHandler outcomes) {
        this.payments=payments; this.gateway=gateway; this.outcomes=outcomes;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean mayClose(Order order) {
        // Payment creation holds the same order lock. Once we own it, this current
        // read sees every committed attempt, even if our transaction has an older snapshot.
        var attempts=payments.lockAttemptsForOrder(order.getId());
        if (attempts.stream().anyMatch(p->"success".equals(p.getStatus()))) return false;
        boolean paid=false;
        for (var p:attempts) {
            if ("refunded".equals(p.getStatus())) continue;
            // Atomic provider close-or-return-success; a query alone leaves a race window.
            var snapshot=gateway.close(p);
            if (snapshot==null) throw new BusinessException("Payment closure outcome unknown; retry later");
            if (snapshot.state()==PaymentProviderGateway.State.SUCCESS) {
                if (snapshot.outcome()==null || !p.getPaymentNo().equals(snapshot.outcome().paymentNo())
                        || !"success".equals(snapshot.outcome().status())) throw new BusinessException("Invalid provider close outcome");
                outcomes.accept(p.getChannel(),snapshot.outcome()); paid=true;
            } else if (snapshot.state()==PaymentProviderGateway.State.CLOSED) {
                p.setStatus("closed"); payments.updateById(p);
            } else throw new BusinessException("Payment closure outcome unknown; resources retained for retry");
        }
        return !paid;
    }
}
