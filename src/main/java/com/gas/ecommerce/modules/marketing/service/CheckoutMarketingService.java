package com.gas.ecommerce.modules.marketing.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.service.CheckoutResourceFence;
import com.gas.ecommerce.common.utils.CheckoutDigest;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Marketing owns coupon eligibility, threshold discounts, stacking and idempotent reservation. */
@Service
public class CheckoutMarketingService {
    public record Quote(BigDecimal fullReductionAmount, BigDecimal couponAmount) {
        public BigDecimal discountAmount() { return fullReductionAmount.add(couponAmount); }
    }
    private final JdbcTemplate db;
    private final CouponService coupons;
    private final CheckoutResourceFence fence;
    public CheckoutMarketingService(JdbcTemplate db, CouponService coupons, CheckoutResourceFence fence) {
        this.db=db; this.coupons=coupons; this.fence=fence;
    }
    public Quote quote(Long userId, Long couponId, BigDecimal subtotal) {
        var reductions=db.queryForList("SELECT discount_amount FROM checkout_full_reduction WHERE enabled=TRUE AND minimum_amount<=? AND start_time<=CURRENT_TIMESTAMP AND end_time>CURRENT_TIMESTAMP ORDER BY discount_amount DESC,id ASC LIMIT 1",BigDecimal.class,subtotal);
        BigDecimal reduction=reductions.isEmpty()?BigDecimal.ZERO:reductions.getFirst();
        if (reduction.signum()<0 || reduction.compareTo(subtotal)>0) throw new BusinessException("满减规则金额异常");
        return new Quote(reduction,coupons.preview(userId,couponId,subtotal.subtract(reduction)));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reserve(String number, Long userId, Long couponId, BigDecimal subtotal, Quote expected) {
        String payload=userId+":"+couponId+":"+subtotal.stripTrailingZeros()+":"+expected.fullReductionAmount().stripTrailingZeros()+":"+expected.couponAmount().stripTrailingZeros();
        if (!fence.reserve("marketing",number,CheckoutDigest.sha256(payload))) return;
        Quote current=quote(userId,couponId,subtotal);
        if (current.fullReductionAmount().compareTo(expected.fullReductionAmount())!=0 || current.couponAmount().compareTo(expected.couponAmount())!=0)
            throw new BusinessException("优惠已变化，请重新结算");
        BigDecimal coupon=coupons.reserve(userId,couponId,null,subtotal.subtract(current.fullReductionAmount()));
        if (coupon.compareTo(expected.couponAmount())!=0) throw new BusinessException("优惠券金额已变化");
        db.update("INSERT INTO checkout_coupon_reservation(order_no,user_id,user_coupon_id,total_amount,full_reduction_amount,coupon_amount) VALUES(?,?,?,?,?,?)",
                number,userId,couponId,subtotal,current.fullReductionAmount(),coupon);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void release(String number) {
        if (!fence.release("marketing",number)) return;
        var row=db.queryForMap("SELECT user_id,user_coupon_id,order_id FROM checkout_coupon_reservation WHERE order_no=?",number);
        coupons.releaseCheckout(((Number)row.get("user_id")).longValue(),nullableLong(row.get("user_coupon_id")),nullableLong(row.get("order_id")));
    }
    /** Local adapter association only; remote resource providers use orderNo and never require the DB ID. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void bind(String number, Long orderId) {
        fence.requireReserved("marketing",number);
        var row=db.queryForMap("SELECT user_coupon_id FROM checkout_coupon_reservation WHERE order_no=?",number);
        Long couponId=nullableLong(row.get("user_coupon_id"));
        if (couponId!=null && db.update("UPDATE user_coupon SET used_order_id=? WHERE id=? AND status='locked' AND (used_order_id IS NULL OR used_order_id=?)",orderId,couponId,orderId)!=1)
            throw new BusinessException("优惠券预占归属异常");
        db.update("UPDATE checkout_coupon_reservation SET order_id=? WHERE order_no=?",orderId,number);
    }
    private static Long nullableLong(Object value) { return value==null?null:((Number)value).longValue(); }
}
