package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stock changes must participate in the order transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderInventoryService {
    private final JdbcTemplate jdbc;
    public OrderInventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void reserve(Order order, List<OrderItem> items) {
        // Lock products and inventory rows in a consistent order across customers.
        for (OrderItem item : items.stream().sorted(Comparator.comparing(OrderItem::getProductId)).toList()) {
            List<Stock> stocks = jdbc.query(
                    "SELECT id, product_id, store_id, quantity, locked_quantity FROM inventory " +
                    "WHERE product_id=? AND is_deleted=0 ORDER BY id FOR UPDATE",
                    (rs, n) -> new Stock(rs.getLong("id"), rs.getLong("product_id"),
                            rs.getObject("store_id", Long.class), rs.getInt("quantity"), rs.getInt("locked_quantity")),
                    item.getProductId());
            long available = stocks.stream().mapToLong(s -> Math.max(0L, (long) s.quantity() - s.locked())).sum();
            if (available < item.getQuantity()) throw new BusinessException("商品库存不足：" + item.getProductName());
            int remaining = item.getQuantity();
            for (Stock stock : stocks) {
                int taken = (int) Math.min(remaining, Math.max(0L, (long) stock.quantity() - stock.locked()));
                if (taken == 0) continue;
                int changed = jdbc.update("UPDATE inventory SET locked_quantity=locked_quantity+?, version=version+1, " +
                        "update_time=CURRENT_TIMESTAMP WHERE id=? AND is_deleted=0 AND quantity-locked_quantity>=?",
                        taken, stock.id(), taken);
                if (changed != 1) throw new BusinessException("库存已变化，请重新下单");
                jdbc.update("INSERT INTO order_stock_reservation(order_id,inventory_id,product_id,quantity,status) " +
                        "VALUES(?,?,?,?,'reserved')", order.getId(), stock.id(), item.getProductId(), taken);
                log(stock, taken, "order_lock", order.getOrderNo(), stock.quantity(), stock.locked()+taken);
                remaining -= taken;
                if (remaining == 0) break;
            }
        }
    }

    public void release(Order order) { finish(order, false); }
    public void consume(Order order) { finish(order, true); }

    private void finish(Order order, boolean consume) {
        List<Reservation> reservations = jdbc.query(
                "SELECT id, inventory_id, product_id, quantity FROM order_stock_reservation " +
                "WHERE order_id=? AND status='reserved' ORDER BY inventory_id",
                (rs, n) -> new Reservation(rs.getLong("id"), rs.getLong("inventory_id"),
                        rs.getLong("product_id"), rs.getInt("quantity")), order.getId());
        for (Reservation row : reservations) {
            Stock stock=jdbc.queryForObject("SELECT id,product_id,store_id,quantity,locked_quantity FROM inventory WHERE id=? FOR UPDATE",
                    (rs,n)->new Stock(rs.getLong("id"),rs.getLong("product_id"),rs.getObject("store_id",Long.class),rs.getInt("quantity"),rs.getInt("locked_quantity")),row.inventoryId());
            String sql = consume
                    ? "UPDATE inventory SET quantity=quantity-?, locked_quantity=locked_quantity-?, version=version+1, " +
                      "update_time=CURRENT_TIMESTAMP WHERE id=? AND is_deleted=0 AND quantity>=? AND locked_quantity>=?"
                    : "UPDATE inventory SET locked_quantity=locked_quantity-?, version=version+1, " +
                      "update_time=CURRENT_TIMESTAMP WHERE id=? AND is_deleted=0 AND locked_quantity>=?";
            int changed = consume
                    ? jdbc.update(sql, row.quantity(), row.quantity(), row.inventoryId(), row.quantity(), row.quantity())
                    : jdbc.update(sql, row.quantity(), row.inventoryId(), row.quantity());
            if (changed != 1) throw new BusinessException("订单预占库存异常，请联系管理员");
            jdbc.update("UPDATE order_stock_reservation SET status=? WHERE id=? AND status='reserved'",
                    consume ? "consumed" : "released", row.id());
            log(stock, -row.quantity(), consume ? "order_deduct" : "order_release", order.getOrderNo(),
                    stock.quantity()-(consume?row.quantity():0),stock.locked()-row.quantity());
        }
    }

    private void log(Stock stock, int quantity, String type, String orderNo, int after, int lockedAfter) {
        jdbc.update("INSERT INTO inventory_log(inventory_id,product_id,store_id,change_quantity,change_type,reference_no,quantity_before,quantity_after,locked_before,locked_after) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)", stock.id(),stock.productId(),stock.storeId(),quantity,type,orderNo,stock.quantity(),after,stock.locked(),lockedAfter);
    }

    private record Stock(Long id, Long productId, Long storeId, int quantity, int locked) { }
    private record Reservation(Long id, Long inventoryId, Long productId, int quantity) { }
}
