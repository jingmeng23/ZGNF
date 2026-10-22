package com.gas.ecommerce.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.utils.PaymentNoGenerator;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest;
import com.gas.ecommerce.modules.payment.vo.PaymentVO;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.payment.service.PaymentService;
/** Payment and order state changes share one database transaction. */
@Service public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {
    private final com.gas.ecommerce.modules.coupon.service.CouponService coupons;
    private final OrderMapper orders;
    private final UserMapper users;
    private final PaymentNoGenerator numbers;
    private final Set<String> channels;
    public PaymentServiceImpl(com.gas.ecommerce.modules.coupon.service.CouponService coupons, OrderMapper orders,
            UserMapper users, PaymentNoGenerator numbers,
            @Value("${app.payments.allowed-channels:}") String channels) {
        this.coupons=coupons; this.orders=orders; this.users=users; this.numbers=numbers;
        this.channels=Arrays.stream(channels.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toSet());
    }
    @Override @Transactional
    public PaymentVO create(Long userId, PaymentCreateRequest request) {
        var user=users.selectById(userId);
        if(user==null || !"active".equals(user.getStatus())) throw error("用户不存在或已停用");
        if(!channels.contains(request.channel())) throw error("支付渠道未启用");
        var order=orders.lockById(request.orderId());
        if(order==null || !userId.equals(order.getUserId())) throw error("订单不存在");
        requirePayable(order);
        var existing=getOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId,order.getId()).eq(Payment::getStatus,"pending").last("LIMIT 1"));
        if(existing!=null) {
            if(!request.channel().equals(existing.getChannel())) throw error("订单已有其他渠道待支付记录");
            return view(existing);
        }
        Payment p=new Payment(); p.setPaymentNo(numbers.next()); p.setOrderId(order.getId()); p.setUserId(userId);
        p.setAmount(order.getPayAmount()); p.setChannel(request.channel()); p.setStatus("pending"); p.setIsDeleted(0); save(p);
        return view(p);
    }
    @Override public PaymentVO detail(Long userId,Long id) {
        var user=users.selectById(userId);
        if(user==null || !"active".equals(user.getStatus())) throw error("用户不存在或已停用");
        Payment p=getById(id);
        if(p==null || !userId.equals(p.getUserId())) throw error("支付记录不存在");
        return view(p);
    }
    @Override public com.gas.ecommerce.common.result.PageResult<PaymentVO> listMine(Long userId,com.gas.ecommerce.modules.payment.dto.PaymentQueryRequest q) {
        var user=users.selectById(userId);
        if(user==null || !"active".equals(user.getStatus())) throw error("用户不存在或已停用");
        var page=page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(q.getPage(),q.getSize()),
                Wrappers.<Payment>lambdaQuery().eq(Payment::getUserId,userId)
                .eq(q.getStatus()!=null,Payment::getStatus,q.getStatus()).eq(q.getOrderId()!=null,Payment::getOrderId,q.getOrderId())
                .orderByDesc(Payment::getCreateTime).orderByDesc(Payment::getId));
        return new com.gas.ecommerce.common.result.PageResult<>(page.getRecords().stream().map(this::view).toList(),page.getTotal(),page.getCurrent(),page.getSize(),page.getPages());
    }
    @Override @Transactional
    public PaymentVO callback(String channel,PaymentCallbackRequest r) {
        if(!channels.contains(channel)) throw error("支付渠道未启用");
        Payment found=getOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getPaymentNo,r.paymentNo()));
        if(found==null || !channel.equals(found.getChannel())) throw error("支付记录不存在");
        // Serialize callbacks, cancellation and refund completion on the order before locking payment.
        var order=orders.lockById(found.getOrderId());
        Payment p=baseMapper.lockById(found.getId());
        if(order==null || p==null || !p.getUserId().equals(order.getUserId()) || !channel.equals(p.getChannel())
                || p.getAmount().compareTo(r.amount())!=0 || p.getAmount().compareTo(order.getPayAmount())!=0) throw error("支付订单或金额不一致");
        if(!"pending".equals(p.getStatus())) {
            if((p.getStatus().equals(r.status()) || ("refunded".equals(p.getStatus()) && "success".equals(r.status())))
                    && r.transactionNo().equals(p.getProviderTransactionNo())) return view(p);
            throw error("支付记录状态与回调不一致");
        }
        requirePayable(order);
        if(r.eventTime().isBefore(p.getCreateTime().minusSeconds(1)) || r.eventTime().isAfter(LocalDateTime.now().plusMinutes(5)))
            throw error("支付事件时间不合法");
        if(count(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getChannel,channel).eq(Payment::getProviderTransactionNo,r.transactionNo()).ne(Payment::getId,p.getId()))>0)
            throw error("渠道交易流水号已被使用");
        p.setProviderTransactionNo(r.transactionNo()); p.setStatus(r.status());
        if("success".equals(r.status())) { p.setPaidTime(r.eventTime()); coupons.markUsed(order); order.setStatus("paid"); orders.updateById(order); }
        try { updateById(p); }
        catch(DuplicateKeyException ex) {throw error("渠道交易流水号已被使用");}
        return view(p);
    }
    private void requirePayable(Order order) {
        if(!"pending_payment".equals(order.getStatus()) || order.getExpireTime()==null || !order.getExpireTime().isAfter(LocalDateTime.now()))
            throw error("订单已支付、取消或过期，无法支付；已扣款需对账处理");
    }
    private BusinessException error(String message) {return new BusinessException(message);}
    private PaymentVO view(Payment p) {
        return new PaymentVO(p.getId(),p.getPaymentNo(),p.getOrderId(),p.getAmount(),p.getChannel(),p.getStatus(),p.getPaidTime());
    }
}
