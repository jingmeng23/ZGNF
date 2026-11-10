package com.gas.ecommerce.modules.payment.wechat;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest;
import com.gas.ecommerce.modules.payment.service.PaymentService;
import com.gas.ecommerce.modules.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/wechat")
public class WechatPaymentController {
    public record Create(@NotNull @Positive Long orderId) {}
    private final PaymentService payments;
    private final WechatPaymentService wechat;
    private final WechatClient client;
    private final WechatIdentityService identities;
    public WechatPaymentController(PaymentService payments,WechatPaymentService wechat,WechatClient client,WechatIdentityService identities) { this.payments=payments; this.wechat=wechat; this.client=client; this.identities=identities; }
    @PostMapping("/prepay")
    public Result<WechatPaymentService.Prepay> prepay(@Valid @RequestBody Create request) {
        client.requireEnabled();
        Long userId=SecurityUtils.currentUserId();
        identities.openId(userId);
        // Commit the payment record BEFORE the external request: retries must always reuse this paymentNo.
        var payment=payments.create(userId,new PaymentCreateRequest(request.orderId(),WechatPayGateway.CHANNEL));
        return Result.success(wechat.prepare(userId,payment.id()));
    }
    @PostMapping("/{id}/sync")
    public Result<PaymentVO> sync(@PathVariable @Positive Long id) {
        Long userId=SecurityUtils.currentUserId();
        payments.detail(userId,id); // Includes current active-account validation.
        wechat.sync(userId,id);
        return Result.success(payments.detail(userId,id));
    }
}
