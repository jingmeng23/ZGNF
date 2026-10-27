package com.gas.ecommerce.modules.product.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.mapper.ProductMapper;
import com.gas.ecommerce.modules.inventory.service.InventoryReservationService;
import java.util.*;
import org.springframework.stereotype.Service;

/** Product-owned checkout contract; order never queries product or stock tables. */
@Service
public class ProductCheckoutService {
    public record Quote(Product product, long available) { }
    private final ProductMapper products;
    private final ProductBrowseService browse;
    private final InventoryReservationService inventory;
    public ProductCheckoutService(ProductMapper products, ProductBrowseService browse, InventoryReservationService inventory) {
        this.products=products; this.browse=browse; this.inventory=inventory;
    }
    public Map<Long,Quote> quote(Collection<Long> ids) {
        Set<Long> categories = new HashSet<>(); browse.categories().forEach(c -> categories.add(c.id()));
        Map<Long,Quote> result = new HashMap<>();
        for (Long id : ids) {
            Product product=products.selectById(id);
            if (product==null || !"on_sale".equals(product.getStatus()) || !categories.contains(product.getCategoryId()))
                throw new BusinessException("商品已下架或不可售");
            if (product.getPrice()==null || product.getPrice().signum()<0) throw new BusinessException("商品价格异常");
            result.put(id,new Quote(product,inventory.available(id)));
        }
        return result;
    }
}
