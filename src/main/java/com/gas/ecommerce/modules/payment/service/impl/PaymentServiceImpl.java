package com.gas.ecommerce.modules.payment.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.payment.service.PaymentService;
@org.springframework.stereotype.Service public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService { }
