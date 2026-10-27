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
import com.gas.ecommerce.modules.order.dto.*;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.entity.OrderItem;
import com.gas.ecommerce.modules.order.mapper.*;
import com.gas.ecommerce.modules.order.service.*;
import com.gas.ecommerce.modules.order.vo.*;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final com.gas.ecommerce.modules.coupon.service.CouponService coupons;
    private final com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService marketing;
    private final UserMapper users;
    private final OrderItemMapper items;
    private final OrderInventoryService stock;
    private final OrderNoGenerator numbers;
    private final ObjectMapper json;
    private final String encryptionKey;
    private final MeterRegistry metrics;
    private final CheckoutTicketService tickets;
    private final CheckoutSettlementService settlement;
    private final OrderSubmissionStore submissions;
    private final CheckoutOrderWriter writer;
    private final CheckoutCompensationService compensation;
    private final CheckoutPointsGateway points;
    private final OrderCloseReleaseService closeReleases;
    private final OrderDetailAssembler details;
    private final com.gas.ecommerce.modules.payment.service.PaymentCloseGuard paymentClose;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public OrderServiceImpl(com.gas.ecommerce.modules.coupon.service.CouponService coupons,
            com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService marketing,
            UserMapper users, OrderItemMapper items, OrderInventoryService stock,
            OrderNoGenerator numbers, ObjectMapper json, @Value("${app.encryption-key}") String encryptionKey,
            MeterRegistry metrics, CheckoutTicketService tickets, CheckoutSettlementService settlement,
            OrderSubmissionStore submissions, CheckoutOrderWriter writer, CheckoutCompensationService compensation,
            CheckoutPointsGateway points, com.gas.ecommerce.modules.payment.service.PaymentCloseGuard paymentClose,
            org.springframework.jdbc.core.JdbcTemplate db, OrderCloseReleaseService closeReleases,OrderDetailAssembler details) {
        this.coupons=coupons; this.marketing=marketing; this.users=users; this.items=items; this.stock=stock;
        this.numbers=numbers; this.json=json; this.encryptionKey=encryptionKey; this.metrics=metrics;
        this.tickets=tickets; this.settlement=settlement; this.submissions=submissions;
        this.writer=writer; this.compensation=compensation; this.points=points; this.paymentClose=paymentClose; this.db=db; this.closeReleases=closeReleases; this.details=details;
    }

    @Override @Transactional
    public OrderPreviewVO preview(Long userId, OrderPreviewRequest request) {
        ensureActive(userId);
        var quote=settlement.settle(userId,request.addressId(),request.userCouponId());
        String number=numbers.next();
        LocalDateTime expires=LocalDateTime.now().plusMinutes(15);
        String token=tickets.issue(number,userId,quote.fingerprint(),expires);
        var address=quote.address().address();
        return new OrderPreviewVO(number,token,expires,new OrderAddressVO(address.getReceiver(),
                EncryptUtils.decryptAes(address.getPhone(),encryptionKey),address.getProvince(),address.getCity(),address.getDistrict(),address.getDetail()),
                quote.items().stream().map(i->new OrderItemVO(i.getProductId(),i.getProductName(),i.getProductImage(),i.getQuantity(),i.getUnitPrice(),i.getAmount(),i.getDiscountAmount(),i.getPayAmount())).toList(),
                quote.total(),quote.marketing().discountAmount(),quote.payAmount(),quote.address().freightAmount(),quote.marketing().fullReductionAmount(),quote.marketing().couponAmount());
    }

    @Override
    @com.gas.ecommerce.modules.order.annotation.CheckoutIdempotent
    public OrderVO create(Long userId, OrderCreateRequest request) {
        OrderSubmissionStore.Claim claim=submissions.claim(userId,request);
        if (claim.replay()) return view(findByNumber(userId,claim.orderNo()));
        if (claim.busy()) return awaitOriginal(userId,claim.orderNo());
        try {
            var quote=settlement.settle(userId,request.addressId(),request.userCouponId());
            submissions.validateQuote(userId,request,quote.fingerprint());
            submissions.heartbeat(claim);
            stock.lockCheckout(claim.orderNo(),quote.items());
            submissions.heartbeat(claim);
            marketing.reserve(claim.orderNo(),userId,request.userCouponId(),quote.total(),quote.marketing());
            submissions.heartbeat(claim);
            points.reserve(claim.orderNo(),userId);
            submissions.heartbeat(claim);
            Order order=writer.save(userId,request,claim,quote);
            metrics.counter("shopping.cart.checkout","result","success").increment();
            return view(order);
        } catch (RuntimeException failure) {
            try {
                // Persist compensation intent before releasing anything. An uncertain commit is never blindly rolled back.
                if (submissions.fail(claim,failure)) compensation.compensate(claim.orderNo());
                else {
                    Order committed=getOne(Wrappers.<Order>lambdaQuery().eq(Order::getUserId,userId).eq(Order::getOrderNo,claim.orderNo()));
                    if (committed!=null) return view(committed);
                }
            } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private Order findByNumber(Long userId, String number) {
        Order order=getOne(Wrappers.<Order>lambdaQuery().eq(Order::getUserId,userId).eq(Order::getOrderNo,number));
        if (order==null) throw new CheckoutConflictException("原订单暂不可读取，请使用同一Token重试");
        return order;
    }
    private OrderVO awaitOriginal(Long userId, String number) {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        do {
            Order order=getOne(Wrappers.<Order>lambdaQuery().eq(Order::getUserId,userId).eq(Order::getOrderNo,number));
            if (order!=null) return view(order);
            try { Thread.sleep(25); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
        } while (System.nanoTime()<deadline);
        throw new CheckoutConflictException("订单提交或资源释放处理中，请稍后使用同一Token查询或重试");
    }

    @Override public PageResult<OrderVO> listMine(Long userId, OrderQueryRequest query) {
        ensureActive(userId);
        return pageOrders(userId, query);
    }
    @Override public PageResult<OrderVO> listAdmin(OrderQueryRequest query) { return pageOrders(null, query); }

    @Override @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderVO detail(Long userId, Long id) {
        ensureActive(userId);
        return details.enrich(view(requireOwned(userId, id, false)));
    }
    @Override public OrderVO detailAdmin(Long id) { return details.enrich(view(requireOrder(id, false))); }

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
        com.gas.ecommerce.modules.order.service.OrderStateMachine.persistTransition(order, "completed",db);
        return view(order);
    }

    @Override @Transactional
    public OrderVO process(Long id) {
        Order order = requireOrder(id, true);
        if ("processing".equals(order.getStatus())) return view(order);
        if (!"paid".equals(order.getStatus())) throw new BusinessException("仅已支付订单可开始处理");
        stock.consume(order);
        com.gas.ecommerce.modules.order.service.OrderStateMachine.persistTransition(order, "processing",db);
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
        if (!paymentClose.mayClose(order)) {
            // Close may have persisted a provider success; refresh the returned view.
            com.gas.ecommerce.modules.order.service.OrderStateMachine.transition(order,baseMapper.lockById(order.getId()).getStatus());
            return;
        }
        com.gas.ecommerce.modules.order.service.OrderStateMachine.persistTransition(order,"cancelled",db);
        closeReleases.enqueue(order.getId());
    }

    private PageResult<OrderVO> pageOrders(Long userId, OrderQueryRequest query) {
        if(userId!=null && (long)(query.getPage()-1)*query.getSize()>10000) throw new BusinessException("Deep pagination requires /orders/scroll");
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

    @Override public OrderCursorPage scrollMine(Long userId,OrderCursorRequest query) {
        ensureActive(userId);
        if(query.getSize()<1 || query.getSize()>100) throw new BusinessException("Invalid page size");
        var wrapper=Wrappers.<Order>lambdaQuery().eq(Order::getUserId,userId)
                .eq(query.getStatus()!=null,Order::getStatus,query.getStatus());
        if(query.getAfterId()!=null) {
            Order anchor=requireOwned(userId,query.getAfterId(),false);
            if(query.getStatus()!=null && !query.getStatus().equals(anchor.getStatus())) throw new BusinessException("Cursor tab changed; restart pagination");
            wrapper.and(w->w.lt(Order::getCreateTime,anchor.getCreateTime()).or(v->v.eq(Order::getCreateTime,anchor.getCreateTime()).lt(Order::getId,anchor.getId())));
        }
        var found=list(wrapper.orderByDesc(Order::getCreateTime).orderByDesc(Order::getId).last("LIMIT "+(query.getSize()+1)));
        boolean more=found.size()>query.getSize();
        var page=found.subList(0,Math.min(found.size(),query.getSize()));
        if(page.isEmpty()) return new OrderCursorPage(List.of(),false,null);
        var grouped=items.selectList(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId,page.stream().map(Order::getId).toList()).orderByAsc(OrderItem::getId))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return new OrderCursorPage(page.stream().map(o->view(o,grouped.getOrDefault(o.getId(),List.of()))).toList(),more,more?page.getLast().getId():null);
    }

    private Order requireOrder(Long id, boolean lock) {
        Order order = lock ? baseMapper.lockById(id) : getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        return order;
    }
    private Order requireOwned(Long userId, Long id, boolean lock) {
        Order order = lock ? baseMapper.lockOwned(id,userId) : getOne(Wrappers.<Order>lambdaQuery().eq(Order::getId,id).eq(Order::getUserId,userId));
        if(order==null) throw new BusinessException("Order does not exist");
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
        if (address != null) address = address.withPhone(EncryptUtils.decryptAes(address.phone(), encryptionKey));
        return new OrderVO(order.getId(), order.getUserId(), order.getOrderNo(), order.getStatus(),
                order.getTotalAmount(), order.getPayAmount(), order.getUserCouponId(), order.getDiscountAmount(), order.getRemark(), order.getCreateTime(),
                order.getExpireTime(), address, orderItems.stream().map(i -> new OrderItemVO(i.getProductId(),
                        i.getProductName(), i.getProductImage(), i.getQuantity(), i.getUnitPrice(), i.getAmount(), i.getDiscountAmount(), i.getPayAmount())).toList(), order.getFreightAmount(), order.getFullReductionAmount(), order.getCouponDiscountAmount());
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
