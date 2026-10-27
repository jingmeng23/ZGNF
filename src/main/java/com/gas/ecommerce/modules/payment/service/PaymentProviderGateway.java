package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.entity.Payment;

/** Payment-service port. Adapters authenticate merchant/currency/amount and bound network timeouts.
 * CLOSED is definitive: the provider has fenced further payment on this paymentNo.
 * A failed payment notification is NOT proof of a closed payment session.
 */
public interface PaymentProviderGateway {
    enum State { SUCCESS, FAILED, PENDING, CLOSED, UNKNOWN }
    record Snapshot(State state, PaymentCallbackRequest outcome) {
        public static Snapshot unknown() { return new Snapshot(State.UNKNOWN, null); }
    }
    Snapshot query(Payment payment);
    Snapshot close(Payment payment);
    /** True only for a confirmed full refund (or an already completed refund).
     * Retry the same refundNo after timeout; never generate another refund identity. */
    boolean refund(Payment payment, String refundNo);
}
