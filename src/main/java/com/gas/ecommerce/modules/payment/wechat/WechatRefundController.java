package com.gas.ecommerce.modules.payment.wechat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.aftersale.dto.AftersaleCompleteRequest;
import com.gas.ecommerce.modules.aftersale.service.AftersaleService;
import com.gas.ecommerce.modules.aftersale.vo.AftersaleVO;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import org.springframework.web.bind.annotation.*;

@RestController
public class WechatRefundController {
    private final AftersaleService aftersales;
    private final PaymentMapper payments;
    public WechatRefundController(AftersaleService aftersales,PaymentMapper payments) { this.aftersales=aftersales; this.payments=payments; }
    @PostMapping("/api/v1/admin/aftersales/{id}/wechat-refund")
    @RequiresPermission("aftersale:write")
    public Result<AftersaleVO> refund(@PathVariable Long id) {
        var row=aftersales.detailAdmin(id);
        var payment=payments.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId,row.orderId())
                .eq(Payment::getChannel,WechatPayGateway.CHANNEL).in(Payment::getStatus,"success","refunded").last("LIMIT 1"));
        if(payment==null) throw new BusinessException("此订单没有已成功的微信支付记录");
        return Result.success(aftersales.complete(id,SecurityUtils.currentUserId(),new AftersaleCompleteRequest("WXAS"+id,"微信原路退款已确认")));
    }
}
