package com.gas.ecommerce.modules.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.gas.ecommerce.common.utils.OrderNoGenerator;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.order.dto.*;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import com.gas.ecommerce.modules.order.mapper.*;
import com.gas.ecommerce.modules.order.service.*;
import com.gas.ecommerce.modules.order.vo.*;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.mapper.CatalogLockMapper;
import com.gas.ecommerce.modules.product.mapper.ProductMapper;
import com.gas.ecommerce.modules.product.service.ProductBrowseService;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.user.mapper.UserAddressMapper;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");
    private final com.gas.ecommerce.modules.coupon.service.CouponService coupons;
    private final UserMapper users;
    private final UserAddressMapper addresses;
    private final CartItemMapper cart;
    private final ProductMapper products;
    private final CatalogLockMapper catalogLock;
    private final ProductBrowseService browse;
    private final OrderItemMapper items;
    private final OrderInventoryService stock;
    private final OrderNoGenerator numbers;
    private final ObjectMapper json;
    private final String encryptionKey;

    public OrderServiceImpl(com.gas.ecommerce.modules.coupon.service.CouponService coupons, UserMapper users, UserAddressMapper addresses, CartItemMapper cart,
            ProductMapper products, CatalogLockMapper catalogLock, ProductBrowseService browse,
            OrderItemMapper items, OrderInventoryService stock, OrderNoGenerator numbers, ObjectMapper json,
            @Value("${app.encryption-key}") String encryptionKey) {
        this.coupons = coupons; this.users = users; this.addresses = addresses; this.cart = cart;
        this.products = products; this.catalogLock = catalogLock; this.browse = browse;
        this.items = items; this.stock = stock; this.numbers = numbers;
        this.json = json; this.encryptionKey = encryptionKey;
    }

    @Override @Transactional
    public OrderVO create(Long userId, OrderCreateRequest request) {
        var user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        Order existing = getOne(Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId)
                .eq(Order::getRequestId, request.requestId()));
        if (existing != null) return view(existing);

        UserAddress address = addresses.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, request.addressId()).eq(UserAddress::getUserId, userId));
        if (address == null) throw new BusinessException("收货地址不存在");
        if (catalogLock.lock() == null) throw new IllegalStateException("缺少商品分类事务锁");
        List<CartItem> selected = cart.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId).eq(CartItem::getSelected, true).orderByAsc(CartItem::getProductId));
        if (selected.isEmpty()) throw new BusinessException("请先选择购物车商品");
        Set<Long> visibleCategories = browse.categories().stream().map(c -> c.id()).collect(Collectors.toSet());
        List<OrderItem> snapshots = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : selected) {
            Product product = products.selectById(cartItem.getProductId());
            if (product == null || !"on_sale".equals(product.getStatus())
                    || !visibleCategories.contains(product.getCategoryId()))
                throw new BusinessException("购物车中存在已下架或不可售商品");
            if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1 || cartItem.getQuantity() > 999)
                throw new BusinessException("商品数量须为 1 至 999");
            if (product.getPrice() == null || product.getPrice().signum() < 0)
                throw new BusinessException("商品价格异常");
            OrderItem item = new OrderItem();
            item.setProductId(product.getId()); item.setProductName(product.getName());
            item.setProductImage(product.getCoverUrl()); item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setAmount(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            total = total.add(item.getAmount()); snapshots.add(item);
        }
        if (total.compareTo(MAX_AMOUNT) > 0) throw new BusinessException("订单金额超过上限");
        Order order = new Order();
        order.setUserId(userId); order.setOrderNo(numbers.next()); order.setRequestId(request.requestId());
        order.setStatus("pending_payment"); order.setTotalAmount(total); order.setPayAmount(total);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setRemark(request.remark());
        // Keep the address phone encrypted in the immutable database snapshot.
        order.setAddressSnapshot(serialize(new OrderAddressVO(address.getReceiver(), address.getPhone(),
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetail())));
        order.setDiscountAmount(BigDecimal.ZERO);
        save(order);
        BigDecimal discount = coupons.reserve(userId, request.userCouponId(), order.getId(), total);
        order.setUserCouponId(request.userCouponId()); order.setDiscountAmount(discount); order.setPayAmount(total.subtract(discount));
        updateById(order);
        for (OrderItem item : snapshots) { item.setOrderId(order.getId()); items.insert(item); }
        stock.reserve(order, snapshots);
        cart.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .in(CartItem::getId, selected.stream().map(CartItem::getId).toList()));
        return view(order);
    }

    @Override public PageResult<OrderVO> listMine(Long userId, OrderQueryRequest query) {
        ensureActive(userId);
        return pageOrders(userId, query);
    }
    @Override public PageResult<OrderVO> listAdmin(OrderQueryRequest query) { return pageOrders(null, query); }

    @Override public OrderVO detail(Long userId, Long id) {
        ensureActive(userId);
        return view(requireOwned(userId, id, false));
    }
    @Override public OrderVO detailAdmin(Long id) { return view(requireOrder(id, false)); }

    @Override @Transactional
    public OrderVO cancel(Long userId, Long id) {
        ensureActive(userId);
        Order order = requireOwned(userId, id, true);
        if ("cancelled".equals(order.getStatus())) return view(order);
        if (!"pending_payment".equals(order.getStatus())) throw new BusinessException("仅待支付订单可取消");
        cancelOrder(order);
        return view(order);
    }

    @Override @Transactional
    public OrderVO confirmReceipt(Long userId, Long id) {
        ensureActive(userId);
        Order order = requireOwned(userId, id, true);
        if ("completed".equals(order.getStatus())) return view(order);
        if (!"processing".equals(order.getStatus())) throw new BusinessException("订单尚未进入配送或处理阶段");
        order.setStatus("completed"); updateById(order);
        return view(order);
    }

    @Override @Transactional
    public OrderVO process(Long id) {
        Order order = requireOrder(id, true);
        if ("processing".equals(order.getStatus())) return view(order);
        if (!"paid".equals(order.getStatus())) throw new BusinessException("仅已支付订单可开始处理");
        stock.consume(order);
        order.setStatus("processing"); updateById(order);
        return view(order);
    }

    @Override @Transactional
    public void cancelExpired(String orderNo) {
        Order found = getOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (found == null) return;
        Order order = requireOrder(found.getId(), true);
        if ("pending_payment".equals(order.getStatus()) && order.getExpireTime() != null
                && !order.getExpireTime().isAfter(LocalDateTime.now())) cancelOrder(order);
    }

    private void cancelOrder(Order order) {
        stock.release(order);
        coupons.release(order);
        order.setStatus("cancelled"); updateById(order);
    }

    private PageResult<OrderVO> pageOrders(Long userId, OrderQueryRequest query) {
        var wrapper = Wrappers.<Order>lambdaQuery().eq(userId != null, Order::getUserId, userId)
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .eq(query.getOrderNo() != null && !query.getOrderNo().isBlank(), Order::getOrderNo, query.getOrderNo())
                .orderByDesc(Order::getCreateTime).orderByDesc(Order::getId);
        Page<Order> page = page(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<Order> orders = page.getRecords();
        if (orders.isEmpty()) return new PageResult<>(List.of(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
        Map<Long, List<OrderItem>> grouped = items.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderId, orders.stream().map(Order::getId).toList()).orderByAsc(OrderItem::getId))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return new PageResult<>(orders.stream().map(o -> view(o, grouped.getOrDefault(o.getId(), List.of()))).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private Order requireOrder(Long id, boolean lock) {
        Order order = lock ? baseMapper.lockById(id) : getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        return order;
    }
    private Order requireOwned(Long userId, Long id, boolean lock) {
        Order order = requireOrder(id, lock);
        if (!userId.equals(order.getUserId())) throw new BusinessException("订单不存在");
        return order;
    }
    private void ensureActive(Long userId) {
        var user = users.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }
    private OrderVO view(Order order) {
        return view(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getId)));
    }
    private OrderVO view(Order order, List<OrderItem> orderItems) {
        OrderAddressVO address = readAddress(order.getAddressSnapshot());
        if (address != null) address = new OrderAddressVO(address.receiver(),
                EncryptUtils.decryptAes(address.phone(), encryptionKey), address.province(),
                address.city(), address.district(), address.detail());
        return new OrderVO(order.getId(), order.getUserId(), order.getOrderNo(), order.getStatus(),
                order.getTotalAmount(), order.getPayAmount(), order.getUserCouponId(), order.getDiscountAmount(), order.getRemark(), order.getCreateTime(),
                order.getExpireTime(), address, orderItems.stream().map(i -> new OrderItemVO(i.getProductId(),
                        i.getProductName(), i.getProductImage(), i.getQuantity(), i.getUnitPrice(), i.getAmount())).toList());
    }
    private String serialize(OrderAddressVO address) {
        try { return json.writeValueAsString(address); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("订单地址序列化失败", ex); }
    }
    private OrderAddressVO readAddress(String snapshot) {
        if (snapshot == null) return null;
        try {
            var node = json.readTree(snapshot);
            // H2's JSON type can wrap a JDBC string more than once.
            for (int depth = 0; depth < 4 && node.isTextual(); depth++) node = json.readTree(node.asText());
            return json.treeToValue(node, OrderAddressVO.class);
        } catch (JsonProcessingException ex) { throw new IllegalStateException("订单地址读取失败", ex); }
    }
}
