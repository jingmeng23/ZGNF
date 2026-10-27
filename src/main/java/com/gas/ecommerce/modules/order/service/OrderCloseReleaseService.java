package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import org.springframework.transaction.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;

/** Durable, ordered release saga. A completed step never runs again. Remote ports must
 * deduplicate by orderNo because a crash can occur between remote success and local commit. */
@Service
public class OrderCloseReleaseService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OrderCloseReleaseService.class);
    private final JdbcTemplate db;
    private final OrderMapper orders;
    private final OrderInventoryService stock;
    private final CouponService coupons;
    private final CheckoutPointsGateway points;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    public OrderCloseReleaseService(JdbcTemplate db,OrderMapper orders,OrderInventoryService stock,
            CouponService coupons,CheckoutPointsGateway points,PlatformTransactionManager manager,MeterRegistry metrics) {
        this.db=db;this.orders=orders;this.stock=stock;this.coupons=coupons;this.points=points;this.metrics=metrics;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(Long id) {
        db.update("INSERT INTO order_close_release(order_id) VALUES(?)",id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { release(id); }
        });
    }
    public void scan() {
        for(Long id:db.queryForList("SELECT order_id FROM order_close_release WHERE step<3 AND next_attempt_time<=CURRENT_TIMESTAMP ORDER BY next_attempt_time,order_id LIMIT 100",Long.class)) release(id);
    }
    public void release(Long id) {
        try {
            for(int i=0;i<3;i++) {
                Boolean progressed=tx.execute(status->{
                    var order=orders.lockById(id);
                    var rows=db.queryForList("SELECT step FROM order_close_release WHERE order_id=? AND step<3 AND next_attempt_time<=CURRENT_TIMESTAMP FOR UPDATE",id);
                    if(rows.isEmpty()) return false;
                    if(order==null || !"cancelled".equals(order.getStatus())) throw new IllegalStateException("Release requires cancelled order");
                    int step=((Number)rows.getFirst().get("step")).intValue();
                    switch(step) {
                        case 0 -> stock.release(order);
                        case 1 -> coupons.release(order);
                        case 2 -> points.release(order.getOrderNo());
                        default -> throw new IllegalStateException("Invalid release step");
                    }
                    db.update("UPDATE order_close_release SET step=step+1,attempts=0,last_error=NULL WHERE order_id=? AND step=?",id,step);
                    return true;
                });
                if(!Boolean.TRUE.equals(progressed)) break;
            }
        } catch(RuntimeException ex) {
            tx.executeWithoutResult(status->db.update("UPDATE order_close_release SET attempts=attempts+1,next_attempt_time=?,last_error=? WHERE order_id=? AND step<3",
                    LocalDateTime.now().plusSeconds(60),ex.getClass().getSimpleName(),id));
            metrics.counter("order.close.release.failures").increment();
            LOG.error("ORDER_CLOSE_RELEASE_FAILED orderId={} retry in 60s; inspect order_close_release",id,ex);
        }
    }
}
