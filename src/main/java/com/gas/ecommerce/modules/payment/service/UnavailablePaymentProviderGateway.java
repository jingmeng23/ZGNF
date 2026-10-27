package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.payment.entity.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fail closed until an authenticated downstream payment adapter is configured. */
@Configuration
public class UnavailablePaymentProviderGateway {
    @Bean @ConditionalOnMissingBean(PaymentProviderGateway.class)
    PaymentProviderGateway paymentProviderGateway() {
        return new PaymentProviderGateway() {
            public Snapshot query(Payment payment) { return Snapshot.unknown(); }
            public Snapshot close(Payment payment) { return Snapshot.unknown(); }
            public boolean refund(Payment payment, String refundNo) { return false; }
        };
    }
}
