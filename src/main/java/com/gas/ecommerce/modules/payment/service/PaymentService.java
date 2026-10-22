package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest;
import com.gas.ecommerce.modules.payment.vo.PaymentVO;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.payment.entity.Payment;
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */
public interface PaymentService extends IService<Payment> {
    com.gas.ecommerce.common.result.PageResult<PaymentVO> listMine(Long userId, com.gas.ecommerce.modules.payment.dto.PaymentQueryRequest query);
    PaymentVO create(Long userId, PaymentCreateRequest request);
    PaymentVO detail(Long userId, Long id);
    PaymentVO callback(String channel, PaymentCallbackRequest request);
}
