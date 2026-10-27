package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Durable retry work. All workers lock order, payment, then recovery row.
 * Provider adapters must bound network calls and reuse paymentNo/refundNo on retries. */
@Service
public class PaymentRecoveryService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PaymentRecoveryService.class);
    private final JdbcTemplate db;
    private final PaymentMapper payments;
    private final OrderMapper orders;
    private final PaymentProviderGateway gateway;
    private final PaymentOutcomeHandler outcomes;
    private final TransactionTemplate transactions;
    public PaymentRecoveryService(JdbcTemplate db, PaymentMapper payments, OrderMapper orders,
            PaymentProviderGateway gateway, PaymentOutcomeHandler outcomes, PlatformTransactionManager manager) {
        this.db=db; this.payments=payments; this.orders=orders; this.gateway=gateway; this.outcomes=outcomes;
        this.transactions=new TransactionTemplate(manager);
    }
    public void recover() {
        transactions.executeWithoutResult(tx -> db.update("INSERT INTO payment_recovery(payment_id) SELECT p.id FROM payment p WHERE p.is_deleted=0 AND p.status IN ('pending','failed','closed') AND NOT EXISTS (SELECT 1 FROM payment_recovery r WHERE r.payment_id=p.id) ON DUPLICATE KEY UPDATE payment_id=payment_id"));
        for (Long id:db.queryForList("SELECT payment_id FROM payment_recovery WHERE status='pending' AND next_attempt_time<=CURRENT_TIMESTAMP ORDER BY next_attempt_time,payment_id LIMIT 100",Long.class)) {
            try { runOne(id); }
            catch (RuntimeException ex) {
                transactions.executeWithoutResult(tx -> db.update("UPDATE payment_recovery SET attempts=attempts+1,next_attempt_time=?,last_error=?,update_time=CURRENT_TIMESTAMP WHERE payment_id=? AND status='pending'",
                        LocalDateTime.now().plusSeconds(60),ex.getClass().getSimpleName(),id));
                LOG.warn("Payment recovery failed for {}: {}",id,ex.getClass().getSimpleName());
            }
        }
    }
    public void runOne(Long id) {
        transactions.executeWithoutResult(tx -> {
            var found=payments.selectById(id);
            if (found==null) return;
            var order=orders.lockById(found.getOrderId());
            var p=payments.lockById(id);
            var rows=db.queryForList("SELECT action,attempts FROM payment_recovery WHERE payment_id=? AND status='pending' AND next_attempt_time<=CURRENT_TIMESTAMP FOR UPDATE",id);
            if (rows.isEmpty() || p==null || order==null) return;
            String action=(String)rows.getFirst().get("action");
            if ("refund".equals(action)) {
                if (!"refunded".equals(p.getStatus())) {
                    if (!"success".equals(p.getStatus())) throw new IllegalStateException("Refund requires persisted receipt");
                    if (!gateway.refund(p,"LATE-"+p.getPaymentNo())) throw new IllegalStateException("Refund unconfirmed");
                    p.setStatus("refunded"); payments.updateById(p);
                }
                db.update("UPDATE payment_recovery SET status='done',last_error=NULL,update_time=CURRENT_TIMESTAMP WHERE payment_id=?",id);
                return;
            }
            if (java.util.Set.of("success","refunded").contains(p.getStatus())) {
                db.update("UPDATE payment_recovery SET status='done',update_time=CURRENT_TIMESTAMP WHERE payment_id=?",id); return;
            }
            var snapshot=gateway.query(p);
            if (snapshot==null || snapshot.state()==PaymentProviderGateway.State.UNKNOWN) throw new IllegalStateException("Provider status unknown");
            if (snapshot.state()==PaymentProviderGateway.State.SUCCESS || snapshot.state()==PaymentProviderGateway.State.FAILED) {
                var r=snapshot.outcome();
                String expected=snapshot.state()==PaymentProviderGateway.State.SUCCESS?"success":"failed";
                if (r==null || !p.getPaymentNo().equals(r.paymentNo()) || !expected.equals(r.status())) throw new IllegalStateException("Invalid provider query outcome");
                outcomes.accept(p.getChannel(),r);
            }
            // Keep closed attempts queryable for delayed settlements; never silently discard unresolved work.
            db.update("UPDATE payment_recovery SET attempts=attempts+1,next_attempt_time=?,last_error=NULL,update_time=CURRENT_TIMESTAMP WHERE payment_id=? AND status='pending' AND action='query'",
                    LocalDateTime.now().plusSeconds(Math.min(300L,30L+((Number)rows.getFirst().get("attempts")).longValue()*5)),id);
        });
    }
}
