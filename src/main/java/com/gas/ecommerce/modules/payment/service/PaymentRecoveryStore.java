package com.gas.ecommerce.modules.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentRecoveryStore {
    private final JdbcTemplate db;
    public PaymentRecoveryStore(JdbcTemplate db) { this.db = db; }
    public void created(Long paymentId) {
        db.update("INSERT INTO payment_recovery(payment_id) VALUES(?)", paymentId);
    }
    public void refund(Long paymentId) {
        // Also covers pre-migration payment rows; order/payment row locks serialize writers.
        if (db.update("UPDATE payment_recovery SET action='refund',status='pending',next_attempt_time=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP WHERE payment_id=?", paymentId)==0)
            db.update("INSERT INTO payment_recovery(payment_id,action) VALUES(?,'refund')", paymentId);
    }
    public void complete(Long paymentId) {
        db.update("UPDATE payment_recovery SET status='done',update_time=CURRENT_TIMESTAMP WHERE payment_id=? AND action='query'", paymentId);
    }
}
