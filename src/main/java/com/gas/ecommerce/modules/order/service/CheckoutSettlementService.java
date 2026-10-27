package com.gas.ecommerce.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import com.gas.ecommerce.modules.product.service.ProductCheckoutService;
import com.gas.ecommerce.modules.user.service.AddressCheckoutService;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class CheckoutSettlementService {
    public record Settlement(List<CartItem> selected, List<OrderItem> items, BigDecimal total,
            AddressCheckoutService.Quote address, CheckoutMarketingService.Quote marketing, String fingerprint) {
        public BigDecimal payAmount() { return total.subtract(marketing.discountAmount()).add(address.freightAmount()); }
    }
    private final CartItemMapper cart;
    private final ProductCheckoutService products;
    private final CheckoutMarketingService marketing;
    private final AddressCheckoutService addresses;
    private final ObjectMapper json;
    public CheckoutSettlementService(CartItemMapper cart, ProductCheckoutService products,
            CheckoutMarketingService marketing, AddressCheckoutService addresses, ObjectMapper json) {
        this.cart=cart; this.products=products; this.marketing=marketing; this.addresses=addresses; this.json=json;
    }
    public Settlement settle(Long userId, Long addressId, Long couponId) {
        var selected=cart.selectList(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId,userId)
                .eq(CartItem::getSelected,true).orderByAsc(CartItem::getProductId));
        if (selected.isEmpty()) throw new BusinessException("请先选择购物车商品");
        var productQuotes=critical("商品服务",()->products.quote(selected.stream().map(CartItem::getProductId).toList()));
        var address=critical("地址服务",()->addresses.quote(userId,addressId));
        if (address==null || !address.reachable() || address.freightAmount()==null || address.freightAmount().signum()<0)
            throw new BusinessException("地址不可达或运费报价不完整");
        List<OrderItem> items=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for (CartItem line : selected) {
            if (line.getQuantity()==null || line.getQuantity()<1 || line.getQuantity()>999) throw new BusinessException("商品数量须为1至999");
            var quote=productQuotes.get(line.getProductId());
            if (quote==null) throw new BusinessException("商品服务报价不完整");
            if (quote.available()<line.getQuantity()) throw new BusinessException("商品库存不足");
            var product=quote.product();
            OrderItem item=new OrderItem(); item.setProductId(product.getId()); item.setProductName(product.getName());
            item.setProductImage(product.getCoverUrl()); item.setUnitPrice(product.getPrice()); item.setQuantity(line.getQuantity());
            item.setAmount(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            total=total.add(item.getAmount()); items.add(item);
        }
        final BigDecimal subtotal=total;
        var discount=critical("营销服务",()->marketing.quote(userId,couponId,subtotal));
        if (discount==null || discount.fullReductionAmount()==null || discount.couponAmount()==null
                || discount.fullReductionAmount().signum()<0 || discount.couponAmount().signum()<0
                || discount.discountAmount().compareTo(total)>0) throw new BusinessException("营销报价金额异常");
        if (total.add(address.freightAmount()).compareTo(new BigDecimal("99999999.99"))>0) throw new BusinessException("订单金额超过上限");
        OrderDiscountAllocator.allocate(items,total,discount.discountAmount());
        try {
            String addressQuote=json.writeValueAsString(address.address())+":"+address.freightAmount().stripTrailingZeros()
                    +":"+discount.fullReductionAmount().stripTrailingZeros()+":"+discount.couponAmount().stripTrailingZeros();
            String fingerprint=CheckoutTicketService.fingerprint(addressId,couponId,selected,items,discount.discountAmount(),addressQuote);
            return new Settlement(selected,items,total,address,discount,fingerprint);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private static <T> T critical(String service, Supplier<T> operation) {
        try { return operation.get(); }
        catch (BusinessException ex) { throw ex; }
        catch (RuntimeException ex) { throw new CheckoutUnavailableException(service,ex); }
    }
}
