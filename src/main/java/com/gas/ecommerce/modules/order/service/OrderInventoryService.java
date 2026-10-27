package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.inventory.service.InventoryReservationService;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import java.util.List;
import org.springframework.stereotype.Service;
/** Order adapter; stock calculations belong to inventory. */
@Service
public class OrderInventoryService {
    private final InventoryReservationService inventory;
    public OrderInventoryService(InventoryReservationService inventory) { this.inventory = inventory; }
    public long available(Long productId) { return inventory.available(productId); }
    public void lockCheckout(String orderNo, List<OrderItem> items) { inventory.lockCheckout(orderNo, items); }
    public void releaseCheckout(String orderNo) { inventory.releaseCheckout(orderNo); }
    public void bindCheckout(String orderNo, Long orderId) { inventory.bindCheckout(orderNo, orderId); }
    public void reserve(Order order, List<OrderItem> items) { inventory.reserve(order, items); }
    public void release(Order order) { inventory.release(order); }
    public void consume(Order order) { inventory.consume(order); }
}
