package com.gas.ecommerce.modules.payment.wechat;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.service.*;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WechatPaymentService {
    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final ObjectProvider<WechatPayGateway> gateway;
    private final PaymentOutcomeHandler outcomes;
    public WechatPaymentService(OrderMapper orders,PaymentMapper payments,ObjectProvider<WechatPayGateway> gateway,PaymentOutcomeHandler outcomes) {
        this.orders=orders; this.payments=payments; this.gateway=gateway; this.outcomes=outcomes;
    }
    public record Prepay(Long paymentId,String paymentNo,Map<String,String> payment) {}
    @Transactional
    public Prepay prepare(Long userId,Long paymentId) {
        Payment found=owned(userId,paymentId);
        var order=orders.lockById(found.getOrderId());
        Payment payment=payments.lockById(paymentId);
        if(order==null || !userId.equals(order.getUserId()) || !"pending_payment".equals(order.getStatus())
                || order.getExpireTime()==null || !order.getExpireTime().isAfter(LocalDateTime.now())
                || !"pending".equals(payment.getStatus())) throw new BusinessException("订单已支付、取消或过期");
        // Creation is committed before entering this transaction, so a network timeout never erases the payment identity.
        return new Prepay(payment.getId(),payment.getPaymentNo(),requiredGateway().prepay(payment,order.getExpireTime()));
    }
    public void sync(Long userId,Long paymentId) {
        Payment p=owned(userId,paymentId);
        if(!WechatPayGateway.CHANNEL.equals(p.getChannel())) throw new BusinessException("支付渠道不匹配");
        var snapshot=requiredGateway().query(p);
        if(snapshot.state()==PaymentProviderGateway.State.SUCCESS) outcomes.accept(WechatPayGateway.CHANNEL,snapshot.outcome());
    }
    private Payment owned(Long userId,Long id) {
        Payment p=payments.selectById(id);
        if(p==null || !userId.equals(p.getUserId())) throw new BusinessException("支付单不存在");
        return p;
    }
    private WechatPayGateway requiredGateway() {
        var result=gateway.getIfAvailable();
        if(result==null) throw new BusinessException("微信支付尚未启用");
        return result;
    }
}
