package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.entity.Order;
import java.util.Map;
import java.util.Set;
/** Sole entry for order status changes. Existing orders must be row-locked by the caller. */
public final class OrderStateMachine {
    private OrderStateMachine() { }
    private static final Map<String, Set<String>> NEXT = Map.of(
        "pending_payment", Set.of("paid", "cancelled"),
        "paid", Set.of("processing", "refunded"),
        "processing", Set.of("completed", "refunded"),
        "completed", Set.of("refunded"));
    public static void initialize(Order order) {
        if (order.getStatus() != null) throw new BusinessException("订单已经初始化");
        order.setStatus("pending_payment");
    }
    public static void transition(Order order, String target) {
        if (target.equals(order.getStatus())) return;
        if (order.getStatus() == null || !NEXT.getOrDefault(order.getStatus(), Set.of()).contains(target))
            throw new BusinessException("不允许的订单状态流转：" + order.getStatus() + " -> " + target);
        order.setStatus(target);
    }
    /** Conditional SQL lives inside the state-machine entry point, never in business callers. */
    public static void persistTransition(Order order, String target, org.springframework.jdbc.core.JdbcTemplate db) {
        String previous=order.getStatus();
        if (target.equals(previous)) return;
        transition(order,target);
        String guard="cancelled".equals(target)
                ? " AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.order_id=`order`.id AND p.status='success' AND p.is_deleted=0)" : "";
        int updated=db.update("UPDATE `order` SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status=? AND is_deleted=0"+guard,
                target,order.getId(),previous);
        if (updated!=1) throw new BusinessException("Concurrent order transition rejected");
        db.update("INSERT INTO order_status_log(order_id,from_status,to_status) VALUES(?,?,?)",order.getId(),previous,target);
    }
}
