package com.gas.ecommerce.modules.payment.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest;
import com.gas.ecommerce.modules.payment.service.PaymentService;
import com.gas.ecommerce.modules.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * HTTP 接口控制器；具体请求方法以类中标注的映射注解为准。
 */
@RestController @RequestMapping("/api/v1/payments") public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments){this.payments=payments;}
    @org.springframework.web.bind.annotation.GetMapping
    public Result<com.gas.ecommerce.common.result.PageResult<PaymentVO>> list(
            @Valid @org.springframework.web.bind.annotation.ModelAttribute com.gas.ecommerce.modules.payment.dto.PaymentQueryRequest query) {
        return Result.success(payments.listMine(SecurityUtils.currentUserId(),query));
    }
    @PostMapping
    public Result<PaymentVO> create(
            @Valid @RequestBody PaymentCreateRequest request) {
        return Result.success(payments.create(SecurityUtils.currentUserId(),request));
    }
    @GetMapping("/{id}")
    public Result<PaymentVO> detail(@PathVariable Long id) {
        return Result.success(payments.detail(SecurityUtils.currentUserId(),id));
    }
}
