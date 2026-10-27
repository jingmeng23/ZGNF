package com.gas.ecommerce.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.*;
import com.gas.ecommerce.modules.order.vo.OrderAddressVO;
import com.gas.ecommerce.modules.product.mapper.CatalogLockMapper;
import com.gas.ecommerce.modules.product.service.ProductCheckoutService;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.modules.user.service.AddressCheckoutService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The final, short local transaction: order snapshots + resource associations + outbox + success marker. */
@Service
public class CheckoutOrderWriter {
    private final OrderSubmissionStore submissions;
    private final UserMapper users;
    private final CartItemMapper cart;
    private final CatalogLockMapper catalog;
    private final ProductCheckoutService products;
    private final AddressCheckoutService addresses;
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final OrderInventoryService inventory;
    private final CheckoutMarketingService marketing;
    private final OrderOutbox outbox;
    private final ObjectMapper json;
    public CheckoutOrderWriter(OrderSubmissionStore submissions, UserMapper users, CartItemMapper cart,
            CatalogLockMapper catalog, ProductCheckoutService products, AddressCheckoutService addresses,
            OrderMapper orders, OrderItemMapper items, OrderInventoryService inventory,
            CheckoutMarketingService marketing, OrderOutbox outbox, ObjectMapper json) {
        this.submissions=submissions; this.users=users; this.cart=cart; this.catalog=catalog;
        this.products=products; this.addresses=addresses; this.orders=orders; this.items=items;
        this.inventory=inventory; this.marketing=marketing; this.outbox=outbox; this.json=json;
    }
    @Transactional
    public Order save(Long userId, OrderCreateRequest request, OrderSubmissionStore.Claim claim,
            CheckoutSettlementService.Settlement quote) {
        var user=users.lockActiveUser(userId);
        if (user==null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        submissions.lockForCommit(claim);
        if (catalog.lock()==null) throw new IllegalStateException("缺少商品分类事务锁");
        var selected=cart.selectList(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId,userId)
                .eq(CartItem::getSelected,true).orderByAsc(CartItem::getProductId));
        if (selected.size()!=quote.selected().size()) throw new BusinessException("购物车已变化，请重新结算");
        for (int i=0;i<selected.size();i++) {
            var current=selected.get(i); var previous=quote.selected().get(i);
            if (!current.getId().equals(previous.getId()) || !current.getQuantity().equals(previous.getQuantity()))
                throw new BusinessException("购物车已变化，请重新结算");
        }
        var currentProducts=products.quote(quote.items().stream().map(i->i.getProductId()).toList());
        for (var item : quote.items()) {
            var current=currentProducts.get(item.getProductId());
            if (current==null || current.product().getPrice().compareTo(item.getUnitPrice())!=0)
                throw new BusinessException("商品价格已变化，请重新结算");
        }
        if (!addresses.quote(userId,request.addressId()).equals(quote.address())) throw new BusinessException("地址或运费已变化，请重新结算");
        Order order=new Order(); order.setOrderNo(claim.orderNo()); order.setUserId(userId);
        order.setRequestId(request.idempotencyKey()); order.setRemark(request.remark());
        OrderStateMachine.initialize(order);
        order.setTotalAmount(quote.total()); order.setPayAmount(quote.payAmount());
        order.setDiscountAmount(quote.marketing().discountAmount()); order.setFreightAmount(quote.address().freightAmount());
        order.setFullReductionAmount(quote.marketing().fullReductionAmount()); order.setCouponDiscountAmount(quote.marketing().couponAmount());
        order.setUserCouponId(request.userCouponId()); order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        var address=quote.address().address();
        try { order.setAddressSnapshot(json.writeValueAsString(new OrderAddressVO(address.getReceiver(),address.getPhone(),address.getProvince(),address.getCity(),address.getDistrict(),address.getDetail(),address.getProvinceCode(),address.getCityCode(),address.getDistrictCode(),address.getLatitude(),address.getLongitude()))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        orders.insert(order);
        for (var item : quote.items()) { item.setOrderId(order.getId()); items.insert(item); }
        // These are same-DB adapter associations, not remote reservations. All remote effects happened before this transaction.
        inventory.bindCheckout(claim.orderNo(),order.getId()); marketing.bind(claim.orderNo(),order.getId());
        cart.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId,userId).in(CartItem::getId,selected.stream().map(CartItem::getId).toList()));
        outbox.created(order);
        submissions.succeeded(claim);
        return order;
    }
}
