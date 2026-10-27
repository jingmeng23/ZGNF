package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.order.vo.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;

/** Called only after ownership is established. Children are additionally scoped by user/order. */
@Service
public class OrderDetailAssembler {
    private final JdbcTemplate db;
    private final ObjectProvider<OrderLogisticsGateway> logistics;
    private final MeterRegistry metrics;
    public OrderDetailAssembler(JdbcTemplate db,ObjectProvider<OrderLogisticsGateway> logistics,MeterRegistry metrics) {
        this.db=db;this.logistics=logistics;this.metrics=metrics;
    }
    public OrderVO enrich(OrderVO order) {
        var timeline=db.query("SELECT l.id,l.from_status,l.to_status,l.occurred_at FROM order_status_log l JOIN `order` o ON o.id=l.order_id WHERE o.id=? AND o.user_id=? AND o.is_deleted=0 ORDER BY l.id",
                (r,n)->new OrderStatusLogVO(r.getLong(1),r.getString(2),r.getString(3),r.getTimestamp(4).toLocalDateTime()),order.id(),order.userId());
        var payments=db.query("SELECT payment_no,channel,status,amount,paid_time FROM payment WHERE order_id=? AND user_id=? AND is_deleted=0 ORDER BY id DESC",
                (r,n)->new OrderPaymentVO(r.getString(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getTimestamp(5)==null?null:r.getTimestamp(5).toLocalDateTime()),order.id(),order.userId());
        OrderLogisticsVO shipment=null;
        try {
            var provider=logistics.getIfAvailable();
            if(provider!=null) shipment=provider.find(order.userId(),order.orderNo());
        } catch(RuntimeException ex) { metrics.counter("order.detail.logistics.degraded").increment(); }
        return order.withDetail(timeline,payments,shipment);
    }
}
