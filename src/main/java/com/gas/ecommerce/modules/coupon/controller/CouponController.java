package com.gas.ecommerce.modules.coupon.controller;

import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.coupon.dto.UserCouponQuery;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import com.gas.ecommerce.modules.coupon.vo.UserCouponVO;
import com.gas.ecommerce.modules.system.dto.CouponQuery;
import com.gas.ecommerce.modules.system.entity.Coupon;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CouponController {
    private final CouponService service;
    public CouponController(CouponService service) { this.service=service; }
    @GetMapping("/coupons") public Result<PageResult<Coupon>> available(@Valid @ModelAttribute CouponQuery q) {
        return Result.success(service.available(SecurityUtils.currentUserId(),q));
    }
    @PostMapping("/coupons/{id}/claim") public Result<UserCouponVO> claim(@PathVariable Long id) {
        return Result.success(service.claim(SecurityUtils.currentUserId(),id));
    }
    @GetMapping("/user-coupons") public Result<PageResult<UserCouponVO>> mine(@Valid @ModelAttribute UserCouponQuery q) {
        return Result.success(service.mine(SecurityUtils.currentUserId(),q));
    }
}
