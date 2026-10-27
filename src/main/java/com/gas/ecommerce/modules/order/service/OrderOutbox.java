package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.order.entity.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/** Persisted with the order. A rollback also rolls back its messages. */
@Service
public class OrderOutbox {
    private final JdbcTemplate db;
    public OrderOutbox(JdbcTemplate db) { this.db = db; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void created(Order order) {
        db.update("INSERT INTO order_status_log(order_id,from_status,to_status) VALUES(?,NULL,?)",order.getId(),order.getStatus());
        for (String type : java.util.List.of("order.created", "order.timeout"))
            db.update("INSERT INTO order_outbox(order_no,event_type,status,attempts,next_attempt_time) VALUES(?,?,'pending',0,CURRENT_TIMESTAMP)", order.getOrderNo(), type);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void paid(Order order) {
        for (String type : java.util.List.of("payment.stock", "payment.points", "payment.notification"))
            db.update("INSERT INTO order_outbox(order_no,event_type,status,attempts,next_attempt_time) VALUES(?,?,'pending',0,CURRENT_TIMESTAMP)", order.getOrderNo(), type);
    }
}
