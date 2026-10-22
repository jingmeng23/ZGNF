package com.gas.ecommerce.modules.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.service.PaymentCallbackVerifier;
import com.gas.ecommerce.modules.payment.service.PaymentService;
import jakarta.validation.Validator;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * HTTP 接口控制器；具体请求方法以类中标注的映射注解为准。
 */
@RestController @RequestMapping("/api/v1/payment/callback") public class PaymentCallbackController {
    private final PaymentService payments;
    private final PaymentCallbackVerifier verifier;
    private final ObjectMapper json;
    private final Validator validator;
    public PaymentCallbackController(PaymentService payments,
            PaymentCallbackVerifier verifier,
            ObjectMapper json,Validator validator) {
        this.payments=payments;this.verifier=verifier;this.json=json;this.validator=validator;
    }
    @PostMapping("/{channel}")
    public Result<?> callback(@PathVariable String channel,
            @RequestHeader(value="X-Payment-Timestamp",defaultValue="") String timestamp,
            @RequestHeader(value="X-Payment-Signature",defaultValue="") String signature,
            @RequestBody String body) {
        verifier.verify(channel,timestamp,signature,body);
        PaymentCallbackRequest request;
        try {request=json.readValue(body,PaymentCallbackRequest.class);}
        catch(JsonProcessingException ex){throw new BusinessException("回调参数格式错误");}
        if(request==null || !validator.validate(request).isEmpty()) throw new BusinessException("回调参数无效");
        return Result.success(payments.callback(channel,request));
    }
}
