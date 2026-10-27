package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import java.math.*;
import java.util.List;
/** Cumulative rounding conserves cents, including the final line's remainder. */
public final class OrderDiscountAllocator {
    private OrderDiscountAllocator() { }
    public static void allocate(List<OrderItem> items, BigDecimal total, BigDecimal discount) {
        BigDecimal cumulative = BigDecimal.ZERO, previous = BigDecimal.ZERO;
        for (OrderItem item : items) {
            cumulative = cumulative.add(item.getAmount());
            BigDecimal allocated = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : discount.multiply(cumulative).divide(total, 2, RoundingMode.DOWN);
            item.setDiscountAmount(allocated.subtract(previous));
            item.setPayAmount(item.getAmount().subtract(item.getDiscountAmount()));
            previous = allocated;
        }
    }
}
