package com.gas.ecommerce.modules.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.order.service.OrderOutbox;
import com.gas.ecommerce.modules.order.service.OrderStateMachine;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single verified-outcome handler for callbacks and authenticated provider queries.
 * Lock order, then payment, then recovery work, consistently with cancellation/refunds. */
@Service
public class PaymentOutcomeHandler {
    private final PaymentMapper payments;
    private final OrderMapper orders;
    private final CouponService coupons;
    private final OrderOutbox outbox;
    private final PaymentRecoveryStore recovery;
    private final JdbcTemplate db;
    public PaymentOutcomeHandler(PaymentMapper payments, OrderMapper orders, CouponService coupons,
            OrderOutbox outbox, PaymentRecoveryStore recovery, JdbcTemplate db) {
        this.payments=payments; this.orders=orders; this.coupons=coupons;
        this.outbox=outbox; this.recovery=recovery; this.db=db;
    }
    @Transactional
    public Payment accept(String channel, PaymentCallbackRequest r) {
        Payment found=payments.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getPaymentNo,r.paymentNo()));
        if (found==null || !channel.equals(found.getChannel())) throw error("Payment does not exist for channel");
        Order order=orders.lockById(found.getOrderId());
        Payment p=payments.lockById(found.getId());
        if (order==null || p==null || !p.getUserId().equals(order.getUserId())
                || p.getAmount().compareTo(r.amount())!=0 || p.getAmount().compareTo(order.getPayAmount())!=0)
            throw error("Payment ownership or amount mismatch");
        if (!java.util.Set.of("success","failed").contains(r.status()) || r.transactionNo()==null || r.transactionNo().isBlank())
            throw error("Invalid payment outcome");
        if (p.getProviderTransactionNo()!=null && !p.getProviderTransactionNo().equals(r.transactionNo()))
            throw error("Provider transaction identity changed");
        // Success/refund are monotonic: late failures must never downgrade them.
        if ("success".equals(p.getStatus()) || "refunded".equals(p.getStatus())) return p;
        if (r.eventTime()==null || r.eventTime().isBefore(p.getCreateTime().minusSeconds(1))
                || r.eventTime().isAfter(LocalDateTime.now().plusMinutes(5))) throw error("Invalid payment event time");
        p.setProviderTransactionNo(r.transactionNo());
        p.setStatus(r.status());
        if ("success".equals(r.status())) p.setPaidTime(r.eventTime());
        // DB unique(channel,provider_transaction_no) is the final cross-order trade fence.
        try { payments.updateById(p); }
        catch (DuplicateKeyException ex) { throw error("Provider transaction already belongs to another payment"); }
        if ("success".equals(r.status())) {
            // Expiry time does not invalidate already collected money. Close protocol, not
            // application wall-clock ordering, arbitrates payment versus cancellation.
            if ("pending_payment".equals(order.getStatus())) {
                coupons.markUsed(order); // Current coupon ledger shares this local transaction.
                OrderStateMachine.persistTransition(order,"paid",db);
                outbox.paid(order);
                recovery.complete(p.getId());
            } else {
                // Cancelled/released order or second successful payment: never fulfill twice
                // or revive an order whose reservations may already have been sold.
                recovery.refund(p.getId());
            }
        }
        return p;
    }
    private BusinessException error(String message) { return new BusinessException(message); }
}
